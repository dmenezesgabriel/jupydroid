package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The modes: `CSI ? Pm h`/`l` (DECSET/DECRST), the `CSI ? Pm s`/`r` that save and recall them, the
 * ANSI `h`/`l` modes, and `ESC =`/`ESC >`.
 *
 * All three `DecSetTest` methods are transcribed here now (termux-app `3df69d1d`, Apache 2.0 -
 * see [TerminalGrid]): `testWrapAroundMode` from the start, and `testEnableDisableCursor` /
 * `testBracketedPasteMode` since P4f-6d gave them the surfaces they were waiting on -
 * `TerminalEmulator.isCursorEnabled` and `TerminalEmulator.paste`.
 *
 * Everything upstream never exercises had its **expectations read off the reference itself**
 * (`evidence/p4-terminal/emulator-differential.md`, "The oracle the expectations came from").
 */
class ScreenModesTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  private fun cursorOf(fixture: EmulatorFixture): Pair<Int, Int> =
    fixture.emulator.screen.cursor.let { it.row to it.column }

  /** `DecSetTest.testWrapAroundMode`: DECAWM, and the last column that overwrites itself without it. */
  @Test
  fun wrapAroundModeOffOverwritesTheLastColumnAndOnResumesWrapping() {
    terminal(3, 3).enterString("abcd").assertLinesAre("abc", "d  ", "   ")
    terminal(3, 3).enterString("$ESC[?7labcd").assertLinesAre("abd", "   ", "   ")
    terminal(3, 3).enterString("$ESC[?7labcdefg").assertLinesAre("abg", "   ", "   ")
    terminal(3, 3).enterString("$ESC[?7labcd$ESC[?7hhij").assertLinesAre("abh", "ij ", "   ")
  }

  /** DECCOLM does not change the width here, but it keeps the side effects a reset depends on. */
  @Test
  fun columnModeClearsTheScreenAndHomesTheCursor() {
    val fixture = terminal(3, 3).enterString("abc\r\ndef$ESC[?3h")
    fixture.assertLinesAre("   ", "   ", "   ")
    assertEquals(0 to 0, cursorOf(fixture))
  }

  /** Setting DECOM homes the cursor; the clamp it feeds needs a margin and lands with DECSTBM. */
  @Test
  fun originModeHomesTheCursorWhenItIsSet() {
    val fixture = terminal(3, 3).enterString("abc$ESC[2;2H$ESC[?6h")
    fixture.assertLinesAre("abc", "   ", "   ")
    assertEquals(0 to 0, cursorOf(fixture))
  }

  /** IRM is an ANSI mode, `CSI 4 h`, not a private one - so it carries no `?`. */
  @Test
  fun insertModePushesWhatIsAlreadyThereToTheRight() {
    terminal(5, 2).enterString("abc$ESC[2G$ESC[4hX").assertLineIs(0, "aXbc ")
    terminal(5, 2).enterString("abc$ESC[2G$ESC[4h$ESC[4lX").assertLineIs(0, "aXc  ")
  }

  /**
   * **`CSI ? Pm s` marks the flag set rather than recording what it was**: upstream the arm is
   * `mSavedDecSetFlags |= internalBit`, with no read of the current value, so the recall that
   * follows always turns the mode *on*. Measured on the reference - all three orderings wrap.
   */
  @Test
  fun savingADecFlagMarksItSetRatherThanRecordingTheValueItHad() {
    terminal(3, 3).enterString("$ESC[?7l$ESC[?7s$ESC[?7rabcd").assertLinesAre("abc", "d  ", "   ")
    terminal(3, 3).enterString("$ESC[?7l$ESC[?7rabcd").assertLinesAre("abc", "d  ", "   ")
    terminal(3, 3).enterString("$ESC[?7s$ESC[?7l$ESC[?7rabcd").assertLinesAre("abc", "d  ", "   ")
  }

  /** A private mode nothing claims is swallowed with its parameters, never drawn. */
  @Test
  fun aPrivateModeNothingClaimsIsSwallowedRatherThanPrinted() {
    terminal(5, 2).enterString("$ESC[?9999hX").assertLineIs(0, "X    ")
    terminal(5, 2).enterString("$ESC[?9999lX").assertLineIs(0, "X    ")
  }

  /** `ESC =` and `ESC >` select the keypad; neither draws anything. */
  @Test
  fun theKeypadEscapesDrawNothing() {
    terminal(5, 2).enterString("$ESC=$ESC>X").assertLineIs(0, "X    ")
  }

  /**
   * DECSC and DECRC carry **two** of the DEC flags and no others - autowrap and origin mode -
   * because `restoreCursor` masks the saved flags down to those before putting them back.
   * **The differential found this**, the moment a mode could change: a `CSI u` after `CSI ? 7 l`
   * put wrapping back on in the reference and left it off here.
   */
  @Test
  fun savingTheCursorCarriesTheWrapAndOriginFlagsBackWithIt() {
    terminal(3, 3).enterString("${ESC}7$ESC[?7labcd").assertLinesAre("abd", "   ", "   ")
    terminal(3, 3).enterString("${ESC}7$ESC[?7l${ESC}8abcd").assertLinesAre("abc", "d  ", "   ")
    terminal(3, 3).enterString("$ESC[?7l${ESC}7$ESC[?7h${ESC}8abcd").assertLinesAre("abd", "   ", "   ")
    terminal(3, 3).enterString("$ESC[s$ESC[?7l$ESC[uabcd").assertLinesAre("abc", "d  ", "   ")
  }

  /** `testEnableDisableCursor`. DECSET 25, DECTCEM, controls the cursor's visibility. */
  @Test
  fun cursorVisibilityIsToggledByDecset25AndRestoredByReset() {
    val fixture = terminal(3, 3)
    assertTrue("Initially the cursor should be enabled", fixture.emulator.isCursorEnabled)
    fixture.enterString("$ESC[?25l")
    assertFalse(fixture.emulator.isCursorEnabled)
    fixture.enterString("$ESC[?25h")
    assertTrue(fixture.emulator.isCursorEnabled)

    fixture.enterString("$ESC[?25l")
    assertFalse(fixture.emulator.isCursorEnabled)
    fixture.emulator.reset()
    assertTrue("Resetting the terminal should enable the cursor", fixture.emulator.isCursorEnabled)

    fixture.enterString("$ESC[?25l")
    assertFalse(fixture.emulator.isCursorEnabled)
    fixture.enterString("${ESC}c") // RIS resetting should enable the cursor.
    assertTrue(fixture.emulator.isCursorEnabled)
  }

  /** What one `paste()` writes back, for the assertions below to read in one line each. */
  private fun assertPasteEcho(fixture: EmulatorFixture, text: String, expected: String) {
    fixture.emulator.paste(text)
    assertEquals(expected, fixture.replies.takeReply())
  }

  /** `testBracketedPasteMode`. DECSET 2004 brackets a paste; escapes and C1 controls are stripped. */
  @Test
  fun bracketedPasteModeWrapsAPasteAndStripsEscapesAndC1Controls() {
    val fixture = terminal(3, 3)
    assertPasteEcho(fixture, "a", "a")

    fixture.enterString("$ESC[?2004h")
    assertPasteEcho(fixture, "a", "$ESC[200~a$ESC[201~")

    fixture.enterString("$ESC[?2004l")
    assertPasteEcho(fixture, "a", "a")

    fixture.enterString("$ESC[?2004h")
    assertPasteEcho(fixture, "a", "$ESC[200~a$ESC[201~")
    assertPasteEcho(fixture, "${ESC}ab${ESC}cd$ESC", "$ESC[200~abcd$ESC[201~")
    assertPasteEcho(fixture, "\u0081ab\u0081cd\u009F", "$ESC[200~abcd$ESC[201~")

    fixture.emulator.reset()
    assertPasteEcho(fixture, "a", "a")
  }

  private companion object {
    const val ESC = "\u001B"
  }
}
