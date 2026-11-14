package com.jupydroid.app.ui.terminal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.IntSize

/**
 * One end of a [TerminalTextSelection], and the handle that drags it. A handle hangs from the
 * bottom of its row on the edge of the span - the start from its first cell's left edge, the end
 * from its last cell's right edge - as `TextSelectionHandleView.positionAtCursor` places them
 * (termux-app `3df69d1d`, which passes `mSelX2 + 1` for the end).
 *
 * ```
 * val anchor = TerminalSelectionEnd.END.anchorIn(selection, cell, topRow)
 * TerminalSelectionEnd.END.cellUnder(anchor + dragged, cell, grid, topRow)   // the new last cell
 * ```
 */
enum class TerminalSelectionEnd {
  START,
  END,
  ;

  /**
   * Where this end's handle hangs from, in pixels from the top-left of column `0` of the view whose
   * top row is [topRow].
   */
  fun anchorIn(selection: TerminalTextSelection, cell: Size, topRow: Int): Offset {
    val (column, row) = when (this) {
      START -> selection.startColumn to selection.startRow
      END -> selection.endColumn + 1 to selection.endRow
    }
    return Offset(column * cell.width, (row - topRow + 1) * cell.height)
  }

  /**
   * The cell this end takes when its handle hangs from [anchor]: the row above the anchor, and the
   * cell on this end's side of the nearest boundary between cells, through [terminalCellAt], so it
   * is clamped to the view as a press is.
   */
  fun cellUnder(anchor: Offset, cell: Size, grid: IntSize, topRow: Int): TerminalCellPosition {
    val halfCell = Offset(cell.width / 2, cell.height / 2)
    val inside = when (this) {
      START -> Offset(anchor.x + halfCell.x, anchor.y - halfCell.y)
      END -> anchor - halfCell
    }
    return terminalCellAt(inside, cell, grid, topRow)
  }
}
