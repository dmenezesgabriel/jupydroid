package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [wordColumnsAt]: what a long press selects first, after `TextSelectionCursorController
 * .setInitialTextSelectionPosition` (termux-app `3df69d1d`).
 */
class GridWordTest {
  private val plain = CellStyle(0L)

  @Test
  fun aPressOnALetterTakesEveryPrintingNeighbourOnItsRow() {
    val grid = gridWith("ab cde f")
    assertEquals(3..5, grid.wordColumnsAt(column = 4, row = 0))
    assertEquals("cde", grid.selectedText(3, 0, 5, 0))
  }

  @Test
  fun aWordReachesBothEdgesOfTheRow() {
    val grid = gridWith("abcdefgh")
    assertEquals(0..7, grid.wordColumnsAt(column = 0, row = 0))
  }

  /** The reference grows a blank cell into the word beside it; here a blank stays one cell. */
  @Test
  fun aPressOnABlankTakesOnlyThatCell() {
    val grid = gridWith("ab cd")
    assertEquals(2..2, grid.wordColumnsAt(column = 2, row = 0))
  }

  /**
   * A wrapped row reads its last column back as a space even when it is blank - `GridSelection`
   * keeps a wrapped row's trailing spaces - so "prints" has to mean not blank, not not empty.
   */
  @Test
  fun aBlankLastColumnOfAWrappedRowIsNotPartOfTheWordBeforeIt() {
    val grid = gridWith("abc     ")
    grid.rowAt(0).lineWrap = true
    assertEquals(0..2, grid.wordColumnsAt(column = 1, row = 0))
  }

  @Test
  fun aWideCharacterIsTakenWithBothOfItsColumns() {
    val grid = TerminalGrid(columns = 8, totalRows = 3, screenRows = 3)
    grid.setChar(0, 0, 'a'.code, plain)
    grid.setChar(1, 0, WIDE_CHARACTER, plain)
    assertEquals(0..2, grid.wordColumnsAt(column = 0, row = 0))
  }

  private fun gridWith(text: String): TerminalGrid {
    val grid = TerminalGrid(columns = text.length, totalRows = 3, screenRows = 3)
    text.forEachIndexed { column, char -> grid.setChar(column, 0, char.code, plain) }
    return grid
  }

  private companion object {
    /** `中`, two columns wide. */
    const val WIDE_CHARACTER = 0x4E2D
  }
}
