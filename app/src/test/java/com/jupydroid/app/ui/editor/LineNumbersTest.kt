package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class LineNumbersTest {
  @Test
  fun logicalLineStarts_numbersAnEmptyCellAsOneLine() {
    assertEquals(listOf(0), logicalLineStarts(""))
  }

  @Test
  fun logicalLineStarts_findsTheOffsetEachLineBeginsAt() {
    assertEquals(listOf(0, 2, 8), logicalLineStarts("a\nbcdef\ng"))
  }

  @Test
  fun logicalLineStarts_countsABlankLine() {
    assertEquals(listOf(0, 2, 3), logicalLineStarts("a\n\nb"))
  }

  /** A cell ending in a newline has an empty last line, and Jupyter numbers it. */
  @Test
  fun logicalLineStarts_countsTheEmptyLineAfterATrailingNewline() {
    assertEquals(listOf(0, 2), logicalLineStarts("a\n"))
  }

  /**
   * Two digits from the start so the code does not shift sideways the moment a cell grows past
   * nine lines - the one width change a user would actually notice while typing.
   */
  @Test
  fun gutterDigits_reservesTwoUntilItNeedsMore() {
    assertEquals(2, gutterDigits(1))
    assertEquals(2, gutterDigits(9))
    assertEquals(2, gutterDigits(99))
    assertEquals(3, gutterDigits(100))
  }
}
