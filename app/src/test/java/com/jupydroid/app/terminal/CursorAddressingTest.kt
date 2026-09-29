package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The control sequences that address the cursor: the motions, the tab stops, and the ANSI.SYS
 * save and restore.
 *
 * `CursorAndScreenTest`'s `testCursorUp` / `testCursorDown` / `testCursorForward` / `testCursorBack`
 * / `testCursorHorizontalPositionAbsolute`, the ANSI.SYS halves of its two save/restore tests, and
 * `UnicodeInputTest`'s `testWideCharacterDeletion` / `testWideCharOverwriting` are transcribed here
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]). The halves that set a left or right
 * margin need `CSI ? 69 h` and DECSLRM and go with those.
 *
 * The cases upstream covers only in passing - the clamps, the absent and zero parameters, CNL/CPL,
 * VPA/VPR/HPR, CHT/CBT and the tab clears - had their **expectations read off the reference
 * itself** ("The oracle the expectations came
 * from").
 */
class CursorAddressingTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  private fun styleAt(fixture: EmulatorFixture, row: Int, column: Int): CellStyle =
    fixture.emulator.screen.grid.styleAt(row, column)

  /** `CSI Pn C` moves forward and stops at the right margin. */
  @Test
  fun cursorForwardStopsAtTheRightMargin() {
    terminal(6, 2).enterString("A$ESC[CB$ESC[2CC").assertLinesAre("A B  C", "      ")
    terminal(6, 2).enterString("A$ESC[44CB").assertLinesAre("A    B", "      ")
  }

  /** `CSI Pn D` moves back and stops at the left margin. */
  @Test
  fun cursorBackStopsAtTheLeftMargin() {
    terminal(3, 2).enterString("A$ESC[DB").assertLinesAre("B  ", "   ")
    terminal(3, 2).enterString("AB$ESC[2DC").assertLinesAre("CB ", "   ")
    terminal(3, 2).enterString("AB$ESC[44DC").assertLinesAre("CB ", "   ")
  }

  @Test
  fun cursorUpStopsAtTheTopRow() {
    terminal(3, 3).enterString("ABCDEFG$ESC[AH").assertLinesAre("ABC", "DHF", "G  ")
    terminal(3, 3).enterString("ABCDEFG$ESC[2AH").assertLinesAre("AHC", "DEF", "G  ")
    terminal(3, 3).enterString("ABCDEFG$ESC[44AH").assertLinesAre("AHC", "DEF", "G  ")
  }

  @Test
  fun cursorDownStopsAtTheBottomRow() {
    terminal(3, 3).enterString("AB$ESC[BC").assertLinesAre("AB ", "  C", "   ")
    terminal(3, 3).enterString("AB$ESC[2BC").assertLinesAre("AB ", "   ", "  C")
    terminal(3, 3).enterString("AB$ESC[44BC").assertLinesAre("AB ", "   ", "  C")
  }

  /** A motion reads a zero exactly as it reads an absent parameter: as one. */
  @Test
  fun anAbsentOrZeroParameterMovesOne() {
    terminal(3, 3).enterString("A\r\nB$ESC[0AX").assertLinesAre("AX ", "B  ", "   ")
    terminal(4, 2).enterString("$ESC[0CX").assertLinesAre(" X  ", "    ")
  }

  /** HPA, `CSI Pn` backtick: the column is one-based and clamps to the last one. */
  @Test
  fun horizontalPositionAbsoluteIsOneBasedAndClamps() {
    terminal(4, 4)
      .enterString("ABC$ESC[${HPA}X")
      .assertCursorAt(0, 1)
      .enterString("$ESC[2${HPA}Y")
      .assertCursorAt(0, 2)
      .assertLineIs(0, "XYC ")
    terminal(4, 4).enterString("\r\n$ESC[3${HPA}P$ESC[22${HPA}Q").assertLineIs(1, "  PQ")
  }

  /** CUP takes the row first, and HVP is the same sequence under another final byte. */
  @Test
  fun cursorPositionTakesTheRowBeforeTheColumn() {
    terminal(4, 4).enterString("$ESC[2;3HX").assertLinesAre("    ", "  X ", "    ", "    ")
    terminal(4, 4).enterString("$ESC[2;3fX").assertLinesAre("    ", "  X ", "    ", "    ")
  }

  @Test
  fun cursorPositionWithNoParametersHomesTheCursor() {
    terminal(4, 2).enterString("abcd$ESC[HX").assertLinesAre("Xbcd", "    ")
  }

  @Test
  fun cursorPositionClampsToTheScreen() {
    terminal(4, 3).enterString("$ESC[99;99HX").assertLinesAre("    ", "    ", "   X")
  }

  @Test
  fun cursorHorizontalAbsoluteIsOneBasedAndClamps() {
    terminal(4, 2).enterString("$ESC[99GX").assertLinesAre("   X", "    ")
  }

  /** CNL and CPL move whole lines and return to the first column, unlike CUD and CUU. */
  @Test
  fun cursorNextAndPreviousLineReturnToTheFirstColumn() {
    terminal(4, 3).enterString("AB$ESC[EX").assertLinesAre("AB  ", "X   ", "    ")
    terminal(4, 3).enterString("A\r\nB\r\nC$ESC[2FX").assertLinesAre("X   ", "B   ", "C   ")
    terminal(4, 3).enterString("A$ESC[99EX").assertLinesAre("A   ", "    ", "X   ")
    terminal(4, 3).enterString("\r\n\r\nA$ESC[99FX").assertLinesAre("X   ", "    ", "A   ")
  }

  @Test
  fun linePositionAbsoluteIsOneBasedAndClamps() {
    terminal(4, 4).enterString("$ESC[3dX").assertLineIs(2, "X   ")
    terminal(4, 3).enterString("$ESC[99dX").assertLineIs(2, "X   ")
  }

  /** VPR moves down and keeps the column; HPR is CUF under another final byte. */
  @Test
  fun theRelativePositionsKeepTheOtherAxis() {
    terminal(4, 4).enterString("A$ESC[2eX").assertLinesAre("A   ", "    ", " X  ", "    ")
    terminal(6, 2).enterString("A$ESC[2aX").assertLinesAre("A  X  ", "      ")
  }

  /** CHT lands on the nth stop forward, and runs out at the last column. */
  @Test
  fun cursorForwardTabulationLandsOnTheNthStop() {
    terminal(20, 2).enterString("$ESC[2IX").assertLineIs(0, "                X   ")
    terminal(20, 2).enterString("$ESC[3g$ESC[5IX").assertLineIs(0, "                   X")
  }

  /** CBT lands on the nth stop back, and runs out at the left margin. */
  @Test
  fun cursorBackwardTabulationLandsOnTheNthStopBack() {
    terminal(20, 2).enterString("$ESC[20G$ESC[2ZX").assertLineIs(0, "        X           ")
    terminal(20, 2).enterString("$ESC[2ZX").assertLineIs(0, "X                   ")
  }

  /** `CSI 0 g` clears the stop under the cursor; `CSI 3 g` clears every one. */
  @Test
  fun theTabClearsRemoveOneStopAndThenAllOfThem() {
    terminal(20, 2).enterString("$ESC[9G${ESC}H$ESC[0g\r\tX").assertLineIs(0, "                X   ")
    terminal(20, 2).enterString("$ESC[3g\r\tX").assertLineIs(0, "                   X")
  }

  /** `CursorAndScreenTest.testCursorSaveRestoreLocation`, the ANSI.SYS half. */
  @Test
  fun theAnsiSaveAndRestorePutTheCursorBack() {
    terminal(4, 2).enterString("t$ESC[sest\r\nme$ESC[ury ").assertLinesAre("try ", "me  ")
  }

  /** `CursorAndScreenTest.testCursorSaveRestoreTextStyle`, the ANSI.SYS half. */
  @Test
  fun theAnsiRestorePutsBackTheStyleThatWasSavedWithIt() {
    val fixture = terminal(4, 2).enterString("$ESC[31;42;4m..$ESC[s$ESC[36;47;24m$ESC[u..")
    val style = styleAt(fixture, 0, 3)
    assertEquals(1, style.foreColor)
    assertEquals(2, style.backColor)
    assertEquals(CellStyle.UNDERLINE, style.effect)
  }

  /** `UnicodeInputTest.testWideCharacterDeletion`: CUB is how the reference reaches every half-cell. */
  @Test
  fun movingBackOntoAWideCharacterReplacesTheHalfItLeaves() {
    terminal(3, 2).enterString("枝$ESC[Da").assertLinesAre(" a ", "   ")
    terminal(3, 2).enterString("枝$ESC[2Da").assertLinesAre("a  ", "   ")
    terminal(3, 2).enterString("枝$ESC[2D枝").assertLinesAre("枝 ", "   ")
    terminal(3, 2).enterString("枝$ESC[1D枝").assertLinesAre(" 枝", "   ")
    terminal(5, 2).enterString(" 枝 $ESC[Da").assertLinesAre(" 枝a ", "     ")
    terminal(5, 2).enterString("a $ESC[D$COMBINING_CIRCUMFLEX").assertLinesAre("a\u0302    ", "     ")
  }

  /** The tail of the same case, which runs on one terminal rather than a fresh one each time. */
  @Test
  fun aCombiningMarkFollowsTheWideCharacterTheCursorBacksOnto() {
    terminal(5, 2)
      .enterString("枝 $ESC[D$COMBINING_CIRCUMFLEX")
      .assertLinesAre("枝\u0302   ", "     ")
      .enterString("Z")
      .assertLinesAre("枝\u0302Z  ", "     ")
      .enterString("$ESC[D ")
      .assertLinesAre("枝\u0302   ", "     ")
      .enterString("$ESC[2DU")
      .assertLinesAre(" U   ", "     ")
  }

  /** `UnicodeInputTest.testWideCharOverwriting`. */
  @Test
  fun aWideCharacterOverwritesTheTwoNarrowOnesItLandsOn() {
    terminal(3, 2).enterString("abc$ESC[3D枝").assertLinesAre("枝c", "   ")
  }

  private companion object {
    const val ESC = "\u001B"

    /** HPA's final byte, spelled out because a backtick inside a Kotlin string is easy to misread. */
    const val HPA = "`"

    const val COMBINING_CIRCUMFLEX = "\u0302"
  }
}
