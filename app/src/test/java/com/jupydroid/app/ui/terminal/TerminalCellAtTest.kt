package com.jupydroid.app.ui.terminal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/** [terminalCellAt]: the cell a press lands on - `TerminalView.getColumnAndRow` (termux-app `3df69d1d`). */
class TerminalCellAtTest {
  private val cell = IntSize(width = 10, height = 20)
  private val grid = IntSize(width = 8, height = 4)

  @Test
  fun aPointFallsInTheCellItIsInside() {
    assertEquals(TerminalCellPosition(column = 3, row = 2), terminalCellAt(Offset(35f, 45f), cell, grid, topRow = 0))
  }

  @Test
  fun aScrolledBackViewCountsRowsFromItsTopRow() {
    assertEquals(TerminalCellPosition(column = 0, row = -3), terminalCellAt(Offset(0f, 30f), cell, grid, topRow = -4))
  }

  /** The canvas is wider and taller than its whole cells, and its padding sits left of column 0. */
  @Test
  fun aPointOutsideTheGridLandsOnTheNearestCell() {
    assertEquals(TerminalCellPosition(column = 7, row = 3), terminalCellAt(Offset(500f, 500f), cell, grid, topRow = 0))
    assertEquals(TerminalCellPosition(column = 0, row = 0), terminalCellAt(Offset(-4f, -1f), cell, grid, topRow = 0))
  }
}
