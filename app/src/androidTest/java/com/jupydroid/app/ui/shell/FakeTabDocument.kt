package com.jupydroid.app.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jupydroid.app.DocumentOpening
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.model.TextEdit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A document for the registry alone - the strip never asks it to render. */
internal class FakeTabDocument(openedUnder: String, isDirty: Boolean = false) : OpenDocument {
  override val openedAs = DocumentOpening(openedUnder)

  /** The live path - snapshot state, so a rename landing on it recomposes the tab like an adoption would. */
  var livePath: String by mutableStateOf(openedUnder)
  val dirtyFlow = MutableStateFlow(isDirty)
  var savesRequested = 0
    private set
  var discards = 0
    private set

  override val path: String get() = livePath
  override val dirty: StateFlow<Boolean> = dirtyFlow

  override fun saveNow(onSaved: () -> Unit) {
    savesRequested++
    dirtyFlow.value = false
    onSaved()
  }

  override fun discardChanges() {
    discards++
    dirtyFlow.value = false
  }

  override fun sourceOf(cell: Int?): String? = null

  override fun applyEdits(cell: Int?, edits: List<TextEdit>) = Unit
}
