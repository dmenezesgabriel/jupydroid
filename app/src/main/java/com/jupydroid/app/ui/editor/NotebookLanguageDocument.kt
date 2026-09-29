package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.port.LanguageServerPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The notebook's **one** document under the language server, kept synced
 * P5d-2b, the owner side of P5d-1's [NotebookVirtualDocument].
 *
 * Everything the server is asked (completion today, symbols next) and everything the server
 * pushes back (`publishDiagnostics` ranges) speaks this document's coordinates, so the sync
 * and the mirror of what was last synced must live in exactly one place: [sync] writes the
 * whole text and publishes the document as [synced], and a diagnostic is always mapped against
 * the last thing the server actually received - never a rebuild of the cells, which could sit
 * ahead of what was sent.
 *
 * ```
 * val synced = document.sync(cells)
 * languageServer.completion(space, synced.uri, synced.positionOf(cellId, offset)!!)
 * ```
 */
class NotebookLanguageDocument(
  private val languageServer: LanguageServerPort,
  /** The notebook's workspace name, read lazily - see `NotebookKernel`'s own provider. */
  private val notebookId: () -> String,
) {
  private val syncLock = Mutex()
  private val _synced = MutableStateFlow<NotebookVirtualDocument?>(null)

  /** The last document the server received in full - null before the first [sync]. */
  val synced: StateFlow<NotebookVirtualDocument?> = _synced

  /**
   * The whole text of [cells]' code cells into the server's one document for this notebook -
   * opened on first sync ([LanguageServerPort.changeDocument]'s own rule), the version bumped
   * by the adapter each time after. Serialized so two syncs can never interleave into a version
   * order neither caller intended, and [synced] moves only once the send succeeded.
   */
  suspend fun sync(cells: List<Cell>): NotebookVirtualDocument = syncLock.withLock {
    val document = NotebookVirtualDocument(notebookId(), cells)
    languageServer.changeDocument(BASE_SPACE, document.uri, document.text)
    _synced.value = document
    document
  }
}
