package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Immutable

/**
 * The tick a cell shows while a run of cells is being selected, or null on a cell that is not
 * being offered one.
 *
 * Beside [CellStripe] and for the same reason: a tiny presentation type keeps the header's
 * signature honest, and folding "is a selection being made" and "is this cell in it" into one
 * nullable stops the two being passed separately and disagreeing.
 *
 * **[position] is in here because a screen reader needs it.** JupyterLab has no such label - it
 * has a mouse, and shift-clicking the cell you are looking at needs no name - but "Select cell"
 * spoken four times over says nothing about which. It is 1-based and positional, which is
 * `activeCellIndex`'s own basis, so it moves with the cell rather than with its id. It counts the
 * cells **on screen**, not the cells in the file: a collapsed heading's section is not drawn, and a
 * number that counted it would send "Select cell 5" to the third tick anyone can reach.
 *
 * ```
 * CellHeader(cell = cell, mark = CellMark(checked = true, position = 2), …)
 * ```
 */
@Immutable
data class CellMark(val checked: Boolean, val position: Int) {
  /** What the checkbox is called, on one node so its label and its state are read together. */
  val label: String get() = "Select cell $position"
}
