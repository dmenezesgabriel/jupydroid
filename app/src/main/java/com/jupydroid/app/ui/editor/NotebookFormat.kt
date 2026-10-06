package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.port.LanguageFormatOptions
import com.jupydroid.app.domain.port.LanguageServerPort
import kotlin.coroutines.cancellation.CancellationException

/**
 * A cell's ask for the language server to reformat it - `textDocument/rangeFormatting` over
 * the cell's whole virtual block, VS Code's `notebook.formatCell`
 * ("Format Cell") read through the notebook's one document.
 *
 * The session gate is the asks' shared rule: a notebook that never ran a cell never asked for
 * a Space, so the row must not boot one. What comes back is **not** the formatted cell but a
 * whole-document `TextEdit` list - the shipped `pylsp` answers through autopep8, whose
 * `pylsp_format_range` returns exactly one - so [NotebookVirtualDocument.cellSourceAfterEdits]
 * is the boundary: it splices the edits into what the last sync sent, refuses anything that
 * moved text outside the cell, and hands back only the cell's own new source. A no-op answer
 * (`[]`) and every refused or failed answer are a null, which the caller reads as silence -
 * the same rule the other asks keep.
 *
 * ```
 * format.formatted(cells, cell.id, code)?.let { edits.cellFormatted(cell.id, it) }
 * ```
 */
class NotebookFormat(
  private val session: NotebookKernelSession,
  private val languageServer: LanguageServerPort,
  /**
   * The notebook's one document under the language server, shared with the other asks - the
   * edit's range always applies against what the last sync sent.
   */
  private val document: NotebookLanguageDocument,
  /**
   * The `options` the request is required to carry, read live at ask time - the editor's
   * `indentUnit`, read the way `NotebookKernel`'s `shutdownOnClose` provider reads its
   * setting rather than once at construction.
   */
  private val formattingOptions: () -> LanguageFormatOptions,
) {
  /**
   * [cellId]'s source after the formatter's answer, or null - no session, a markdown/raw cell
   * that owns no block, an empty reply, a refused cross-boundary edit, and a server that
   * cannot answer are all silence here.
   */
  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  suspend fun formatted(cells: List<Cell>, cellId: String, code: String): String? {
    session.opened ?: return null
    return try {
      val synced = document.sync(cells.withLiveSource(cellId, code))
      synced.rangeOf(cellId)
        ?.let { range -> languageServer.rangeFormatting(document.space, synced.uri, range, formattingOptions()) }
        ?.let { edits -> synced.cellSourceAfterEdits(cellId, edits) }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failed: Exception) {
      null
    }
  }
}
