package com.jupydroid.app.ui.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the notebook marks: the six commands [SelectionTopBar] drives.
 *
 * Beside [NotebookCellEdits] and split from it on the same seam the reference draws. A selection
 * is not the document - `NotebookRevision` does not carry one, `NotebookActions.undo` ends with a
 * `deselectAll()` rather than restoring what was marked - so **nothing here schedules a save**.
 * That is the whole reason it is not a method on the edits: every one of those writes, and none of
 * these has anything to write.
 *
 * ```
 * val selection = NotebookCellSelection(uiState)
 * selection.extendTo("c3")   // the run now runs from the anchor to c3
 * ```
 */
class NotebookCellSelection(private val uiState: MutableStateFlow<EditorUiState>) {
  /** Moves the head - the touch analogue of a shift-click, and what a cell's tick does. */
  fun extendTo(cellId: String) = uiState.update { it.withSelectionExtendedTo(cellId) }

  /** The four `notebook:extend-marked-cells-*` commands. */
  fun extend(reach: SelectionReach) = uiState.update { it.withSelectionExtended(reach) }

  /** `notebook:select-all`. */
  fun selectAll() = uiState.update { it.withAllCellsSelected() }

  /** `notebook:deselect-all`, which is also how the mode is left. */
  fun deselectAll() = uiState.update { it.withSelectionCollapsed() }
}
