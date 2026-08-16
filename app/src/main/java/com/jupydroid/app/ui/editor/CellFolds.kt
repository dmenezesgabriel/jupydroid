package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell

/**
 * Folding a cell's halves away, as pure functions over [EditorUiState].
 *
 * Split out of [CellEdits] on detekt's `TooManyFunctions` when P2d-4 added the second clear, and
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

private fun EditorUiState.folding(
  cellIds: List<String>,
  fold: (Cell) -> Cell,
): EditorUiState {
  val folding = cellIds.toSet()
  if (cells.none { it.id in folding }) return this
  return copy(cells = cells.map { if (it.id in folding) fold(it) else it }, dirty = true)
}
