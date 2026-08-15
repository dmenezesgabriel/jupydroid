package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import java.util.UUID

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
 * Clears the execution count along with the outputs, which is what JupyterLab's `clearExecution`
 * does: `[7]` beside a cell with nothing under it claims a result that is no longer there.
 */
fun EditorUiState.withOutputsCleared(cellIds: List<String>): EditorUiState {
  val clearing = cellIds.toSet()
  if (cells.none { it.id in clearing }) return this
  return copy(outputs = outputs - clearing, executionCounts = executionCounts - clearing, dirty = true)
}

/**
 * Folds or unfolds the source of a whole run, to **one** state rather than each cell's opposite.
 *
 * JupyterLab has `hideCode` and `showCode` as separate commands over the selection
 * (`packages/notebook/src/actions.tsx:1877-1910`), so a run always ends up uniform. Toggling each
 * cell against its own state would invert a mixed run and leave it exactly as mixed - and the menu
 * row that asked for it says one thing, so it has to do one thing. Which state is the caller's,
 * read off the head cell, which is also what the row's label was written from.
 */
fun EditorUiState.withSourceHiddenSet(cellIds: List<String>, hidden: Boolean): EditorUiState {
  val folding = cellIds.toSet()
  if (cells.none { it.id in folding }) return this
  return copy(
    cells = cells.map { if (it.id in folding) it.copy(sourceHidden = hidden) else it },
    dirty = true,
  )
}

/**
 * Hides the outputs without discarding them - which is the whole difference from
 * [withOutputsCleared], and why both are in the menu. Independent of the source's collapse:
 * JupyterLab has a separate command for each and stores them under separate keys.
 */
fun EditorUiState.withOutputsHiddenSet(cellIds: List<String>, hidden: Boolean): EditorUiState {
  val folding = cellIds.toSet()
  if (cells.none { it.id in folding }) return this
  return copy(
    cells = cells.map { if (it.id in folding) it.copy(outputsHidden = hidden) else it },
    dirty = true,
  )
}

private fun <T> Map<String, T>.carryOver(from: String, to: String): Map<String, T> =
  get(from)?.let { this + (to to it) } ?: this
