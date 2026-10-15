package com.jupydroid.app.ui.shell

import com.jupydroid.app.DocumentOpening
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.model.TextEdit
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A document that counts how often it was read back from its file - the half of a revert that
 * happens after the restore, and must not happen when the restore was refused.
 */
class RevertingDocument(override val path: String) : OpenDocument {
  var reloads = 0
    private set

  override val openedAs: DocumentOpening = DocumentOpening(path)
  override val dirty = MutableStateFlow(true)

  override fun reloadFromDisk() {
    reloads++
    dirty.value = false
  }

  override suspend fun saveAndConfirm(): Boolean = false
  override fun saveNow(onSaved: () -> Unit) = Unit
  override fun discardChanges() = Unit
  override suspend fun writeCopyTo(path: String) = Unit
  override fun sourceOf(cell: Int?): String? = null
  override fun applyEdits(cell: Int?, edits: List<TextEdit>) = Unit
}
