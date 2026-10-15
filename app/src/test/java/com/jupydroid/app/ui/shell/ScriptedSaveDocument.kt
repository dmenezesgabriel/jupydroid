package com.jupydroid.app.ui.shell

import com.jupydroid.app.DocumentOpening
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.model.TextEdit
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A document whose save is decided in advance: [refuses] is a disk that answers this file's write
 * with a conflict or a failure, which leaves the document dirty as the real ones are left.
 * [saveLog] is shared between documents, so a test reads the order the sweep wrote them in.
 */
class ScriptedSaveDocument(
  override val path: String,
  isDirty: Boolean,
  private val saveLog: MutableList<String>,
  private val refuses: Boolean = false,
) : OpenDocument {
  override val openedAs: DocumentOpening = DocumentOpening(path)
  override val dirty = MutableStateFlow(isDirty)

  override suspend fun saveAndConfirm(): Boolean {
    saveLog += path
    if (!refuses) dirty.value = false
    return !dirty.value
  }

  override fun saveNow(onSaved: () -> Unit) = Unit
  override fun discardChanges() = Unit
  override suspend fun writeCopyTo(path: String) = Unit
  override fun reloadFromDisk() = Unit
  override fun sourceOf(cell: Int?): String? = null
  override fun applyEdits(cell: Int?, edits: List<TextEdit>) = Unit
}
