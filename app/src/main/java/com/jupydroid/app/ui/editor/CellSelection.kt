package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellKind

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
 *
 * **Editing also drops a selected run** - *"Edit mode deselects all cells"*, `setMode` at
 * `packages/notebook/src/widget.ts:1925-1929`. One line there, and here it is what lets a tap into
 * a cell keep meaning "type in this one" while a run is marked, instead of needing a gesture that
 * says "leave selection mode first".
 */
fun EditorUiState.withMode(next: CellMode): EditorUiState {
  val editing = selectedCellId?.takeIf { next == CellMode.EDIT }
    ?: return copy(mode = CellMode.COMMAND)
  return copy(
    mode = CellMode.EDIT,
    anchorCellId = null,
    cells = cells.map { if (it.id == editing) it.copy(sourceHidden = false) else it },
  )
}

/**
 * What `notebook:enter-edit-mode` asks of the state (`tracker.json:443-448`).
 *
 * Upstream sets `widget.mode = 'edit'` and focuses the editor; here the ask is the caret, and the
 * mode arrives with the focus event the field reports - asking for edit mode directly would mean
 * claiming a focus that has not landed yet. A code or raw cell asks through [EditorUiState.cellTakingCaret];
 * a markdown cell opens its editor, whose own ask is what [EditorUiState.markdownTakingCaret] is for.
 *
 * `activeCell!.inputHidden = false` again: entering edit on a collapsed cell expands the source
 * the caret lands in, so the transform unhides before it asks.
 */
fun EditorUiState.withEditRequested(): EditorUiState {
  val head = cells.firstOrNull { it.id == selectedCellId } ?: return this
  val shown = copy(
    cells = cells.map { if (it.id == head.id) it.copy(sourceHidden = false) else it },
  )
  return if (head.kind == CellKind.MARKDOWN) {
    shown.withMarkdownEditorOpened(head.id)
  } else {
    shown.copy(cellTakingCaret = head.id)
  }
}

/**
 * Focus landing on a cell's frame, as a state transform - upstream's `_evtFocusIn` on `.jp-Cell`
 * (`packages/notebook/src/widget.ts:3541-3563`): select the cell first, then the mode, and a cell
 * node holding focus is never the editor, so the mode is command. The order matters on a cell
 * that was being edited: upstream's own sequence leaves edit before it changes the active cell.
 */
fun EditorUiState.withCellActivated(cellId: String): EditorUiState {
  if (cells.none { it.id == cellId }) return this
  return withCellSelected(cellId).withMode(CellMode.COMMAND)
}

/**
 * `notebook:move-cursor-{up,down}` - the head stepping one *visible* cell, `ArrowUp`/`K` and
 * `ArrowDown`/`J` in command mode.
 *
 * `selectAbove`/`selectBelow` walk the widgets skipping `isHidden` and `inputHidden` ones
 * (`packages/notebook/src/actions.tsx:1128-1132`), which is why the scan reads [hiddenCellIds]
 * and [Cell.sourceHidden] rather than stepping ±1. A collapsed cell is never a stop on the way.
 *
 * Two rules come straight out of them, and the first is the one a first guess gets wrong:
 *
 * - **The edges bail before anything changes** - `selectAbove` returns on index 0 and
 *   `selectBelow` on the last non-hidden cell (`:1110-1115`, `:1150-1155`), *before*
 *   `deselectAll` is reached. A move that goes nowhere keeps the marked run. (`selectBelow`
 *   there also focuses the notebook's footer, which ours does not draw as a focus target - the
 *   head simply stays.)
 * - **A real move deselects** - `activeCellIndex = …` then `deselectAll()` (`:1136-1140`,
 *   `:1168-1173`). The mode is left alone, which is what lets a move out of a marked run stay a
 *   command-mode act.
 *
 * Each move counts on [EditorUiState.headMovesByKey], the event the screen's scroll-and-refocus
 * effect listens for; a step that goes nowhere counts nothing.
 */
fun EditorUiState.withCursorMoved(direction: CursorDirection): EditorUiState {
  val at = cells.indexOfFirst { it.id == selectedCellId }
  val target = if (at < 0) null else nextVisibleAt(at, direction)
  return if (target == null) {
    this
  } else {
    copy(
      selectedCellId = cells[target].id,
      anchorCellId = null,
      headMovesByKey = headMovesByKey + 1,
    )
  }
}

/** The index [direction]'s key lands on: one cell over, skipping `sourceHidden` and folded cells. */
private fun EditorUiState.nextVisibleAt(at: Int, direction: CursorDirection): Int? {
  val hidden = hiddenCellIds
  val step = if (direction == CursorDirection.UP) -1 else 1
  return generateSequence(at + step) { it + step }
    .takeWhile { it in cells.indices }
    .firstOrNull { !cells[it].sourceHidden && cells[it].id !in hidden }
}

/**
 * Drops a selection whose cell is gone, and falls back to command mode when it does.
 *
 * The id-based equivalent of JupyterLab clamping `activeCellIndex` to the bounds of the cell
 * list. Applied after any transform that can remove a cell; a selection pointing at nothing is
 * worse than none, because it looks live and every action scoped to it quietly no-ops.
 *
 * **Both ends are clamped**, and the anchor for the same reason as the head: a run whose anchor
 * has been deleted is a range that cannot be computed, so `selectedCellIds` would silently narrow
 * to one cell while the bar above it still said three.
 */
fun EditorUiState.withSelectionClamped(): EditorUiState {
  val anchored = anchorCellId?.takeIf { anchor -> cells.any { it.id == anchor } }
  val alive = selectedCellId != null && cells.any { it.id == selectedCellId }
  if (!alive) return copy(selectedCellId = null, anchorCellId = null, mode = CellMode.COMMAND)
  return if (anchored == anchorCellId) this else copy(anchorCellId = anchored)
}
