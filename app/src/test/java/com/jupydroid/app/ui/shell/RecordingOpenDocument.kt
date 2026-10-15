package com.jupydroid.app.ui.shell

import com.jupydroid.app.DocumentOpening
import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.model.TextEdit
import com.jupydroid.app.domain.model.withEditsApplied
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * A document that is open, remembering what was applied to it and where.
 *
 * Named rather than inlined because two things about it are assertions: that the engine edits the
 * **model** and not the file, and that it addresses the resource `MatchedLine.cell` names. A null
 * [text] is a document that cannot be read - one still loading, or one whose read failed - which the
 * engine must refuse rather than fall back to the file for.
 */
class RecordingOpenDocument(override val path: String, private var text: String?) : OpenDocument {
  var applied: List<Pair<Int?, List<TextEdit>>> = emptyList()
    private set

  /** Nothing renames a recording document: what it opened under is where it still lives. */
  override val openedAs: DocumentOpening = DocumentOpening(path)

  // The close half of the contract - a clean document: these tests never ask it to leave.
  override val dirty: StateFlow<Boolean> = MutableStateFlow(false)
  override suspend fun saveAndConfirm(): Boolean = true
  override fun saveNow(onSaved: () -> Unit) = onSaved()
  override fun discardChanges() = Unit

  override fun reloadFromDisk() = Unit

  override fun sourceOf(cell: Int?): String? = text.takeIf { cell == null }

  override fun applyEdits(cell: Int?, edits: List<TextEdit>) {
    applied = applied + (cell to edits)
    // A real document's source changes when it is edited, and an undo is refused unless it does -
    // it compares what the replace produced against what is there now.
    text = text?.withEditsApplied(edits)
  }
}
