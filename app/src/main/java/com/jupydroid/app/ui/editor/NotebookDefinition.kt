package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.port.LanguageLocation
import com.jupydroid.app.domain.port.LanguageServerPort
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where a definition jump lands: the cell that owns the target and the range inside its own
 * source - [offset] the caret takes (`Range.collapseToStart` in the reference's
 * `_openReference`), [endOffset] the far edge of the range a symbol flash highlights.
 */
data class DefinitionTarget(val cellId: String, val offset: Int, val endOffset: Int)

/**
 * A cell's ask for where the symbol under the caret is defined - `textDocument/definition`
 * against the notebook's one virtual document.
 *
 * The session gate is [NotebookHover]'s own rule: a notebook that never ran a cell never asked
 * for a Space, so a "Go to definition" row there must not boot one. The answer is read with
 * the reference's own cut: jupyterlab-lsp opens a foreign target in a second editor, and
 * nothing here stands in for a file outside the virtual document - so a location whose uri is
 * not the synced document's (a stdlib `posixpath.py` is what pylsp actually answers for
 * `os.path.join`) is skipped, and the first that does map is the jump.
 *
 * ```
 * definition.at(cells, cell.id, code, cursorOffset)
 * ```
 */
class NotebookDefinition(
  private val session: NotebookKernelSession,
  private val languageServer: LanguageServerPort,
  /**
   * The notebook's one document under the language server, shared with the other asks - a
   * `Location`'s virtual range always maps against what the last sync sent.
   */
  private val document: NotebookLanguageDocument,
) {
  /**
   * Where the symbol at [cursorOffset] in [cellId]'s [code] is defined, or null - no session,
   * a caret on a markdown cell, a `pylsp` that cannot start, an empty reply, and every
   * location that lands on a separator or outside the document.
   */
  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  suspend fun at(cells: List<Cell>, cellId: String, code: String, cursorOffset: Int): DefinitionTarget? {
    session.opened ?: return null
    return try {
      val synced = document.sync(cells.withLiveSource(cellId, code))
      synced.positionOf(cellId, cursorOffset)?.let { position ->
        languageServer.definition(BASE_SPACE, synced.uri, position)
          .firstNotNullOfOrNull(synced::definitionTarget)
      }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failed: Exception) {
      null
    }
  }
}

/**
 * A `Location` in virtual coordinates as a jump into a cell, or null - off-document uris
 * land here, and so does a range whose start sits on a separator or in another cell (a
 * `cellLineAt` null). The range's *end* clamps instead of dropping: a span that runs onto a
 * separator still flashes the part of it the cell owns.
 */
private fun NotebookVirtualDocument.definitionTarget(location: LanguageLocation): DefinitionTarget? {
  if (location.uri != uri) return null
  val hit = cellLineAt(location.range.start.line)?.cellId
    ?.let { cellId -> cellOffsetOf(cellId, location.range.start)?.let { cellId to it } }
  return hit?.let { (cellId, offset) ->
    DefinitionTarget(
      cellId = cellId,
      offset = offset,
      endOffset = cellOffsetOfClamped(cellId, location.range.end) ?: offset,
    )
  }
}
