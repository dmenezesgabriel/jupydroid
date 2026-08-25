package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell

/**
 * Which row of the notebook has to be brought on screen because it is about to be typed into.
 *
 * **The reference's `_ensureFocus`, which is three conditions rather than one**
 * (`packages/notebook/src/widget.ts:2885-2905`): edit mode, an active cell whose editor does not
 * already hold the caret, and that cell **not already in the viewport** - only then
 * `scrollToItem(activeCellIndex)`. The third belongs to the caller, since only the list knows what
 * it is showing; the first two are here.
 *
 * **This is not only about inserted cells, and it should not be.** Insertion is where its absence
 * showed - a heading inserted at the end of a section landed below the fold and behind the symbol
 * row, so the command looked like it had done nothing (`evidence/p2f-4-insert-heading/`) - but the
 * reference's rule is about *entering edit mode* wherever the cell is, and a narrower one would
 * leave the same hole for split, paste and undo to fall into.
 *
 * **Why nothing caught it for so long.** The flow that covers insertion asserts the new cell is
 * visible, and it used to pass: focusing the field brings the soft keyboard up, the viewport
 * shrinks, and the list scrolled as a side effect. That is a race, not a rule - it depends on the
 * IME winning against a 5 s `waitForAnimationToEnd` - and when it stopped winning the assertion
 * finally reported what had always been true.
 *
 * ```
 * cellBeingEditedIndex(state.mode, state.selectedCellId, state.cellsOnScreen)   // 2, or null
 * ```
 *
 * @param mode COMMAND scrolls nothing: stepping the selection is not a request to type, and
 *   scrolling on it would pull the document away from someone reading it.
 * @param selectedCellId the active cell - JupyterLab's `activeCellIndex`, by id.
 * @param cellsOnScreen the rows actually drawn, **not** the document: a cell inside a collapsed
 *   section is not a row, so an index into `cells` would name a different one.
 * @return the row to scroll to, or null when nothing should move.
 */
internal fun cellBeingEditedIndex(mode: CellMode, selectedCellId: String?, cellsOnScreen: List<Cell>): Int? {
  if (mode != CellMode.EDIT || selectedCellId == null) return null
  return cellsOnScreen.indexOfFirst { it.id == selectedCellId }.takeIf { it >= 0 }
}
