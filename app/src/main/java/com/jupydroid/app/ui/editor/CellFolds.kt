package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind

/**
 * Folding a cell's halves away, as pure functions over [EditorUiState].
 *
 * Split out of [CellEdits] on detekt's `TooManyFunctions` when the second clear arrived, and
 * the count was pointing at a seam that was already there: **a fold discards nothing.** Every
 * other transform next door changes which cells the notebook has, what they say, or what results
 * they carry; these two change only what is drawn, and they are the only ones whose state
 * round-trips through nbformat metadata rather than through the document proper
 * (`data/disk/CellCollapse.kt`).
 *
 * The pair with the opposite claim is [EditorUiState.withOutputsCleared], which is why its
 * docstring names this file: one hides the results, the other throws them away, and the cell menu
 * offers both a row apart.
 *
 * Under [CellEdits]'s rules throughout - pure, and a stale id is a no-op.
 */

/**
 * Folds or unfolds the source of a whole run, to **one** state rather than each cell's opposite.
 *
 * JupyterLab has `hideCode` and `showCode` as separate commands over the selection
 * (`packages/notebook/src/actions.tsx:1877-1910`), so a run always ends up uniform. Toggling each
 * cell against its own state would invert a mixed run and leave it exactly as mixed - and the menu
 * row that asked for it says one thing, so it has to do one thing. Which state is the caller's,
 * read off the head cell, which is also what the row's label was written from.
 */
fun EditorUiState.withSourceHiddenSet(cellIds: List<String>, hidden: Boolean): EditorUiState =
  folding(cellIds) { it.copy(sourceHidden = hidden) }

/**
 * Hides the outputs without discarding them - which is the whole difference from
 * [withOutputsCleared], and why both are in the menu. Independent of the source's collapse:
 * JupyterLab has a separate command for each and stores them under separate keys.
 */
fun EditorUiState.withOutputsHiddenSet(cellIds: List<String>, hidden: Boolean): EditorUiState =
  folding(cellIds) { it.copy(outputsHidden = hidden) }

/**
 * Bounds or unbounds a code cell's outputs - `notebook:enable/disable-output-scrolling`.
 *
 * **Code cells only, and silently so.** The reference's own write loop guards on
 * `cell.model.type === 'code'` inside `enableOutputScrolling`/`disableOutputScrolling`
 * (`packages/notebook/src/actions.tsx:2081-2114`), not only in the read that decides the toggle -
 * a markdown cell caught up in a run because it sits between two marked code cells is left with
 * no `outputsScrolled` of its own to set, so it has to be filtered here rather than trusted to the
 * caller.
 */
fun EditorUiState.withOutputsScrolledSet(cellIds: List<String>, scrolled: Boolean): EditorUiState =
  folding(toCodeCellIds(cellIds)) { it.copy(outputsScrolled = scrolled) }

/**
 * `isOutputScrollingEnabled` (`packages/notebook-extension/src/index.ts:2780-2796`) - true only
 * when every selected **code** cell is already scrolled, and false when there are none. What
 * `notebook:toggle-output-scrolling` inverts, and the one predicate among the folds that reads
 * more than the head cell - see [CellAction.ToggleOutputsScrolled].
 */
internal fun EditorUiState.allMarkedCodeCellsScrolled(cellIds: List<String>): Boolean {
  val codeCells = cells.filter { it.id in cellIds && it.kind == CellKind.CODE }
  return codeCells.isNotEmpty() && codeCells.all { it.outputsScrolled }
}

private fun EditorUiState.toCodeCellIds(cellIds: List<String>): List<String> =
  cells.filter { it.id in cellIds && it.kind == CellKind.CODE }.map { it.id }

private fun EditorUiState.folding(cellIds: List<String>, fold: (Cell) -> Cell): EditorUiState {
  val folding = cellIds.toSet()
  if (cells.none { it.id in folding }) return this
  return copy(cells = cells.map { if (it.id in folding) fold(it) else it }, dirty = true)
}
