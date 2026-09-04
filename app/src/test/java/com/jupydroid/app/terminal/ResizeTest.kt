package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Every method of `terminal-emulator/src/test/java/com/termux/terminal/ResizeTest.java`
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]).
 */
class ResizeTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  /** `testResizeWhenHasHistory`. Growing rows pulls history back onto the screen; shrinking refiles it. */
  @Test
  fun growingRowsPullsHistoryBackAndShrinkingFilesItAgain() {
    val fixture = terminal(3, 3).enterString("111222333444555666777888999")
    fixture.assertCursorAt(2, 2).assertLinesAre("777", "888", "999")
    fixture.resize(3, 5).assertCursorAt(4, 2).assertLinesAre("555", "666", "777", "888", "999")
    fixture.resize(3, 3).assertCursorAt(2, 2).assertLinesAre("777", "888", "999")
  }

  /** `testResizeWhenInAltBuffer`. A resize behaves the same in either buffer, and each keeps its own content. */
  @Test
  fun resizingWorksTheSameInEitherBufferAndEachKeepsItsOwnContent() {
    val fixture = terminal(3, 3).enterString("a\r\ndef$")
    fixture.assertLinesAre("a  ", "def", "$  ").assertCursorAt(2, 1)
    fixture.resize(3, 5).assertLinesAre("a  ", "def", "$  ", "   ", "   ").assertCursorAt(2, 1)
    fixture.resize(3, 3).assertLinesAre("a  ", "def", "$  ").assertCursorAt(2, 1)
    fixture.enterString("$ESC[?1049h").assertLinesAre("   ", "   ", "   ").assertCursorAt(2, 1)
    fixture.enterString("h").assertLinesAre("   ", "   ", " h ").assertCursorAt(2, 2)
    fixture.resize(3, 5).resize(3, 3)
    fixture.enterString("$ESC[?1049l").assertLinesAre("a  ", "def", "$  ").assertCursorAt(2, 1)
  }

  /**
   * `testShrinkingInAltBuffer`. Resizing while the alternate screen is live leaves the main
   * buffer stale; leaving catches it up before the character that follows lands - P4h-4's gap.
   */
  @Test
  fun leavingAShrunkAlternateScreenCatchesTheStaleMainBufferUp() {
    val fixture = terminal(3, 5).enterString("A\r\nB\r\nC\r\nD\r\nE")
    fixture.assertLinesAre("A  ", "B  ", "C  ", "D  ", "E  ")
    fixture.enterString("$ESC[?1049h").assertLinesAre("   ", "   ", "   ", "   ", "   ")
    fixture.resize(3, 3).enterString("$ESC[?1049lF").assertLinesAre("C  ", "D  ", "EF ")
  }

  /** `testResizeAfterNewlineWhenInAltBuffer`. A resize inside the alternate screen leaves the main screen alone. */
  @Test
  fun resizingInTheAlternateBufferAfterANewlineDoesNotDisturbTheMainScreen() {
    val fixture = terminal(3, 3)
    fixture.enterString("a\r\nb\r\nc\r\nd\r\ne\r\nf\r\n").assertLinesAre("e  ", "f  ", "   ").assertCursorAt(2, 0)
    fixture.assertLineWraps(false, false, false)
    fixture.enterString("$ESC[?1049h").assertLinesAre("   ", "   ", "   ").assertCursorAt(2, 0)
    fixture.enterString("h").assertLinesAre("   ", "   ", "h  ").assertCursorAt(2, 1)
    fixture.resize(3, 5).assertLinesAre("   ", "   ", "h  ", "   ", "   ").assertCursorAt(2, 1)
    fixture.resize(3, 3).assertLinesAre("   ", "   ", "h  ").assertCursorAt(2, 1)
    fixture.enterString("$ESC[?1049l").assertLinesAre("e  ", "f  ", "   ").assertCursorAt(2, 0)
  }

  /** `testResizeAfterHistoryWraparound`. A thousand scrolled lines later, a resize still shows the right rows. */
  @Test
  fun resizingAfterTheScrollbackWrapsAroundStillShowsTheRightRows() {
    val cols = 10
    val rows = 3
    val fixture = terminal(cols, rows)
    for (i in 0 until 1000) {
      val line = i.toString()
      fixture.enterString(line)
      if (i > rows) fixture.assertLineIs(rows - 1, line.padEnd(cols))
      fixture.enterString("\r\n")
    }
    fixture.assertLinesAre("998       ", "999       ", "          ")
    fixture.resize(cols, 2).assertLinesAre("999       ", "          ")
    fixture.resize(cols, 5).assertLinesAre("996       ", "997       ", "998       ", "999       ", "          ")
    fixture.resize(cols, rows).assertLinesAre("998       ", "999       ", "          ")
  }

  /** `testVerticalResize`. A row-only resize keeps a cell's style, and growth fills in the current one. */
  @Test
  fun aVerticalResizeKeepsCellStyleAndFillsGrowthWithTheCurrentOne() {
    val rows = 5
    val cols = 3
    val fixture = terminal(cols, rows)
    fixture.enterString("$ESC[38;5;119m$ESC[48;5;129m$ESC[2J")
    assertStyleEverywhere(fixture, rows, cols, fore = 119) { 129 }
    fixture.enterString("11\r\n22")
    fixture.assertLinesAre("11 ", "22 ", "   ", "   ", "   ").assertLineWraps(false, false, false, false, false)
    fixture.resize(cols, rows - 2).assertLinesAre("11 ", "22 ", "   ")
    assertStyleEverywhere(fixture, rows - 2, cols, fore = 119) { 129 }
    fixture.enterString("$ESC[48;5;200m")
    fixture.resize(cols, rows)
    assertStyleEverywhere(fixture, rows, cols, fore = 119) { r -> if (r >= 3) 200 else 129 }
  }

  /** `testHorizontalResize`. A column resize reflows every wrapped row, and the cursor follows its character. */
  @Test
  fun aColumnResizeReflowsWrappedRowsAndTheCursorFollowsItsCharacter() {
    val cols = 5
    val rows = 5
    val fixture = terminal(cols, rows).enterString("1111\r\n2222\r\n3333\r\n4444\r\n5555")
    fixture.assertCursorAt(4, 4).assertLinesAre("1111 ", "2222 ", "3333 ", "4444 ", "5555 ")
    fixture.assertLineWraps(false, false, false, false, false)
    fixture.resize(cols + 2, rows).assertLinesAre("1111   ", "2222   ", "3333   ", "4444   ", "5555   ")
    fixture.assertCursorAt(4, 4).assertLineWraps(false, false, false, false, false)
    fixture.resize(cols, rows).assertLinesAre("1111 ", "2222 ", "3333 ", "4444 ", "5555 ")
    fixture.assertCursorAt(4, 4).assertLineWraps(false, false, false, false, false)
    fixture.resize(cols - 1, rows).assertLinesAre("2222", "3333", "4444", "5555", "    ")
    fixture.assertCursorAt(4, 0).assertLineWraps(false, false, false, true, false)
    fixture.resize(cols - 2, rows).assertLinesAre("3  ", "444", "4  ", "555", "5  ")
    fixture.assertCursorAt(4, 1).assertLineWraps(false, true, false, true, false)
    fixture.resize(cols, rows).assertLinesAre("1111 ", "2222 ", "3333 ", "4444 ", "5555 ")
    fixture.assertCursorAt(4, 4).assertLineWraps(false, false, false, false, false)
  }

  /** `testLineWrap`. A column resize reflows a wrapped row, and shrinking back restores its wrap. */
  @Test
  fun resizingColumnsReflowsAWrappedRowAndRestoresItsWrapOnTheWayBack() {
    val rows = 3
    val cols = 5
    val fixture = terminal(cols, rows).enterString("111111")
    fixture.assertLinesAre("11111", "1    ", "     ").assertCursorAt(1, 1).assertLineWraps(true, false, false)
    fixture.resize(7, rows).assertCursorAt(0, 6).assertLinesAre("111111 ", "       ", "       ")
    fixture.assertLineWraps(false, false, false)
    fixture.resize(cols, rows).assertCursorAt(1, 1).assertLinesAre("11111", "1    ", "     ")
    fixture.assertLineWraps(true, false, false)
    fixture.enterString("2").assertLinesAre("11111", "12   ", "     ").assertLineWraps(true, false, false)
    fixture.enterString("123").assertLinesAre("11111", "12123", "     ").assertLineWraps(true, false, false)
    fixture.enterString("W").assertLinesAre("11111", "12123", "W    ").assertLineWraps(true, true, false)
    val second = terminal(cols, rows).enterString("1234512345")
    second.assertLinesAre("12345", "12345", "     ").assertLineWraps(true, false, false)
    second.enterString("W").assertLinesAre("12345", "12345", "W    ").assertLineWraps(true, true, false)
  }

  /** `testCursorPositionWhenShrinking`. A cursor above the row a resize cuts is left exactly where it stood. */
  @Test
  fun shrinkingAndGrowingRowsLeavesACursorAboveTheCutUntouched() {
    val fixture = terminal(3, 5).enterString("$ ")
    fixture.assertLinesAre("$  ", "   ", "   ", "   ", "   ").assertCursorAt(0, 2)
    fixture.resize(3, 3).assertLinesAre("$  ", "   ", "   ").assertCursorAt(0, 2)
    fixture.resize(3, 5).assertLinesAre("$  ", "   ", "   ", "   ", "   ").assertCursorAt(0, 2)
  }

  /** `testResizeWithCombiningCharInLastColumn`. A combining character and its colour survive a column grow. */
  @Test
  fun growingColumnsCarriesACombiningCharacterAndItsColourAcrossTheReflow() {
    val plain = terminal(3, 3).enterString("ABĈDEF")
    plain.assertLinesAre("ABĈ", "DEF", "   ")
    plain.resize(4, 3).assertLinesAre("ABĈD", "EF  ", "    ")
    val coloured = terminal(3, 3).enterString("$ESC[37mA$ESC[35mB$ESC[33mĈ$ESC[32mD$ESC[31mE$ESC[34mF")
    coloured.assertLinesAre("ABĈ", "DEF", "   ")
    coloured.resize(4, 3).assertLinesAre("ABĈD", "EF  ", "    ")
    coloured.assertForegroundIndices(intArrayOf(7, 5, 3, 2), intArrayOf(1, 4, 4, 4), intArrayOf(4, 4, 4, 4))
  }

  /** `testResizeWithLineWrappingContinuing`. A wrapped row keeps reflowing correctly across repeated resizes. */
  @Test
  fun aWrappedRowKeepsReflowingCorrectlyThroughRepeatedColumnResizes() {
    val fixture = terminal(5, 3).enterString("\r\nAB DE")
    fixture.assertLinesAre("     ", "AB DE", "     ")
    fixture.resize(4, 3).assertLinesAre("AB D", "E   ", "    ")
    fixture.resize(3, 3).assertLinesAre("AB ", "DE ", "   ")
    fixture.resize(5, 3).assertLinesAre("     ", "AB DE", "     ")
  }

  /** `testResizeWithWideChars`. A surrogate pair whose display width is one stays whole across a reflow. */
  @Test
  fun resizingColumnsKeepsASurrogatePairWholeAcrossTheReflow() {
    val oneWide = String(Character.toChars(TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE))
    val fixture = terminal(4, 3).enterString(oneWide).enterString("\r\n")
    fixture.enterString(oneWide).assertLinesAre("$oneWide   ", "$oneWide   ", "    ")
    fixture.resize(3, 3).assertLinesAre("$oneWide  ", "$oneWide  ", "   ")
    fixture.enterString(oneWide).assertLinesAre("$oneWide  ", "$oneWide$oneWide ", "   ")
  }

  /** `testResizeWithMoreWideChars`. Shrinking columns wraps plain and wide characters onto separate rows alike. */
  @Test
  fun shrinkingColumnsWrapsPlainAndWideCharactersOntoSeparateRowsAlike() {
    val plain = terminal(5, 4).enterString("qqrr")
    plain.assertLinesAre("qqrr ", "     ", "     ", "     ")
    plain.resize(2, 4).assertLinesAre("qq", "rr", "  ", "  ")
    plain.resize(5, 4).assertLinesAre("qqrr ", "     ", "     ", "     ")
    val wide = terminal(5, 4).enterString("ＱＲ")
    wide.assertLinesAre("ＱＲ ", "     ", "     ", "     ")
    wide.resize(2, 4).assertLinesAre("Ｑ", "Ｒ", "  ", "  ")
    wide.resize(5, 4).assertLinesAre("ＱＲ ", "     ", "     ", "     ")
  }

  /**
   * P4i-2b: a resize's cell-pixel pair is optional and defaults to "unchanged", not to
   * [TerminalEmulator.DEFAULT_CELL_WIDTH_PIXELS]/`HEIGHT_PIXELS` - a plain columns/rows resize
   * must not quietly forget a cell size a caller already reported.
   */
  @Test
  fun aPlainResizeLeavesTheCellPixelSizeUntouched() {
    val fixture = terminal(3, 3)
    fixture.resize(3, 3, cellWidthPixels = 8, cellHeightPixels = 18)
    fixture.assertReplyTo("$ESC[16t", "$ESC[6;18;8t")
    fixture.resize(5, 5)
    fixture.assertReplyTo("$ESC[16t", "$ESC[6;18;8t")
  }

  private fun assertStyleEverywhere(fixture: EmulatorFixture, rows: Int, cols: Int, fore: Int, back: (Int) -> Int) {
    for (r in 0 until rows) {
      for (c in 0 until cols) {
        assertEquals(fore, fixture.getStyleAt(r, c).foreColor)
        assertEquals("wrong at row=$r", back(r), fixture.getStyleAt(r, c).backColor)
      }
    }
  }

  private companion object {
    const val ESC = "\u001B"

    /** A musical symbol (U+1D11E): two UTF-16 chars, `WcWidth` reports one column. */
    const val TWO_JAVA_CHARS_DISPLAY_WIDTH_ONE = 0x1D11E
  }
}
