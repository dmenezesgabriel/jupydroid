package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind

/**
 * Managing a notebook's cells, as pure functions over [EditorUiState].
 *
 * New ids arrive as parameters rather than being generated here, so a test can say what it
 * expects instead of reading back whatever was minted.
 *
 * A stale id is a no-op throughout: a long-press menu outlives the cell it was opened on, and a
 * menu item tapped just after the row went away must not throw.
 *
 * Semantics follow JupyterLab's `NotebookActions` where the obvious guess differs - see
 * `CellEditsTest`, which cites the file and line for each.
 */
fun EditorUiState.withCellInserted(afterCellId: String, kind: CellKind, newId: String): EditorUiState {
  val at = cells.indexOfFirst { it.id == afterCellId }
  if (at < 0) return this
  return copy(cells = cells.toMutableList().apply { add(at + 1, Cell(newId, kind, "")) }, dirty = true)
}

/**
 * Shifts a whole run of cells past one neighbour - `move` (`actions.tsx:586-610`).
 *
 * **A block, not each cell in turn.** The reference finds the first and last selected index and
 * moves the span between them; walking the cells one at a time would step them over each other
 * and come out reversed. One cell is the same computation with a span of one.
 */
fun EditorUiState.withCellsMoved(cellIds: List<String>, move: CellMove): EditorUiState {
  val span = spanOf(cellIds)?.takeIf { steppedOver(it, move) in cells.indices } ?: return this
  val moving = cells.subList(span.first, span.last + 1).toList()
  val rest = cells.toMutableList().apply { subList(span.first, span.last + 1).clear() }
  // The run lands where its first cell would be after the shift: one earlier going up, and one
  // later going down, which is `toIndex = lastIndex` read against a list the run has left.
  val at = if (move == CellMove.UP) span.first - 1 else span.first + 1
  return copy(cells = rest.apply { addAll(at, moving) }, dirty = true)
}

/** The one cell the run steps over, which is off the end when the run is already against it. */
private fun steppedOver(span: IntRange, move: CellMove): Int =
  if (move == CellMove.UP) span.first - 1 else span.last + 1

/**
 * The contiguous span [cellIds] covers, or null when none of them is in the notebook.
 *
 * Indices rather than ids because every batch transform needs *where* the run is, and a selection
 * is contiguous by construction ([CellRangeSelection]) - so first and last describe it completely,
 * which is how the reference computes it too (`findIndex` of the first selected, then of the first
 * unselected after it).
 */
internal fun EditorUiState.spanOf(cellIds: List<String>): IntRange? {
  val wanted = cellIds.toSet()
  val at = cells.indices.filter { cells[it].id in wanted }
  return if (at.isEmpty()) null else at.first()..at.last()
}

/**
 * Copies of a whole run, dropped in **after its last cell**.
 *
 * `notebook:duplicate-below` passes `'belowSelected'` rather than the `'below'` that `duplicate`
 * itself defaults to (`packages/notebook-extension/src/index.ts:3675`), and the difference is what
 * keeps the copies out of the middle of the run when the head is at its top.
 *
 * Each copy keeps the original's outputs and execution count. That is JupyterLab's behaviour, not
 * an oversight: it duplicates the cell's nbformat JSON and clears only the id.
 */
fun EditorUiState.withCellsDuplicated(cellIds: List<String>, newIds: List<String>): EditorUiState {
  val span = spanOf(cellIds) ?: return this
  val copies = span.map { cells[it] }.mapIndexed { at, cell -> cell to newIds.getOrElse(at) { cell.id } }
  return copy(
    cells = cells.toMutableList().apply { addAll(span.last + 1, copies.map { (cell, id) -> cell.copy(id = id) }) },
    outputs = copies.fold(outputs) { carried, (cell, id) -> carried.carryOver(cell.id, id) },
    executionCounts = copies.fold(executionCounts) { carried, (cell, id) -> carried.carryOver(cell.id, id) },
    dirty = true,
  )
}

/**
 * Deleting the last cell leaves an empty code one, so a notebook is never left with nothing to
 * type into - JupyterLab's `deleteCells` does the same, and inserts the replacement *inside* the
 * same transaction so that emptying a notebook stays one undo.
 */
fun EditorUiState.withCellsDeleted(cellIds: List<String>, replacementId: String): EditorUiState {
  val going = cellIds.toSet()
  if (cells.none { it.id in going }) return this
  val remaining = cells.filterNot { it.id in going }
  return copy(
    cells = remaining.ifEmpty { listOf(Cell(replacementId, CellKind.CODE, "")) },
    outputs = outputs - going,
    executionCounts = executionCounts - going,
    // The record goes with the cell. Left behind, a deleted cell that happened to be running
    // would keep `runningCellId` pointing at it and Run All disabled for ever.
    executions = executions - going,
    dirty = true,
  )
}

/**
 * `notebook:clear-cell-output` over a run of cells - `clearOutputs` (`actions.tsx:1822-1840`).
 *
 * Everything `clearExecution` throws away goes together, and that is three things rather than one
 * (`packages/cells/src/model.ts:706-714`): the outputs, the **execution count** - `[7]` beside a
 * cell with nothing under it claims a result that is no longer there - and the **run itself**,
 * because `executionState = 'idle'` and the deleted `execution` metadata are where our
 * [CellExecution] keeps the red stripe and the *"ran in 6.8 s"*.
 *
 * **And it unfolds them**, which the transaction does in the same breath:
 * `(child as CodeCell).outputHidden = false` (`actions.tsx:1835`). Otherwise clearing a collapsed
 * cell leaves a fold over nothing, offering to expand it.
 */
fun EditorUiState.withOutputsCleared(cellIds: List<String>): EditorUiState = clearingOutputsOf(cellIds.toSet())

/**
 * `notebook:clear-all-cell-outputs` - `clearAllOutputs` (`actions.tsx:1851-1870`).
 *
 * The same transaction as [withOutputsCleared] with one difference, and it is only the operand:
 * that one filters on `isSelectedOrActive` and this one walks every cell. **So it touches the
 * selection not at all** - neither has a `deselectAll()` in it, which puts them with `move` and the
 * collapses.
 */
fun EditorUiState.withAllOutputsCleared(): EditorUiState = clearingOutputsOf(cells.map { it.id }.toSet())

/** Both commands' transaction, since the operand is all that separates them. */
private fun EditorUiState.clearingOutputsOf(clearing: Set<String>): EditorUiState {
  // Code cells only: both loops guard on `cell.type === 'code'`, and a markdown cell has neither
  // an output to throw away nor a fold of its own to unpick.
  val clearable = cells.filter { it.id in clearing && it.kind == CellKind.CODE }.map { it.id }.toSet()
  if (clearable.isEmpty()) return this
  return copy(
    cells = cells.map { if (it.id in clearable) it.copy(outputsHidden = false) else it },
    outputs = outputs - clearable,
    executionCounts = executionCounts - clearable,
    executions = executions - clearable,
    dirty = true,
  )
}

private fun <T> Map<String, T>.carryOver(from: String, to: String): Map<String, T> =
  get(from)?.let { this + (to to it) } ?: this
