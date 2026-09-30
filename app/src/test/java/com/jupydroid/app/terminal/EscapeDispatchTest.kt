package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The `ESC` dispatch: the sequences introduced by an escape alone, with no control-sequence
 * introducer after it.
 *
 * `TerminalTest.testLineDrawing` and the DEC halves of `CursorAndScreenTest`'s
 * `testCursorSaveRestoreLocation` / `testCursorSaveRestoreTextStyle` are transcribed here
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]); the reference covers the other eleven
 * bytes of `doEsc` only through whole-screen scenarios, so those cases are written here and their
 * **expectations were read off the reference itself** rather than off its source, by running it as
 * the differential's oracle.
 *
 * The alternate-screen halves of the two save/restore tests need `CSI ? 1049 h`, so they live with
 * the alternate screen's own tests.
 */
class EscapeDispatchTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  private fun styleAt(fixture: EmulatorFixture, row: Int, column: Int): CellStyle =
    fixture.emulator.screen.grid.styleAt(row, column)

  /** `TerminalTest.testLineDrawing`, first line: `ESC ) 0` designates G1, SO and SI invoke. */
  @Test
  fun designatingG1DrawsLinesOnlyWhileG1IsInvoked() {
    terminal(4, 2).enterString("q$ESC)0q${SO}q${SI}q").assertLinesAre("qq─q", "    ")
  }

  /** `ESC ( C` designates G0, which is what an application leaves invoked by default. */
  @Test
  fun designatingG0DrawsLinesUntilItIsDesignatedBack() {
    terminal(4, 2).enterString("q$ESC(0q$ESC(Bq").assertLinesAre("q─q ", "    ")
  }

  /** `TerminalTest.testLineDrawing`, second line: DECSC saves G0, G1 and the invoked set. */
  @Test
  fun savingTheCursorSavesWhichGraphicSetIsInvoked() {
    terminal(4, 2).enterString("$ESC)0${SO}qqq${ESC}7$SI${ESC}8q").assertLinesAre("────", "    ")
  }

  /** `CursorAndScreenTest.testCursorSaveRestoreLocation`, the DECSC/DECRC half. */
  @Test
  fun restoringTheCursorPutsItBackWhereItWasSaved() {
    terminal(4, 2).enterString("t${ESC}7est\r\nme${ESC}8ry ").assertLinesAre("try ", "me  ")
  }

  /** `CursorAndScreenTest.testCursorSaveRestoreTextStyle`, the DECSC/DECRC half. */
  @Test
  fun restoringTheCursorPutsBackTheStyleThatWasSavedWithIt() {
    val fixture = terminal(4, 2).enterString("$ESC[31;42;4m..${ESC}7$ESC[36;47;24m${ESC}8..")
    val style = styleAt(fixture, 0, 3)
    assertEquals(1, style.foreColor)
    assertEquals(2, style.backColor)
    assertEquals(CellStyle.UNDERLINE, style.effect)
  }

  /** IND moves down a row and leaves the column alone, where NEL also returns to the left. */
  @Test
  fun indexMovesDownOneRowWithoutChangingTheColumn() {
    terminal(3, 3).enterString("AB${ESC}DC").assertLinesAre("AB ", "  C", "   ")
  }

  @Test
  fun indexAtTheBottomScrollsTheScreenUp() {
    terminal(3, 2).enterString("A\r\nB${ESC}DC").assertLinesAre("B  ", " C ")
  }

  @Test
  fun nextLineMovesDownOneRowAndBackToTheFirstColumn() {
    terminal(3, 3).enterString("AB${ESC}EC").assertLinesAre("AB ", "C  ", "   ")
  }

  @Test
  fun reverseIndexMovesUpOneRowWithoutChangingTheColumn() {
    terminal(3, 3).enterString("A\r\nB${ESC}MC").assertLinesAre("AC ", "B  ", "   ")
  }

  /** At the top margin there is nowhere to move to, so the screen moves down instead. */
  @Test
  fun reverseIndexAtTheTopScrollsTheScreenDown() {
    terminal(3, 3).enterString("AB${ESC}MC").assertLinesAre("  C", "AB ", "   ")
  }

  /** DECALN, the screen alignment test: every cell an `E`, and the cursor left where it stood. */
  @Test
  fun theAlignmentTestFillsTheScreenWithLetterE() {
    terminal(3, 2).enterString("$ESC#8").assertLinesAre("EEE", "EEE").assertCursorAt(0, 0)
  }

  /** A pound sequence the reference does not implement ends taking only its final byte. */
  @Test
  fun aPoundSequenceThatIsNotTheAlignmentTestIsSwallowed() {
    terminal(3, 2).enterString("$ESC#7X").assertLinesAre("X  ", "   ")
  }

  /**
   * RIS: the screen, the scrollback, the cursor, the style and the graphic sets all go back.
   *
   * **A declared divergence in the last of those.** The reference's `reset()` assigns `mForeColor`
   * and `mBackColor` and never `mEffect`, so upstream `ESC [ 1;4m A ESC c B` leaves `B` bold and
   * underlined - measured, not read off the source. RIS is a *hard* reset in xterm's `ctlseqs`,
   * and
   */
  @Test
  fun resetToInitialStateClearsTheScreenTheTranscriptAndTheStyle() {
    val fixture = terminal(3, 2).enterString("a\r\nb\r\nc$ESC[1;4;31m$ESC)0$SO${ESC}cq")
    fixture.assertLinesAre("q  ", "   ").assertCursorAt(0, 1)
    assertEquals("q", fixture.emulator.screen.grid.transcriptText)
    assertEquals(CellStyle.COLOR_INDEX_FOREGROUND, styleAt(fixture, 0, 0).foreColor)
    assertEquals(0, styleAt(fixture, 0, 0).effect)
  }

  /** And the cursor DECSC saved before it, which is why `reset()` clears the saved state. */
  @Test
  fun resetToInitialStateForgetsTheSavedCursor() {
    terminal(4, 2).enterString("ab${ESC}7${ESC}c${ESC}8X").assertLinesAre("X   ", "    ")
  }

  /**
   * **A declared divergence.** The reference's `doEsc` calls `setCursorRowCol(0, mBottomMargin - 1)`
   * for a byte its own comment calls "cursor to lower-left corner" - row and column transposed, so
   * on a 4x3 screen it lands at row 0, column 2. xterm's `ctlseqs` is unambiguous
   * ("Cursor to lower left corner of screen"), and
   */
  @Test
  fun lowerLeftPutsTheCursorAtTheStartOfTheLastRow() {
    terminal(4, 3).enterString("${ESC}FX").assertLinesAre("    ", "    ", "X   ")
  }

  /** HTS sets a stop where the cursor stands, and a tab afterwards lands on it. */
  @Test
  fun tabSetAddsAStopTheNextTabLandsOn() {
    terminal(10, 2).enterString("abc${ESC}H\r\tX").assertLinesAre("abcX      ", "          ")
  }

  @Test
  fun backIndexMovesLeftWhenThereIsRoom() {
    terminal(3, 2).enterString("ab${ESC}6X").assertLinesAre("aX ", "   ")
  }

  /** At the left margin there is no room, so the screen shifts right and a blank column appears. */
  @Test
  fun backIndexAtTheLeftMarginInsertsABlankColumn() {
    terminal(3, 2).enterString("abc\r${ESC}6X").assertLinesAre("Xab", "   ")
  }

  @Test
  fun forwardIndexMovesRightWhenThereIsRoom() {
    terminal(3, 2).enterString("a${ESC}9X").assertLinesAre("a X", "   ")
  }

  /**
   * At the right margin the screen shifts left instead. The pending wrap survives - the reference
   * assigns the column rather than going through its setter - so the `X` after it wraps.
   */
  @Test
  fun forwardIndexAtTheRightMarginDropsTheFirstColumn() {
    terminal(3, 2).enterString("abc${ESC}9X").assertLinesAre("bc ", "X  ")
  }

  /**
   * SS2 and SS3 affect one character in ISO 2022 and neither reference emulator implements that;
   * both are consumed and change nothing. `ESC 0` is consumed too - the reference writes `'0'`
   * where it means `'O'`, and since both arms do nothing the two are indistinguishable.
   */
  @Test
  fun theSingleShiftsAreConsumedAndChangeNothing() {
    terminal(6, 2).enterString("${ESC}Na${ESC}Ob${ESC}0c").assertLinesAre("abc   ", "      ")
  }

  private companion object {
    const val ESC = "\u001B"

    /** Shift Out: invoke G1 into GL. */
    const val SO = "\u000E"

    /** Shift In: invoke G0 into GL. */
    const val SI = "\u000F"
  }
}
