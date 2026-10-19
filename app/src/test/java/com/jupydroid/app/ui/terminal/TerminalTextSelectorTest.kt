package com.jupydroid.app.ui.terminal

import com.jupydroid.app.terminal.CellStyle
import com.jupydroid.app.terminal.TerminalGrid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [TerminalTextSelector]: what a long press selects, what Copy reads back, and how the selection
 * and the view follow output - `TerminalView.onScreenUpdated` (termux-app `3df69d1d`).
 */
class TerminalTextSelectorTest {
  private val grid = TerminalGrid(columns = 8, totalRows = 6, screenRows = 3)
  private val selector = TerminalTextSelector { grid }

  @Test
  fun aLongPressSelectsTheWordUnderItAndCopyReadsItBack() {
    write(row = 1, text = "ls -la x")

    selector.selectWordAt(column = 4, row = 1)

    assertEquals(
      TerminalTextSelection(startColumn = 3, startRow = 1, endColumn = 5, endRow = 1),
      selector.selection.value,
    )
    assertEquals("-la", selector.selectedText())
  }

  @Test
  fun closingTheSelectionLeavesNothingToCopy() {
    write(row = 0, text = "word")
    selector.selectWordAt(column = 0, row = 0)

    selector.clear()

    assertNull(selector.selection.value)
    assertNull(selector.selectedText())
  }

  /** `ACTION_COPY` in `TextSelectionCursorController`: the text goes to the clipboard and the selection ends. */
  @Test
  fun copyHandsTheSelectedTextOverAndEndsTheSelection() {
    write(row = 0, text = "ls -la")
    selector.selectWordAt(column = 4, row = 0)
    val copied = mutableListOf<String>()

    selector.copyInto { copied += it }

    assertEquals(listOf("-la"), copied)
    assertNull(selector.selection.value)
  }

  @Test
  fun copyWithNothingSelectedHandsNothingOver() {
    val copied = mutableListOf<String>()

    selector.copyInto { copied += it }

    assertEquals(emptyList<String>(), copied)
  }

  @Test
  fun aSelectionCoversItsRowsBetweenItsEnds() {
    val selection = TerminalTextSelection(startColumn = 5, startRow = 0, endColumn = 2, endRow = 2)

    assertTrue(selection.covers(column = 5, row = 0))
    assertFalse(selection.covers(column = 4, row = 0))
    assertTrue(selection.covers(column = 0, row = 1))
    assertTrue(selection.covers(column = 2, row = 2))
    assertFalse(selection.covers(column = 3, row = 2))
    assertFalse(selection.covers(column = 0, row = 3))
  }

  @Test
  fun withNothingSelectedOutputReturnsTheViewToTheLiveScreen() {
    assertEquals(0, selector.topRowAfterOutput(topRow = -2, rowShift = 1))
  }

  /** The text moved up two rows, so the selection and the view move up with it. */
  @Test
  fun outputMovesTheSelectionAndTheViewUpWithTheText() {
    write(row = 2, text = "word")
    selector.selectWordAt(column = 0, row = 2)
    scroll(rows = 2)

    val topRow = selector.topRowAfterOutput(topRow = 0, rowShift = 2)

    assertEquals(-2, topRow)
    assertEquals(
      TerminalTextSelection(startColumn = 0, startRow = 0, endColumn = 3, endRow = 0),
      selector.selection.value,
    )
    assertEquals("word", selector.selectedText())
  }

  /** Once the view would have to reach past the oldest row kept, the reference gives the selection up. */
  @Test
  fun outputThatPushesTheViewPastTheScrollbackEndsTheSelection() {
    write(row = 0, text = "word")
    selector.selectWordAt(column = 0, row = 0)
    scroll(rows = 1)

    val topRow = selector.topRowAfterOutput(topRow = 0, rowShift = 2)

    assertEquals(0, topRow)
    assertNull(selector.selection.value)
  }

  /**
   * `TextSelectionCursorController.updatePosition` (termux-app `3df69d1d`): the dragged end lands
   * where it is dropped.
   */
  @Test
  fun draggingTheEndMovesItAcrossColumnsAndRows() {
    write(row = 1, text = "ls -la x")
    selector.selectWordAt(column = 4, row = 1)

    selector.moveEnd(TerminalSelectionEnd.END, TerminalCellPosition(column = 2, row = 2))

    assertEquals(
      TerminalTextSelection(startColumn = 3, startRow = 1, endColumn = 2, endRow = 2),
      selector.selection.value,
    )
  }

  @Test
  fun draggingTheStartMovesItLeft() {
    write(row = 1, text = "ls -la x")
    selector.selectWordAt(column = 4, row = 1)

    selector.moveEnd(TerminalSelectionEnd.START, TerminalCellPosition(column = 0, row = 1))

    assertEquals("ls -la", selector.selectedText())
  }

  /** The reference pins a dragged end to the other one rather than swapping them. */
  @Test
  fun theStartDraggedPastTheEndStopsOnIt() {
    write(row = 1, text = "ls -la x")
    selector.selectWordAt(column = 4, row = 1)

    selector.moveEnd(TerminalSelectionEnd.START, TerminalCellPosition(column = 7, row = 1))

    assertEquals(
      TerminalTextSelection(startColumn = 5, startRow = 1, endColumn = 5, endRow = 1),
      selector.selection.value,
    )
  }

  @Test
  fun theEndDraggedAboveTheStartStopsOnItsRowAndColumn() {
    write(row = 1, text = "ls -la x")
    selector.selectWordAt(column = 4, row = 1)

    selector.moveEnd(TerminalSelectionEnd.END, TerminalCellPosition(column = 0, row = 0))

    assertEquals(
      TerminalTextSelection(startColumn = 3, startRow = 1, endColumn = 3, endRow = 1),
      selector.selection.value,
    )
  }

  @Test
  fun theStartDraggedBelowTheEndKeepsItsColumnOnTheEndsRow() {
    write(row = 1, text = "ls -la x")
    selector.selectWordAt(column = 4, row = 1)

    selector.moveEnd(TerminalSelectionEnd.START, TerminalCellPosition(column = 1, row = 2))

    assertEquals(
      TerminalTextSelection(startColumn = 1, startRow = 1, endColumn = 5, endRow = 1),
      selector.selection.value,
    )
  }

  @Test
  fun draggingWithNothingSelectedSelectsNothing() {
    selector.moveEnd(TerminalSelectionEnd.END, TerminalCellPosition(column = 2, row = 0))

    assertNull(selector.selection.value)
  }

  private fun write(row: Int, text: String) =
    text.forEachIndexed { column, char -> grid.setChar(column, row, char.code, CellStyle.NORMAL) }

  private fun scroll(rows: Int) = repeat(rows) { grid.scrollDownOneLine(0, grid.screenRows, CellStyle.NORMAL) }
}
