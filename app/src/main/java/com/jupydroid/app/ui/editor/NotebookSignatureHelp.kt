package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.LanguageSignatureHelp
import kotlin.coroutines.cancellation.CancellationException

/**
 * A cell's ask for the signature of the call the caret is inside - `textDocument/signatureHelp`
 * against the notebook's one virtual document.
 *
 * [NotebookHover]'s own contract one method over: the same session gate (typing alone never
 * boots a runtime), the same virtual position (an earlier cell's `import os` is what makes
 * `os.path.join(` answerable here), and the same failure rule - a server that cannot answer
 * is silence, and a cancelled ask stays cancelled rather than surface as an error.
 *
 * ```
 * signatureHelp.at(cells, cell.id, code, cursorOffset)
 * ```
 */
class NotebookSignatureHelp(
  private val session: NotebookKernelSession,
  private val languageServer: LanguageServerPort,
  private val document: NotebookLanguageDocument,
) {
  /**
   * What the server says the caret-in-[cursorOffset]'s call offers, or null - no session, a
   * caret on a markdown cell, a `pylsp` that cannot start, a reply of null or no signatures
   * where the caret is inside no call the server knows.
   */
  @Suppress("TooGenericExceptionCaught", "SwallowedException")
  suspend fun at(cells: List<Cell>, cellId: String, code: String, cursorOffset: Int): LanguageSignatureHelp? {
    session.opened ?: return null
    return try {
      val synced = document.sync(cells.withLiveSource(cellId, code))
      synced.positionOf(cellId, cursorOffset)
        ?.let { languageServer.signatureHelp(document.space, synced.uri, it) }
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (failed: Exception) {
      null
    }
  }
}
