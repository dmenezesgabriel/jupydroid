package com.jupydroid.app.ui.shell

import com.jupydroid.app.DocumentOpening
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.model.TextEdit
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A document holding [text] on screen, whose copy goes to [workspace] - or, with a [refusal], a
 * disk that answers the copy's write with that failure, as a missing folder does.
 */
class CopyingOpenDocument(
  override val path: String,
  private val text: String,
  private val workspace: WorkspaceRepository,
  private val refusal: Exception? = null,
) : OpenDocument {
  override val openedAs: DocumentOpening = DocumentOpening(path)
  override val dirty = MutableStateFlow(true)

  override suspend fun writeCopyTo(path: String) {
    refusal?.let { throw it }
    workspace.writeText(path, text)
  }

  override suspend fun saveAndConfirm(): Boolean = false
  override fun saveNow(onSaved: () -> Unit) = Unit
  override fun discardChanges() = Unit
  override fun reloadFromDisk() = Unit
  override fun sourceOf(cell: Int?): String? = text
  override fun applyEdits(cell: Int?, edits: List<TextEdit>) = Unit
}
