package com.jupydroid.app.ui.editor

/**
 * Selecting a **run** of cells, as pure functions over [EditorUiState].
 *
 * Beside [CellSelection], which owns the one-cell half, and split from it because this is where a
 * second end of the selection appears. `notebook:select-all`, `notebook:deselect-all` and
 * `notebook:extend-marked-cells-{above,below,top,bottom}` all live here.
 *
 * **The selection is contiguous, and that is not a simplification of the reference - it is the
 * reference.** `extendContiguousSelectionTo` is *"move the head of an existing contiguous
 * selection"* (`packages/notebook/src/widget.ts:2216`), `getContiguousSelection()` throws on
 * anything else (`:2325-2348`), and shift-click and shift-drag - the only two multi-select
 * gestures there - both go through it (`:3181`, `:3290`). There is no ctrl-click and no
 * discontiguous selection to model.
 *
 * So the whole feature is two ends: [EditorUiState.anchorCellId], the cell the selection started
 * from, and [EditorUiState.selectedCellId], the head, which is JupyterLab's `activeCellIndex` and
 * was already here. Nothing else is stored - the cells between them are the ones between them.
 */

/**
 * Every cell a cell-scoped action means, in document order.
 *
 * This is JupyterLab's `isSelectedOrActive` (`widget.ts:2178-2183`), which is why a notebook with
 * nothing marked still answers with one cell: `_updateSelectedCells` filters on it
 * (`:3736-3739`), so the active cell is always in the selection. Today's single-cell behaviour is
 * therefore the degenerate case of this list, not a separate path around it.
 *
 * ```
 * state.selectedCellIds   // ["b", "c", "d"] while a run of three is marked
 * ```
 */
val EditorUiState.selectedCellIds: List<String>
  get() {
    val head = cells.indexOfFirst { it.id == selectedCellId }
    if (head < 0) return emptyList()
    val anchor = cells.indexOfFirst { it.id == anchorCellId }
    if (anchor < 0) return listOf(cells[head].id)
    return cells.subList(minOf(anchor, head), maxOf(anchor, head) + 1).map { it.id }
  }

/** Whether a run of cells is being marked, which is the touch mode the contextual bar is up for. */
val EditorUiState.selecting: Boolean
  get() = anchorCellId != null

/**
 * Begins a selection at [cellId] - the touch entry point, and the only one that has no reference
 * equivalent because there is nothing to *enter* with a keyboard.
 *
 * Command mode, as every extend is: `extendSelectionAbove` sets `notebook.mode = 'command'` before
 * it moves anything (`packages/notebook/src/actions.tsx:1326`). Marking cells is not typing in one.
 */
fun EditorUiState.withSelectionStartedAt(cellId: String): EditorUiState {
  if (cells.none { it.id == cellId }) return this
  return copy(selectedCellId = cellId, anchorCellId = cellId, mode = CellMode.COMMAND)
}

/**
 * Moves the head to [cellId], keeping the anchor - `extendContiguousSelectionTo`, and the touch
 * analogue of a shift-click.
 *
 * Two rules come straight out of it, and the second is the one a first guess gets wrong:
 *
 * - **With nothing marked, the active cell becomes the anchor** - *"We will start a new selection
 *   below… head = anchor = this.activeCellIndex"* (`widget.ts:2222-2232`). That is what lets the
 *   four `extend-marked-cells-*` commands work with no selection mode in front of them.
 * - **Extending back onto the anchor ends the selection outright** rather than leaving that one
 *   cell marked - *"Collapse the selection if it is only the active cell"* → `deselectAll()`
 *   (`:2245-2249`). On touch that is also Material's rule, where clearing the last item leaves
 *   selection mode, so nothing has to be adapted here.
 */
fun EditorUiState.withSelectionExtendedTo(cellId: String): EditorUiState {
  val anchor = anchorCellId ?: selectedCellId
  if (anchor == null || cells.none { it.id == cellId }) return this
  return copy(
    selectedCellId = cellId,
    // Null when the head lands back on the anchor, which is the collapse above.
    anchorCellId = anchor.takeIf { it != cellId },
    mode = CellMode.COMMAND,
  )
}

/**
 * The four `notebook:extend-marked-cells-*` commands, as one function over [SelectionReach].
 *
 * **No wrap-around**, which both halves of the reference say in as many words: `extendSelectionAbove`
 * returns early on index 0 and `extendSelectionBelow` on the last cell
 * (`packages/notebook/src/actions.tsx:1318-1322`, `:1352-1356`).
 */
fun EditorUiState.withSelectionExtended(reach: SelectionReach): EditorUiState {
  val head = cells.indexOfFirst { it.id == selectedCellId }
  // -1 for a notebook with no active cell, which `getOrNull` then turns into the same no-op an
  // out-of-range reach gets - the reference bails on both (`actions.tsx:1314-1322`).
  val target = if (head < 0) {
    -1
  } else {
    when (reach) {
      SelectionReach.ABOVE -> head - 1
      SelectionReach.BELOW -> head + 1
      SelectionReach.TOP -> 0
      SelectionReach.BOTTOM -> cells.lastIndex
    }
  }
  return cells.getOrNull(target)?.let { withSelectionExtendedTo(it.id) } ?: this
}

/**
 * `notebook:select-all`, with **one departure from the reference, made to keep the reference's own
 * invariant.**
 *
 * `selectAll` marks every widget and leaves `activeCellIndex` where it was
 * (`packages/notebook/src/actions.tsx:1376-1386`). With the active cell in the middle of the
 * notebook, the next `extendSelectionAbove` reaches `getContiguousSelection()`, which throws
 * *"Active cell not at endpoint of selection"* (`widget.ts:2344-2348`) with nothing catching it.
 * The same file states the rule that call site breaks - *"The head of a contiguous selection is
 * always the active cell"* (`:2320-2324`) - so the head goes on the last cell and every later
 * extend is well defined.
 */
fun EditorUiState.withAllCellsSelected(): EditorUiState {
  if (cells.isEmpty()) return this
  return copy(
    anchorCellId = cells.first().id,
    selectedCellId = cells.last().id,
    mode = CellMode.COMMAND,
  )
}

/**
 * `notebook:deselect-all` - drops the anchor and leaves the head, which is exactly what
 * `Notebook.deselectAll()` does: it clears the marks and never touches `activeCellIndex`
 * (`packages/notebook/src/widget.ts:2196-2210`).
 */
fun EditorUiState.withSelectionCollapsed(): EditorUiState = copy(anchorCellId = null)
