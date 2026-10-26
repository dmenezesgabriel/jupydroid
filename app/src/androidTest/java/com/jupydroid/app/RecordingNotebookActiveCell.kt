package com.jupydroid.app

import com.jupydroid.extension.api.NotebookActiveCell

/**
 * An active cell that keeps the code it was asked to insert below it, so a test reads it without a notebook.
 *
 * ```
 * val activeCell = RecordingNotebookActiveCell(available = true)
 * SidePanelHost("", server = null, spaceId = "base", RichTextMarkdownRenderer, activeCell)
 * ```
 */
class RecordingNotebookActiveCell(override val available: Boolean) : NotebookActiveCell {
  val inserted = mutableListOf<String>()

  override fun insertBelow(code: String) {
    inserted += code
  }
}
