package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The margins: DECSTBM (`CSI Pt;Pb r`) sets the scrolling region, DECSLRM (`CSI Pl;Pr s`) the
 * horizontal one, and origin mode decides whether the cursor is addressed relative to them.
 *
 * Transcribed whole from `ScrollRegionTest.java` - all seventeen (termux-app `3df69d1d`, Apache 2.0
 * - see [TerminalGrid]). Eight call sites already read `TerminalCursor`'s margins; until this step
 * nothing could set one, so every test here was unreachable rather than merely absent.
 *
 * **`CSI s` means two things**, and which one is DECLRMM's to say: with the mode off it is the
 * ANSI.SYS cursor save [CursorAddressing] has performed since P4f-3b, and with it on it is DECSLRM.
 * That is the correction P4f-3b's row said this step owed.
 */
class ScrollRegionTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  private fun cursorOf(fixture: EmulatorFixture): Pair<Int, Int> =
    fixture.emulator.screen.cursor.let { it.row to it.column }

  private fun styleAt(fixture: EmulatorFixture, row: Int, column: Int): CellStyle =
    fixture.emulator.screen.grid.styleAt(row, column)

  /** DECSTBM with only a top margin, and the reset that puts the whole screen back. */
  @Test
  fun aTopMarginHoldsTheRowsAboveItStill() {
    val fixture = terminal(3, 4).enterString("111222333444").assertLinesAre("111", "222", "333", "444")
    fixture.enterString("$ESC[2r")
    assertEquals(0 to 0, cursorOf(fixture))
    fixture.enterString("\r\n\r\n\r\n\r\nCDEFGH")
      .assertLinesAre("111", "444", "CDE", "FGH")
      .assertHistoryStartsWith("333")
    fixture.enterString("IJK").assertLinesAre("111", "CDE", "FGH", "IJK").assertHistoryStartsWith("444")
    fixture.enterString("$ESC[r").enterString("\r\n\r\n\r\n").enterString("LMNOPQ")
      .assertLinesAre("CDE", "FGH", "LMN", "OPQ")
  }

  /** DECSTBM with a bottom margin: the last row stays put while the ones above it scroll. */
  @Test
  fun aBottomMarginHoldsTheRowsBelowItStill() {
    val fixture = terminal(3, 4).enterString("111222333444").assertLinesAre("111", "222", "333", "444")
    fixture.enterString("$ESC[1;3r")
    assertEquals(0 to 0, cursorOf(fixture))
    fixture.enterString("\r\n\r\nCDEFGH").assertLinesAre("222", "CDE", "FGH", "444").assertHistoryStartsWith("111")
    fixture.enterString("$ESC[r").enterString("\r\n\r\n\r\n").enterString("IJKLMN")
      .assertLinesAre("CDE", "FGH", "IJK", "LMN")
  }

  /** DECSTBM homes the cursor, and under origin mode home is the top margin rather than row 0. */
  @Test
  fun settingTheRegionHomesTheCursorRelativeToOriginMode() {
    val fixture = terminal(3, 4).enterString("111222333444").assertLinesAre("111", "222", "333", "444")
    fixture.enterString("$ESC[?6h$ESC[2r")
    assertEquals(1 to 0, cursorOf(fixture))
  }

  /** DECSLRM's left margin: the first column stops taking part in the scroll. */
  @Test
  fun aLeftMarginKeepsTheColumnsLeftOfItStill() {
    val fixture = terminal(3, 3).enterString("$ESC[?69h$ESC[2sABCDEFG").assertLinesAre("ABC", " DE", " FG")
    fixture.enterString("HI").assertLinesAre("ADE", " FG", " HI")
    fixture.enterString("JK").assertLinesAre("AFG", " HI", " JK")
    fixture.enterString("\n").assertLinesAre("AHI", " JK", "   ")
  }

  /** And its right margin, with a column outside it written before the mode was on. */
  @Test
  fun aRightMarginKeepsTheColumnsRightOfItStill() {
    val fixture = terminal(3, 3).enterString("YYY$ESC[?69h$ESC[1;2sABCDEF").assertLinesAre("ABY", "CD ", "EF ")
    fixture.enterString("GH").assertLinesAre("CDY", "EF ", "GH ")
    fixture.enterString("IJ").assertLinesAre("EFY", "GH ", "IJ ")
    fixture.enterString("\n").assertLinesAre("GHY", "IJ ", "   ")
  }

  /** All four margins at once, under origin mode, so each sequence homes into the box. */
  @Test
  fun allFourMarginsBoxTheScrollingArea() {
    val fixture = terminal(4, 4).enterString("ABCDEFGHIJKLMNOP").assertLinesAre("ABCD", "EFGH", "IJKL", "MNOP")
    fixture.enterString("$ESC[?6h$ESC[2;3r")
    assertEquals(1 to 0, cursorOf(fixture))
    fixture.enterString("$ESC[?69h$ESC[2;3s")
    assertEquals(1 to 1, cursorOf(fixture))
    fixture.enterString("QRST").assertLinesAre("ABCD", "EQRH", "ISTL", "MNOP")
    fixture.enterString("UV").assertLinesAre("ABCD", "ESTH", "IUVL", "MNOP")
  }

  /** DECCOLM clears the scroll margins, which is what makes the `reset` utility work. */
  @Test
  fun columnModeResetsTheScrollMargins() {
    terminal(3, 4).enterString("111222333444")
      .enterString("$ESC[2r$ESC[?3h\r\nABCDEFGHIJKL")
      .assertLinesAre("ABC", "DEF", "GHI", "JKL")
  }

  /** A cursor placed below the bottom margin writes there and does not scroll the region. */
  @Test
  fun writingBelowTheRegionDoesNotScrollIt() {
    terminal(3, 4).enterString("$ESC[0;2rhi$ESC[4;0Hyou").assertLinesAre("hi ", "   ", "   ", "you")
  }

  /** NEL goes to the left margin, and under origin mode that is the margin rather than column 0. */
  @Test
  fun nextLineRespectsTheLeftMargin() {
    terminal(3, 3).enterString("$ESC[?69h$ESC[2sABC${ESC}ED").assertLinesAre("ABC", "D  ", "   ")
    terminal(3, 3).enterString("$ESC[?69h$ESC[2sABC$ESC[?6h${ESC}ED").assertLinesAre("ABC", " D ", "   ")
  }

  /** RI scrolls only what lies between the horizontal margins. */
  @Test
  fun reverseIndexRespectsTheLeftMargin() {
    terminal(4, 3).enterString("ABCD$ESC[?69h$ESC[2;3s$ESC[?6h${ESC}M").assertLinesAre("A  D", " BC ", "    ")
  }

  /** SD likewise. */
  @Test
  fun scrollDownRespectsTheLeftMargin() {
    terminal(4, 3).enterString("ABCD$ESC[?69h$ESC[2;3s$ESC[?6h$ESC[2T").assertLinesAre("A  D", "    ", " BC ")
  }

  /** DECBI, with and without a left margin - `vttest` 11.3.2 test 7. */
  @Test
  fun backIndexRespectsTheLeftMargin() {
    val fixture = terminal(3, 3).enterString("ABCDEF${ESC}6H").assertLinesAre("ABC", "DHF", "   ")
    fixture.enterString("${ESC}6${ESC}6I").assertLinesAre("ABC", "IHF", "   ")
    fixture.enterString("${ESC}6${ESC}6").assertLinesAre(" AB", " IH", "   ")
    terminal(3, 3).enterString("$ESC[?69h$ESC[2sABCDEF${ESC}6${ESC}6").assertLinesAre("A B", "  D", "  F")
  }

  /** DECFI, with and without a right margin - `vttest` 11.3.2 test 8. */
  @Test
  fun forwardIndexRespectsTheRightMargin() {
    val fixture = terminal(3, 3).enterString("ABCD${ESC}9E").assertLinesAre("ABC", "D E", "   ")
    fixture.enterString("${ESC}9").assertLinesAre("BC ", " E ", "   ")
    terminal(3, 3).enterString("$ESC[?69h$ESC[0;2sABCD${ESC}9").assertLinesAre("B  ", "D  ", "   ")
  }

  /** SD inside a region scrolls only the region. */
  @Test
  fun scrollDownStaysInsideTheRegion() {
    terminal(2, 5).enterString("1\r\n2\r\n3\r\n4\r\n5")
      .enterString("$ESC[3r").enterString("$ESC[2T")
      .assertLinesAre("1 ", "2 ", "  ", "  ", "3 ")
  }

  /** And a cursor below the region scrolls nothing at all when it runs off the last row. */
  @Test
  fun writingBelowTheRegionScrollsNothing() {
    terminal(2, 5).enterString("1\r\n2\r\n3\r\n4\r\n5")
      .enterString("$ESC[1;3r")
      .enterString("$ESC[4;1H")
      .enterString("QQ\r\nRR\r\n\r\n\r\nYY")
      .assertLinesAre("1 ", "2 ", "3 ", "QQ", "YY")
  }

  /** termux-app#1340: CUU and CUD clamp to the screen, not to the region. */
  @Test
  fun theRegionDoesNotLimitCursorMovement() {
    terminal(6, 4).enterString("$ESC[4;7r$ESC[3;1Haaa$ESC[Axxx")
      .assertLinesAre("      ", "   xxx", "aaa   ", "      ")
    terminal(6, 4).enterString("$ESC[1;3r$ESC[3;1Haaa$ESC[Bxxx")
      .assertLinesAre("      ", "      ", "aaa   ", "   xxx")
  }

  /**
   * termux-packages#12556: with a left margin, the column outside it is not part of the scroll,
   * so it keeps the terminal's own colours where every column inside takes the current style.
   */
  @Test
  fun scrollingWithAMarginLeavesTheColumnOutsideItUncoloured() {
    val size = 3
    val fixture = terminal(size, size)
      .enterString("$ESC[?69h$ESC[2s")
      .enterString("$ESC[32;43m")
      .enterString("\r\n\r\n\r\n\r\n\r\n")
    for (row in 0 until size) {
      for (column in 0 until size) {
        val style = styleAt(fixture, row, column)
        val expectedFore = if (column == 0) CellStyle.COLOR_INDEX_FOREGROUND else 2
        val expectedBack = if (column == 0) CellStyle.COLOR_INDEX_BACKGROUND else 3
        assertEquals("foreground at $row,$column", expectedFore, style.foreColor)
        assertEquals("background at $row,$column", expectedBack, style.backColor)
      }
    }
  }

  /**
   * DECIC (`CSI Pn ' }`), which no reference test covers - expectations read off `RefScreen`. It
   * works on the **whole screen height**, not the scrolling region, and stops at the right margin.
   */
  @Test
  fun insertColumnsShiftsTheWholeScreenRightAndStopsAtTheRightMargin() {
    terminal(4, 2).enterString("ABCDEFGH$ESC[1;2H$ESC[1'}").assertLinesAre("A BC", "E FG")
    terminal(4, 2).enterString("ABCDEFGH$ESC[1;2H$ESC[2'}").assertLinesAre("A  B", "E  F")
    terminal(4, 2).enterString("ABCDEFGH$ESC[1;2H$ESC[9'}").assertLinesAre("A   ", "E   ")
    terminal(4, 2).enterString("ABCDEFGH$ESC[?69h$ESC[1;3s$ESC[1;2H$ESC[1'}").assertLinesAre("A BD", "E FH")
  }

  /**
   * DECDC (`CSI Pn ' ~`). **It blanks nothing**: the columns it shifts left leave their old content
   * standing in the last column, so `ABCD` becomes `ACDD` rather than `ACD `. That is the
   * reference's `blockCopy` with no `blockClear` after it, measured rather than read off the
   * source, and transcribed rather than corrected.
   */
  @Test
  fun deleteColumnsShiftsLeftAndLeavesTheColumnItVacatedStanding() {
    terminal(4, 2).enterString("ABCDEFGH$ESC[1;2H$ESC[1'~").assertLinesAre("ACDD", "EGHH")
  }

  /**
   * **A declared divergence, and a crash closed with it.** DECSLRM homes the cursor to column 0
   * while the left margin is 1, so the cursor sits *outside* its own margin; the reference's
   * backspace tests `==` and walks it to column -1 from there, which it then throws on under
   * insert mode. Both lines here abort the reference, and neither is a state a terminal can be in.
   *
   * `TerminalScreen.backspace` says what changed and why; the measurement is in
   * `evidence/p4-terminal/backspace-past-the-left-margin.md`.
   */
  @Test
  fun backspaceDoesNotStepLeftOfTheLeftMargin() {
    val fixture = terminal(10, 4).enterString("$ESC[?69h$ESC[2s\bX")
    fixture.assertLineIs(0, "X         ")
    assertEquals(0 to 1, cursorOf(fixture))

    val inserting = terminal(10, 4).enterString("$ESC[?69h$ESC[2s$ESC[4h\bX")
    inserting.assertLineIs(0, "X         ")
    assertEquals(0 to 1, cursorOf(inserting))
  }

  /**
   * DECSLRM's right margin is **held above its left**, so `CSI 3 ; 1 s` on four columns is a
   * one-column region at column 2 rather than a right margin left of the left one. Expectations off
   * `RefScreen`; the differential cannot reach this, since its left-margin forms are excluded.
   */
  @Test
  fun theRightMarginIsHeldAboveTheLeftMargin() {
    terminal(4, 2).enterString("$ESC[?69h$ESC[3;1sABCDEF").assertLinesAre("ABE ", "  F ")
  }

  /**
   * CBT stops at the **left margin**, not column 0 - so a tab stop below the margin is not somewhere
   * a backward tab can reach. The stop at column 1 is set with HTS before the margin exists.
   */
  @Test
  fun backwardTabulationStopsAtTheLeftMargin() {
    val bounded = terminal(10, 2).enterString("$ESC[2G${ESC}H$ESC[?69h$ESC[4s$ESC[6G$ESC[ZX")
    bounded.assertLineIs(0, "   X      ")
    assertEquals(0 to 4, cursorOf(bounded))

    val unbounded = terminal(10, 2).enterString("$ESC[2G${ESC}H$ESC[6G$ESC[ZX")
    unbounded.assertLineIs(0, " X        ")
    assertEquals(0 to 2, cursorOf(unbounded))
  }

  /**
   * **CBT leaves a pending wrap standing**, where an addressed move clears it: the reference assigns
   * the column rather than going through its setter. Only observable inside a one-column region,
   * where the column CBT lands on is also the last one - which is why this needed a margin and why
   * the mutation for it survived every table until one existed.
   */
  @Test
  fun backwardTabulationLeavesAPendingWrapStanding() {
    val region = "$ESC[?69h$ESC[3;3s$ESC[3G"
    terminal(4, 3).enterString("${region}A$ESC[ZB").assertLinesAre("  A ", "  B ", "    ")
    terminal(4, 3).enterString("${region}A$ESC[3GB").assertLinesAre("  B ", "    ", "    ")
  }

  /**
   * **A linefeed on the bottom margin stays put when that margin is the last row.** The predicate
   * is `row >= bottomMargin`, and it differs from `row > bottomMargin` in exactly one place: at
   * `row == bottomMargin`, where the `>=` form takes the below-region branch and holds the cursor
   * on the last row, and the `>` form falls through and steps it **off the screen**.
   *
   * That needs `bottomMargin == rows - 1`, which is why a region ending at the screen's own bottom
   * does not show it - the first case below scrolls, the second does not.
   *
   * **The differential is not a reliable witness here.** It was unreachable through P4f-4b,
   * detected at P4f-5a, and survived again at P4f-5c, where a full-suite sweep found the mutation
   * covered by no test at all. Expectations read off `RefScreen`.
   */
  @Test
  fun aLinefeedOnABottomMarginThatIsTheLastRowStaysThere() {
    val filled = "1\r\n2\r\n3\r\n4\r\n5"
    val held = terminal(2, 5).enterString("$filled$ESC[1;4r$ESC[5;1HQ\nR")
    held.assertLinesAre("1 ", "2 ", "3 ", "4 ", "QR")
    assertEquals(4 to 1, cursorOf(held))

    val scrolled = terminal(2, 5).enterString("$filled$ESC[1;5r$ESC[5;1HQ\nR")
    scrolled.assertLinesAre("2 ", "3 ", "4 ", "Q ", " R")
    assertEquals(4 to 1, cursorOf(scrolled))
  }

  private companion object {
    const val ESC = "\u001B"
  }
}
