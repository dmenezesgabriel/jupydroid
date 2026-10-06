package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageServerPort
import kotlin.coroutines.cancellation.CancellationException

/**
 * A cell's ask for what the symbol under the caret is - `textDocument/hover` against the
 * notebook's one virtual document.
 *
 * The session gate is [NotebookCompletion]'s own rule: a notebook that never ran a cell never
 * asked for a Space, so a "Symbol info" row there must not boot one. Unlike a completion there
 * is no kernel turn to leave - `inspect_request` is a different feature the plan never
 * promised, and a server that cannot answer is simply nothing to show.
 *
 * ```
 * hover.at(cells, cell.id, code, cursorOffset)
 * ```
 */
class NotebookHover(
  private val session: NotebookKernelSession,
  private val languageServer: LanguageServerPort,
  /**
   * The notebook's one document under the language server, shared with completions and the
   * diagnostics watch - a `publishDiagnostics` or a hover range always maps against what the
   * last sync sent.
   */
  private val document: NotebookLanguageDocument,
) {
  /**
   * What the server says about [cursorOffset] in [cellId]'s [code], or null - no session, a
   * caret on a markdown cell (which owns no block of the Python document), a `pylsp` that
   * cannot start, a reply of `null` where nothing is defined.
   *
   * The ask goes to one virtual document per notebook and at the caret's virtual position,
   * [NotebookCompletion]'s own contract: `def compute_total` in an earlier cell is what makes
   * `compute_total` hoverable in this one.
   */
  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  suspend fun at(cells: List<Cell>, cellId: String, code: String, cursorOffset: Int): LanguageHover? {
    session.opened ?: return null
    return try {
      val synced = document.sync(cells.withLiveSource(cellId, code))
      synced.positionOf(cellId, cursorOffset)
        ?.let { languageServer.hover(document.space, synced.uri, it) }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failed: Exception) {
      null
    }
  }
}
