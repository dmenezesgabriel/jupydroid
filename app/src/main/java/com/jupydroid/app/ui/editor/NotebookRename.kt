package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageServerException
import com.jupydroid.app.domain.port.LanguageServerPort
import kotlin.coroutines.cancellation.CancellationException

/**
 * A cell's ask for the language server to rename the symbol under the caret -
 * `textDocument/rename`, `docs/PLAN.md` P5e-7, VS Code's `editor.action.rename` read through
 * the notebook's one virtual document.
 *
 * The session gate is the asks' shared rule: a notebook that never ran a cell never asked for
 * a Space, so the row must not boot one. What comes back is a `WorkspaceEdit` - the shipped
 * `pylsp` answers with `documentChanges` carrying **one whole-document `TextEdit`**, the `def`
 * line and every call site inside a single `newText` (`evidence/p5e-0-lsp-surface/`) - so
 * [NotebookVirtualDocument.cellSourcesAfterEdits] is the boundary: it splices the edits into
 * what the last sync sent, reslices on the cell separators, and verifies the parts rebuild the
 * edited text byte-for-byte, because the alternative is rewriting a cell the answer never
 * meant to.
 *
 * Unlike the other asks a refusal is **not** silence: a server that says no - jedi's
 * "there's no symbol that can be renamed" - has a message worth reporting, which is
 * VS Code's `notificationService` answer to a provider error rather than the hover's shrug.
 * Only the outcomes that cannot run at all stay silent.
 *
 * ```
 * when (val answer = rename.renamed(cells, cell.id, code, cursorOffset, newName)) {
 *   is RenameAnswer.Renamed -> edits.cellsRenamed(cell.id, answer.sources, answer.askedSources)
 *   is RenameAnswer.Refused -> edits.renameFailed(answer.message)
 *   null -> Unit
 * }
 * ```
 */
class NotebookRename(
  private val session: NotebookKernelSession,
  private val languageServer: LanguageServerPort,
  /**
   * The notebook's one document under the language server, shared with the other asks - the
   * edit's ranges always apply against what the last sync sent.
   */
  private val document: NotebookLanguageDocument,
) {
  /**
   * Every code cell's source after the rename - a [RenameAnswer] - or null where the ask
   * cannot run: no session, a caret on no code cell's block, and a server that cannot answer
   * are all silence here, the asks' own rule.
   */
  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  suspend fun renamed(
    cells: List<Cell>,
    cellId: String,
    code: String,
    cursorOffset: Int,
    newName: String,
  ): RenameAnswer? {
    session.opened ?: return null
    val live = cells.withLiveSource(cellId, code)
    return try {
      val synced = document.sync(live)
      synced.positionOf(cellId, cursorOffset)?.let { position ->
        renameAnswerOn(synced, live, position, newName)
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (refused: LanguageServerException) {
      RenameAnswer.Refused(refused.serverMessage ?: refused.message.orEmpty())
    } catch (failed: Exception) {
      null
    }
  }

  /**
   * The `WorkspaceEdit`'s part for this document, resliced into cells - only the synced uri's
   * edits apply, an edit for any other document being nothing this notebook can hold. The
   * refused cases carry the message the snackbar says verbatim.
   */
  private suspend fun renameAnswerOn(
    synced: NotebookVirtualDocument,
    live: List<Cell>,
    position: LanguagePosition,
    newName: String,
  ): RenameAnswer {
    val edits = languageServer.rename(BASE_SPACE, synced.uri, position, newName).changes[synced.uri]
    return when {
      edits == null -> RenameAnswer.Refused(NO_RESULT)
      else -> synced.cellSourcesAfterEdits(edits)
        ?.let { RenameAnswer.Renamed(it, live.associate { cell -> cell.id to cell.source }) }
        ?: RenameAnswer.Refused(NOT_APPLIED)
    }
  }

  private companion object {
    /** VS Code's own message for a provider that answered nothing (`rename.ts`). */
    const val NO_RESULT = "No result."

    /** The slice guard refused - the answer exists but cannot land without guessing. */
    const val NOT_APPLIED = "The rename could not be applied"
  }
}

/**
 * What a `textDocument/rename` ask answered with - a sealed answer rather than a bare sources
 * map, because "the server refused" is a third outcome beside "renamed" and "no ask ran", and
 * the first two are the ones a user is waiting on.
 */
sealed interface RenameAnswer {
  /**
   * Every code cell's post-rename source - and [askedSources], the same cells as the ask
   * synced them, which the apply gate compares against the live notebook: a reply computed
   * over text that has since moved must be dropped whole, not half-applied.
   */
  data class Renamed(val sources: Map<String, String>, val askedSources: Map<String, String>) : RenameAnswer

  /** The server - or the slice guard - said no; [message] is what the snackbar says verbatim. */
  data class Refused(val message: String) : RenameAnswer
}

/**
 * The last refused rename the screen has to report - [tick] counted rather than flagged, for
 * [EditorUiState.noDefinitionFound]'s reason: a second refusal with the same message is still
 * a change the snackbar must show. Not persisted - it describes an ask in this sitting.
 */
data class RenameOutcome(val tick: Int, val message: String)
