package com.jupydroid.app.ui.terminal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [TerminalSelectionEnd]: where each handle hangs, and which cell a handle dropped at a point
 * makes that end - `TextSelectionHandleView.positionAtCursor` and
 * `TextSelectionCursorController.updatePosition` (termux-app `3df69d1d`).
 */
class TerminalSelectionEndTest {
  private val cell = IntSize(width = 10, height = 20)
  private val grid = IntSize(width = 8, height = 4)
  private val selection = TerminalTextSelection(startColumn = 3, startRow = 1, endColumn = 5, endRow = 1)

  @Test
  fun theStartHangsFromTheLeftEdgeOfItsCellAndTheEndFromTheRightEdgeOfItsCell() {
    assertEquals(Offset(30f, 40f), TerminalSelectionEnd.START.anchorIn(selection, cell, topRow = 0))
    assertEquals(Offset(60f, 40f), TerminalSelectionEnd.END.anchorIn(selection, cell, topRow = 0))
  }

  @Test
  fun aScrolledBackViewHangsTheHandlesLower() {
    assertEquals(Offset(30f, 80f), TerminalSelectionEnd.START.anchorIn(selection, cell, topRow = -2))
  }

  /** A handle that has not moved is the end it already was. */
  @Test
  fun aHandleDroppedOnItsOwnAnchorKeepsItsCell() {
    for (end in TerminalSelectionEnd.entries) {
      val anchor = end.anchorIn(selection, cell, topRow = 0)
      val expected = if (end == TerminalSelectionEnd.START) TerminalCellPosition(3, 1) else TerminalCellPosition(5, 1)
      assertEquals(expected, end.cellUnder(anchor, cell, grid, topRow = 0))
    }
  }

  /** The handle sits on a boundary between cells, so the nearest boundary decides the column. */
  @Test
  fun theNearestCellBoundaryDecidesTheColumn() {
    assertEquals(3, TerminalSelectionEnd.START.cellUnder(Offset(34f, 40f), cell, grid, topRow = 0).column)
    assertEquals(4, TerminalSelectionEnd.START.cellUnder(Offset(36f, 40f), cell, grid, topRow = 0).column)
    assertEquals(5, TerminalSelectionEnd.END.cellUnder(Offset(64f, 40f), cell, grid, topRow = 0).column)
    assertEquals(6, TerminalSelectionEnd.END.cellUnder(Offset(66f, 40f), cell, grid, topRow = 0).column)
  }

  /** The handle hangs below its row, so the row is the one above the point it is dropped at. */
  @Test
  fun theRowIsTheOneTheHandleHangsFrom() {
    assertEquals(2, TerminalSelectionEnd.END.cellUnder(Offset(60f, 60f), cell, grid, topRow = 0).row)
    assertEquals(-1, TerminalSelectionEnd.END.cellUnder(Offset(60f, 60f), cell, grid, topRow = -3).row)
  }

  @Test
  fun aHandleDraggedOffTheGridLandsOnItsNearestCell() {
    assertEquals(TerminalCellPosition(0, 0), TerminalSelectionEnd.START.cellUnder(Offset(-50f, -50f), cell, grid, 0))
    assertEquals(TerminalCellPosition(7, 3), TerminalSelectionEnd.END.cellUnder(Offset(500f, 500f), cell, grid, 0))
  }
}
