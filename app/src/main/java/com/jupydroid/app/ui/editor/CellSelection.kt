package com.jupydroid.app.ui.editor

/**
 * Selecting a cell and moving between the two [CellMode]s, as pure functions.
 *
 * Beside [CellEdits] and for the same reason: what the notebook becomes after a gesture is
 * decided by something a test can call directly, with no composition or coroutine in the way.
 */

/**
 * Makes [cellId] the one cell every cell-scoped action means - JupyterLab's `activeCellIndex`,
 * by id rather than by position because our cells are keyed and reorderable.
 *
 * Selecting is **not** editing: the mode is left where it was. That is JupyterLab's split too -
 * `_evtFocusIn` sets `activeCellIndex` whenever focus lands anywhere in a cell, and only asks for
 * edit mode when the focused node is inside the *editor*
 * (`packages/notebook/src/widget.ts:3547-3562`).
 *
 * A cell that is not there is not selected. The alternative - a selection naming a missing cell -
 * makes every later action silently do nothing.
 */
fun EditorUiState.withCellSelected(cellId: String): EditorUiState =
  if (cells.none { it.id == cellId }) this else copy(selectedCellId = cellId)

/**
 * Moves between command and edit mode, with the two side effects JupyterLab's `setMode` has.
 *
 * **Edit mode needs a cell.** `if (!activeCell) newValue = 'command'` is the first thing
 * `setMode` does, so asking to edit with nothing selected is not an error - it just does not
 * happen.
 *
 * **Editing expands a collapsed cell** (`activeCell!.inputHidden = false`). Reachable here
 * because a cell can be collapsed from the menu and then asked to edit from the keyboard, and
 * without it the caret would land in something that is not on screen.
 */
fun EditorUiState.withMode(next: CellMode): EditorUiState {
  val editing = selectedCellId?.takeIf { next == CellMode.EDIT }
    ?: return copy(mode = CellMode.COMMAND)
  return copy(
    mode = CellMode.EDIT,
    cells = cells.map { if (it.id == editing) it.copy(sourceHidden = false) else it },
  )
}

/**
 * Drops a selection whose cell is gone, and falls back to command mode when it does.
 *
 * The id-based equivalent of JupyterLab clamping `activeCellIndex` to the bounds of the cell
 * list. Applied after any transform that can remove a cell; a selection pointing at nothing is
 * worse than none, because it looks live and every action scoped to it quietly no-ops.
 */
fun EditorUiState.withSelectionClamped(): EditorUiState {
  val alive = selectedCellId != null && cells.any { it.id == selectedCellId }
  if (alive) return this
  return copy(selectedCellId = null, mode = CellMode.COMMAND)
}
