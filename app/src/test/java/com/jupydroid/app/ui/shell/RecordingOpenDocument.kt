package com.jupydroid.app.ui.shell

import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.model.TextEdit

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

  override fun sourceOf(cell: Int?): String? = text.takeIf { cell == null }

  override fun applyEdits(cell: Int?, edits: List<TextEdit>) {
    applied = applied + (cell to edits)
  }
}
