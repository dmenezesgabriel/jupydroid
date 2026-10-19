package com.jupydroid.app.ui.terminal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [TerminalTextSelection.boundsIn]: where the platform's floating toolbar is anchored - the
 * content rect `TextSelectionCursorController.onGetContentRect` (termux-app `3df69d1d`) hands
 * its `ActionMode`, measured from the top-left of column `0` of the view.
 */
class TerminalSelectionBoundsTest {
  private val cell = IntSize(width = 10, height = 20)

  @Test
  fun aWordOnOneRowIsBoundedByItsOwnCells() {
    val selection = TerminalTextSelection(startColumn = 3, startRow = 2, endColumn = 5, endRow = 2)

    assertEquals(Rect(30f, 40f, 60f, 60f), selection.boundsIn(cell, columns = 8, topRow = 0))
  }

  /** A selection over several rows covers them across the whole width, as the reference's does. */
  @Test
  fun aSelectionOverSeveralRowsSpansTheWidth() {
    val selection = TerminalTextSelection(startColumn = 6, startRow = 1, endColumn = 1, endRow = 2)

    assertEquals(Rect(0f, 20f, 80f, 60f), selection.boundsIn(cell, columns = 8, topRow = 0))
  }

  @Test
  fun aScrolledBackViewMeasuresRowsFromItsTopRow() {
    val selection = TerminalTextSelection(startColumn = 0, startRow = -3, endColumn = 1, endRow = -3)

    assertEquals(Rect(0f, 20f, 20f, 40f), selection.boundsIn(cell, columns = 8, topRow = -4))
  }
}
