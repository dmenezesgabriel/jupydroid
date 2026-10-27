package com.jupydroid.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.ui.editor.EditorUiState
import com.jupydroid.extension.api.NotebookCellSummary
import com.jupydroid.extension.api.NotebookCellType
import com.jupydroid.extension.api.NotebookCells

/**
 * Where the open notebook publishes its cells and its insert, and a contributed panel reads them -
 * [OpenDebugStack]'s seam, crossed for the same reason: the panel is in the list pane and the cells
 * are the detail pane's `EditorViewModel`'s.
 *
 * Empty while no notebook is open, and not [available] while one is open with no active cell, which
 * is what makes Insert disabled there as jupyter-chat's `available` does (jupyter-chat@2c224bc
 * `packages/jupyter-chat/src/active-cell-manager.ts`). The cells are read from the editor's state
 * each time they are asked for, since an agent's command can arrive at any moment after publishing.
 *
 * ```
 * val notebookCells = remember { OpenNotebookCells() }                                 // MainNavigation
 * notebookCells.publish(hasActiveCell, { viewModel.uiState.value }, edits::insertCellAt) // the editor
 * ```
 */
@Stable
class OpenNotebookCells : NotebookCells {
  private var published by mutableStateOf<PublishedNotebook?>(null)

  override val available: Boolean get() = published?.hasActiveCell == true

  override val path: String? get() = published?.current?.invoke()?.notebookId

  override val cells: List<NotebookCellSummary>
    get() = published?.current?.invoke()?.cells.orEmpty().map { NotebookCellSummary(it.id, cellTypeOf(it.kind)) }

  override val activeCellId: String? get() = published?.current?.invoke()?.selectedCellId

  override fun insert(index: Int, type: NotebookCellType, source: String): String? =
    published?.insert?.invoke(index, cellKindOf(type), source)

  /** The open notebook's state and insert, replacing whatever was there. */
  fun publish(
    hasActiveCell: Boolean,
    current: () -> EditorUiState,
    insert: (index: Int, kind: CellKind, source: String) -> String,
  ) {
    published = PublishedNotebook(hasActiveCell, current, insert)
  }

  /** The notebook has gone; nothing here should outlive the editor that published it. */
  fun clear() {
    published = null
  }

  private class PublishedNotebook(
    val hasActiveCell: Boolean,
    val current: () -> EditorUiState,
    val insert: (Int, CellKind, String) -> String,
  )
}

private fun cellTypeOf(kind: CellKind): NotebookCellType = when (kind) {
  CellKind.CODE -> NotebookCellType.CODE
  CellKind.MARKDOWN -> NotebookCellType.MARKDOWN
  CellKind.RAW -> NotebookCellType.RAW
}

private fun cellKindOf(type: NotebookCellType): CellKind = when (type) {
  NotebookCellType.CODE -> CellKind.CODE
  NotebookCellType.MARKDOWN -> CellKind.MARKDOWN
  NotebookCellType.RAW -> CellKind.RAW
}
