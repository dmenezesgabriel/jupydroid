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

fun EditorUiState.withCellMoved(cellId: String, move: CellMove): EditorUiState {
  val from = cells.indexOfFirst { it.id == cellId }
  val to = if (move == CellMove.UP) from - 1 else from + 1
  if (from < 0 || to !in cells.indices) return this
  return copy(
    cells = cells.toMutableList().apply { add(to, removeAt(from)) },
    dirty = true,
  )
}

/**
 * The copy keeps the original's outputs and execution count. That is JupyterLab's behaviour,
 * not an oversight: it duplicates the cell's nbformat JSON and clears only the id.
 */
fun EditorUiState.withCellDuplicated(cellId: String, newId: String): EditorUiState {
  val at = cells.indexOfFirst { it.id == cellId }
  if (at < 0) return this
  return copy(
    cells = cells.toMutableList().apply { add(at + 1, cells[at].copy(id = newId)) },
    outputs = outputs.carryOver(cellId, newId),
    executionCounts = executionCounts.carryOver(cellId, newId),
    dirty = true,
  )
}

/**
 * Deleting the last cell leaves an empty code one, so a notebook is never left with nothing to
 * type into - JupyterLab's `deleteCells` does the same.
 */
fun EditorUiState.withCellDeleted(cellId: String, replacementId: String): EditorUiState {
  if (cells.none { it.id == cellId }) return this
  val remaining = cells.filterNot { it.id == cellId }
  return copy(
    cells = remaining.ifEmpty { listOf(Cell(replacementId, CellKind.CODE, "")) },
    outputs = outputs - cellId,
    executionCounts = executionCounts - cellId,
    // The record goes with the cell. Left behind, a deleted cell that happened to be running
    // would keep `runningCellId` pointing at it and Run All disabled for ever.
    executions = executions - cellId,
    dirty = true,
  )
}

/**
 * Clears the execution count along with the outputs, which is what JupyterLab's `clearExecution`
 * does: `[7]` beside a cell with nothing under it claims a result that is no longer there.
 */
fun EditorUiState.withOutputsCleared(cellId: String): EditorUiState {
  if (cells.none { it.id == cellId }) return this
  return copy(outputs = outputs - cellId, executionCounts = executionCounts - cellId, dirty = true)
}

/**
 * A toggle rather than a hide and a show, which is what a menu on one cell wants. JupyterLab
 * has both pairs *and* a `toggleOutput` for the same reason
 * (`packages/notebook/src/actions.tsx:1998`).
 */
fun EditorUiState.withSourceHiddenToggled(cellId: String): EditorUiState {
  if (cells.none { it.id == cellId }) return this
  return copy(
    cells = cells.map { if (it.id == cellId) it.copy(sourceHidden = !it.sourceHidden) else it },
    dirty = true,
  )
}

/**
 * Hides the outputs without discarding them - which is the whole difference from
 * [withOutputsCleared], and why both are in the menu. Independent of the source's collapse:
 * JupyterLab has a separate command for each and stores them under separate keys.
 */
fun EditorUiState.withOutputsHiddenToggled(cellId: String): EditorUiState {
  if (cells.none { it.id == cellId }) return this
  return copy(
    cells = cells.map { if (it.id == cellId) it.copy(outputsHidden = !it.outputsHidden) else it },
    dirty = true,
  )
}

private fun <T> Map<String, T>.carryOver(from: String, to: String): Map<String, T> =
  get(from)?.let { this + (to to it) } ?: this
