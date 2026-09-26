package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.LanguageCompletion
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageServerPort
import kotlin.coroutines.cancellation.CancellationException

/**
 * A cell's ask for what fits under the cursor: the Space's language server first, the kernel
 * only when no server is running - `docs/PLAN.md` P5c, behind the same [CompletionResult] the
 * kernel path always produced, so the popup and [applyCompletion] do not change.
 *
 * The session gate is the rule this ask always had: a notebook that never ran a cell never
 * asked for a Space, and typing there must not boot one. Once the kernel is open the Space is
 * already paid for, so the ask may lazily spawn `pylsp` - which is also when the kernel is
 * there to fall back on if the server cannot answer. The reference merges every completer's
 * rows into one model (`ICompletionProviderManager`); the plan's wording is the narrower one
 * taken here - the kernel is a fallback, not a second source.
 *
 * ```
 * completion.at(cell.id, cell.source, cursorOffset)
 * ```
 */
class NotebookCompletion(
  private val session: NotebookKernelSession,
  private val kernelGateway: KernelGateway,
  private val languageServer: LanguageServerPort,
) {
  /**
   * What is offered for [code] with the caret at [cursorOffset] in the cell [cellId] names.
   *
   * [cellId] is what the document is keyed on: one LSP text document per cell, synced whole on
   * every ask - [LanguageServerPort.changeDocument] opens it on the first one, the reference's
   * `_sendChange` rule.
   */
  @Suppress("SwallowedException") // a kernel that cannot answer has nothing to say, which is not an error
  suspend fun at(cellId: String, code: String, cursorOffset: Int): CompletionResult {
    val open = session.opened ?: return CompletionResult.NONE
    return viaLanguageServer(cellId, code, cursorOffset)
      ?: try {
        kernelGateway.complete(open, code, cursorOffset)
      } catch (e: IllegalStateException) {
        CompletionResult.NONE
      }
  }

  /**
   * The language server's answer, or null when there is no answer to give - a `pylsp` that
   * cannot start, a socket that dies mid-request, a reply that never comes. Each is worth the
   * kernel's turn and none is worth a crash, which is the broad catch; a cancelled ask is not
   * a failure and is rethrown, because `collectLatest` cancels on the next keystroke.
   */
  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  private suspend fun viaLanguageServer(cellId: String, code: String, cursorOffset: Int): CompletionResult? = try {
    val uri = cellDocumentUri(cellId)
    languageServer.changeDocument(BASE_SPACE, uri, code)
    toCompletionResult(
      languageServer.completion(BASE_SPACE, uri, positionAt(code, cursorOffset)),
      code,
      cursorOffset,
    )
  } catch (cancelled: CancellationException) {
    throw cancelled
  } catch (failed: Exception) {
    null
  }
}

/** A document `file://` uri naming the cell - virtual under root, so it never touches a real path. */
internal fun cellDocumentUri(cellId: String): String = "file:///jupyterdroid/cells/$cellId.py"

/** The caret as LSP speaks it: zero-based line, character in the negotiated `utf-16` code units. */
private fun positionAt(code: String, cursorOffset: Int): LanguagePosition {
  val before = code.take(cursorOffset.coerceIn(0, code.length))
  return LanguagePosition(
    line = before.count { it == '\n' },
    character = cursorOffset - (before.lastIndexOf('\n') + 1),
  )
}

/** Where a line-and-character point sits in [code], as a code-unit offset - clamped at line end. */
private fun offsetAt(code: String, position: LanguagePosition): Int {
  var offset = 0
  repeat(position.line) {
    val newline = code.indexOf('\n', offset)
    if (newline < 0) return code.length
    offset = newline + 1
  }
  val lineEnd = code.indexOf('\n', offset).takeIf { it >= 0 } ?: code.length
  return (offset + position.character).coerceAtMost(lineEnd)
}

/**
 * LSP items as the one [CompletionResult] the popup knows.
 *
 * A row is the text accepting it writes - `textEdit.newText`, else `insertText` - not the
 * `label` it was shown under, because those differ exactly when the label would splice wrong
 * (`np.ar` completing to `arange` must not insert `np.` again). The replaced span is the
 * edit's own range when an item carries one, and the identifier under the cursor when it does
 * not - the word range the reference computes for an edit-less item.
 */
private fun toCompletionResult(items: List<LanguageCompletion>, code: String, cursorOffset: Int): CompletionResult {
  val matches = items.map { it.edit?.newText ?: it.insertText }.distinct()
  if (matches.isEmpty()) return CompletionResult.NONE
  val edit = items.firstNotNullOfOrNull { it.edit }
  val (start, end) =
    if (edit != null) {
      offsetAt(code, edit.range.start) to offsetAt(code, edit.range.end)
    } else {
      identifierSpanEndingAt(code, cursorOffset) ?: (cursorOffset to cursorOffset)
    }
  return CompletionResult(matches, start, end)
}
