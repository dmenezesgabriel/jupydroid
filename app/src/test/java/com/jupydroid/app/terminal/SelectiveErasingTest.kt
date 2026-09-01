package com.jupydroid.app.terminal

import org.junit.Test

/**
 * The protected attribute and the two erases that respect it: DECSCA (`CSI Pn " q`) marks what
 * follows as protected, and DECSED (`CSI ? Pn J`) and DECSEL (`CSI ? Pn K`) erase everything that
 * is not.
 *
 * Transcribed from `RectangularAreasTest.testSelectiveEraseInDisplay` and `testSelectiveEraseInLine`
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]). That file's third selective test,
 * `testSelectiveEraseInRectangle`, is DECSERA under the `$` intermediate and goes with P4f-5c.
 *
 * **DECSCA is a [CellStyle] bit, not a screen mode**: `CellStyle.PROTECTED` has existed since
 * P4e-2, which transcribed the whole effect set, and this is the step that gives it a writer and
 * two readers.
 */
class SelectiveErasingTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  /** `testSelectiveEraseInDisplay`: `CSI ? 2 J` spares whatever DECSCA protected. */
  @Test
  fun selectiveEraseInDisplaySparesProtectedCells() {
    terminal(3, 3).enterString("ABCDEFGHI$ESC[?2J").assertLinesAre("   ", "   ", "   ")
    terminal(3, 3).enterString("ABC$ESC[1\"qDE$ESC[0\"qFGHI$ESC[?2J").assertLinesAre("   ", "DE ", "   ")
    terminal(3, 3).enterString("$ESC[1\"qABCDE$ESC[0\"qFGHI$ESC[?2J").assertLinesAre("ABC", "DE ", "   ")
  }

  /** `testSelectiveEraseInLine`, all six: the three parameters, the cursor's half-line, and DECSCA. */
  @Test
  fun selectiveEraseInLineSparesProtectedCells() {
    terminal(3, 3).enterString("ABCDEFGHI$ESC[?2K").assertLinesAre("ABC", "DEF", "   ")
    terminal(3, 3).enterString("ABCDE$ESC[?0KFGHI").assertLinesAre("ABC", "DEF", "GHI")
    terminal(3, 3).enterString("ABCDE$ESC[?1KFGHI").assertLinesAre("ABC", "  F", "GHI")
    terminal(3, 3).enterString("ABCDE$ESC[?2KFGHI").assertLinesAre("ABC", "  F", "GHI")
    terminal(3, 3).enterString("ABCDEFGHI$ESC[2;2H$ESC[?0K").assertLinesAre("ABC", "D  ", "GHI")
    terminal(3, 3).enterString("ABC$ESC[1\"qD$ESC[0\"qE$ESC[?2KFGHI").assertLinesAre("ABC", "D F", "GHI")
  }

  /**
   * DECSCA's parameters: `1` protects, `0` and `2` both unprotect, and anything else is an unknown
   * sequence that changes nothing. Expectations read off `RefScreen` - upstream tests only `0` and
   * `1`, so `2`'s equivalence to `0` and the unknown arm are measured rather than assumed.
   */
  @Test
  fun theProtectionAttributeTakesThreeParametersAndIgnoresTheRest() {
    terminal(3, 2).enterString("$ESC[1\"qAB$ESC[2\"qC$ESC[?2J").assertLinesAre("AB ", "   ")
    terminal(3, 2).enterString("$ESC[1\"qAB$ESC[0\"qC$ESC[?2J").assertLinesAre("AB ", "   ")
    terminal(3, 2).enterString("$ESC[1\"qAB$ESC[9\"qC$ESC[?2J").assertLinesAre("ABC", "   ")
    terminal(3, 2).enterString("$ESC[\"qAB$ESC[?2J").assertLinesAre("   ", "   ")
  }

  /**
   * **A selective erase clears the pending wrap whatever its parameter**, where ED and EL clear it
   * only for a parameter they understood (P4f-3c). The reference assigns `mAboutToAutoWrap = false`
   * *before* its switch rather than after, so the asymmetry P4f-3c transcribed does not apply here.
   * Expectations off `RefScreen`.
   */
  @Test
  fun aSelectiveEraseClearsThePendingWrapEvenForAParameterItDoesNotUnderstand() {
    terminal(5, 2).enterString("abcde$ESC[?9K-").assertLinesAre("abcd-", "     ")
    terminal(5, 2).enterString("abcde$ESC[?9J-").assertLinesAre("abcd-", "     ")
  }

  private companion object {
    const val ESC = "\u001B"
  }
}
