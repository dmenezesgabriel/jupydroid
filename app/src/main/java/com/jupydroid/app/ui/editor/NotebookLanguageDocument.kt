package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.LanguageServerPort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The notebook's **one** document under the language server, kept synced - the owner side of
 * [NotebookVirtualDocument].
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
  /**
   * The entered Space, whose own `pylsp` holds this document - `StdioLanguageServer` runs one per
   * Space, so every ask about this document names it too.
   */
  val space: Space,
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
   *
   * A rename re-keys the uri (`NotebookVirtualDocument` builds it from `notebookId`), and the
   * document the server knew is orphaned without a `didClose` - left open and linted, its
   * pushes dropped by the callers' uri check. The orphan is found here, on the next sync, and
   * closed before the new uri's auto-open (`NotebookLanguageDocumentTest.aRename_closesTheDocumentTheServerKnew`).
   */
  suspend fun sync(cells: List<Cell>): NotebookVirtualDocument = syncLock.withLock {
    val document = NotebookVirtualDocument(notebookId(), cells)
    _synced.value?.takeIf { it.uri != document.uri }
      ?.let { languageServer.closeDocument(space, it.uri) }
    languageServer.changeDocument(space, document.uri, document.text)
    _synced.value = document
    document
  }
}
