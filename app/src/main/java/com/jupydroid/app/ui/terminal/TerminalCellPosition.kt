package com.jupydroid.app.ui.terminal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize

/** A cell of the terminal, its row external: `0` is the live screen's top, negative is scrollback. */
data class TerminalCellPosition(val column: Int, val row: Int)

/**
 * The cell under [point], a position measured from the top-left of column `0` of the view whose
 * top row is [topRow] - `TerminalView.getColumnAndRow` (termux-app `3df69d1d`). Clamped to [grid],
 * since the canvas is larger than its whole cells and a press in its margin still means the
 * nearest one.
 *
 * ```
 * terminalCellAt(Offset(35f, 45f), cell = IntSize(10, 20), grid = IntSize(80, 24), topRow = 0)   // column 3, row 2
 * ```
 */
fun terminalCellAt(point: Offset, cell: IntSize, grid: IntSize, topRow: Int): TerminalCellPosition {
  val column = (point.x / cell.width).toInt().coerceIn(0, grid.width - 1)
  val row = (point.y / cell.height).toInt().coerceIn(0, grid.height - 1)
  return TerminalCellPosition(column, topRow + row)
}
