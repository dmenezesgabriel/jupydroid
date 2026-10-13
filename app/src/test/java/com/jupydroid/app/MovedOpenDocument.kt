package com.jupydroid.app

import com.jupydroid.app.domain.model.TextEdit
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * A document whose [path] a rename can move while [openedAs] stays where its tab opened it - the
 * registry contract, nothing more.
 */
class MovedOpenDocument(override val openedAs: DocumentOpening) : OpenDocument {
  override var path: String = openedAs.path
  override val dirty = MutableStateFlow(false)
  override fun saveNow(onSaved: () -> Unit) = onSaved()
  override fun discardChanges() = Unit
  override fun sourceOf(cell: Int?): String? = null
  override fun applyEdits(cell: Int?, edits: List<TextEdit>) = Unit
}
