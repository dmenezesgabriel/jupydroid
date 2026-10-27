package com.jupydroid.app

import com.jupydroid.extension.api.NotebookCellSummary
import com.jupydroid.extension.api.NotebookCellType
import com.jupydroid.extension.api.NotebookCells

/**
 * An open notebook of one active code cell that keeps the code it was asked to insert below it, so a
 * test reads it without a notebook. Not [available], it has no notebook open at all.
 *
 * ```
 * val notebookCells = RecordingNotebookCells(available = true)
 * SidePanelHost("", server = null, spaceId = "base", RichTextMarkdownRenderer, notebookCells, fileOpener)
 * ```
 */
class RecordingNotebookCells(override val available: Boolean) : NotebookCells {
  val inserted = mutableListOf<String>()

  override val path: String? get() = "untitled.ipynb".takeIf { available }

  override val cells: List<NotebookCellSummary>
    get() = listOf(NotebookCellSummary("active", NotebookCellType.CODE)).takeIf { available }.orEmpty()

  override val activeCellId: String? get() = "active".takeIf { available }

  override fun insert(index: Int, type: NotebookCellType, source: String): String? {
    if (!available) return null
    inserted += source
    return "inserted-${inserted.size}"
  }
}
