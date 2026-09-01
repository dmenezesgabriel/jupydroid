package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The escape state machine end to end: what reaches the screen, what is swallowed, and what ends a
 * sequence early.
 *
 * `ControlSequenceIntroducerTest.testCsiSGRParameters` is transcribed here (termux-app `3df69d1d`,
 * Apache 2.0 - see [TerminalGrid]); the rest are cases neither reference suite covers, because
 * upstream the machine is only ever driven through sequences it fully implements.
 *
 * **Three of these change at P4f-4 and P4f-5**, and say so where they stand: the modes, the private
 * parameter bytes and the intermediate bytes are each swallowed until the sub-step that implements
 * them arrives.
 */
class EscapeSequenceTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  private fun foregroundAt(fixture: EmulatorFixture, row: Int, column: Int): Int =
    fixture.emulator.screen.grid.styleAt(row, column).foreColor

  private fun backgroundAt(fixture: EmulatorFixture, row: Int, column: Int): Int =
    fixture.emulator.screen.grid.styleAt(row, column).backColor

  @Test
  fun aGraphicRenditionPaintsWhatComesAfterItAndNotWhatCameBefore() {
    val fixture = terminal(5, 2).enterString("a$ESC[31mb")
    fixture.assertLineIs(0, "ab   ")
    assertEquals(CellStyle.COLOR_INDEX_FOREGROUND, foregroundAt(fixture, 0, 0))
    assertEquals(1, foregroundAt(fixture, 0, 1))
  }

  /** `ControlSequenceIntroducerTest.testCsiSGRParameters`: nineteen parameters, all consumed. */
  @Test
  fun aLongGraphicRenditionIsConsumedWhole() {
    terminal(3, 2)
      .enterString("$ESC[0;38;2;255;255;255;48;2;0;0;0;1;2;3;4;5;7;8;9mabc")
      .assertLinesAre("abc", "   ")
  }

  @Test
  fun aSecondEscapeAbandonsTheSequenceInProgress() {
    val fixture = terminal(5, 2).enterString("$ESC[31$ESC[32mx")
    fixture.assertLineIs(0, "x    ")
    assertEquals(2, foregroundAt(fixture, 0, 0))
  }

  /**
   * CAN and SUB abandon a sequence where it stands and leave a `DEL` behind, which the reference
   * writes straight to the screen. `DEL` has no width, so it lands on the column before the cursor
   * as a combining mark does - which is why this writes an `a` first, to have one.
   */
  @Test
  fun cancelAndSubstituteAbandonTheSequenceAndLeaveADeleteBehind() {
    for (abandon in listOf(CANCEL, SUBSTITUTE)) {
      terminal(5, 2)
        .enterString("a$ESC[31${abandon}bc")
        .assertLineStartsWith(0, 'a'.code, DELETE, 'b'.code, 'c'.code)
    }
  }

  @Test
  fun cancelOutsideASequenceLeavesNothingBehind() {
    terminal(5, 2).enterString("a${CANCEL}bc").assertLineIs(0, "abc  ")
  }

  /**
   * The case this transport actually has: bytes arrive in WebSocket frames, so a sequence straddles
   * a frame boundary whenever the shell writes one near an edge. Neither reference suite covers it.
   */
  @Test
  fun aSequenceSplitAcrossTwoAppendsIsStillOneSequence() {
    val fixture = terminal(5, 2)
    fixture.append("$ESC[3".toByteArray())
    fixture.append("1mx".toByteArray())
    fixture.assertLineIs(0, "x    ")
    assertEquals(1, foregroundAt(fixture, 0, 0))
  }

  /**
   * A C0 control inside a sequence is acted on as a control and the sequence goes *on*: the
   * reference dispatches the named C0s before it looks at the escape state at all.
   */
  @Test
  fun aCarriageReturnInsideASequenceMovesTheCursorAndTheSequenceSurvives() {
    val fixture = terminal(5, 2).enterString("ab$ESC[3\r1mx")
    fixture.assertLineIs(0, "xb   ")
    assertEquals(1, foregroundAt(fixture, 0, 0))
  }

  /**
   * A byte that is no parameter, intermediate or final byte ends the sequence where it stands, as
   * `parseArg`'s fall-through does upstream - so what follows is drawn. `SOH` is one: a C0 control
   * the ground state itself drops.
   */
  @Test
  fun aByteThatIsNoParameterEndsTheSequence() {
    terminal(5, 2).enterString("$ESC[3\u0001mx").assertLineIs(0, "mx   ")
  }

  /**
   * A final byte the reference implements and this step does not is **swallowed with its
   * parameters** rather than printed - the frame, not a stub. The two cases move on as the
   * sub-steps take them: this held `CSI 2 J` and `CSI 10;3 H` until P4f-3b and P4f-3c implemented
   * both, and now holds IRM (**P4f-4**) and DECSTBM (**P4f-5**).
   */
  @Test
  fun aFinalByteThisStepDoesNotImplementIsSwallowedWhole() {
    terminal(5, 2).enterString("$ESC[4hx").assertLineIs(0, "x    ")
    terminal(5, 2).enterString("$ESC[2;3rx").assertLineIs(0, "x    ")
  }

  /** A private parameter byte (`?`, `>`, `<`, `=`) is swallowed to its final byte. P4f-4 implements them. */
  @Test
  fun aPrivateParameterByteIsSwallowedToItsFinalByte() {
    terminal(5, 2).enterString("$ESC[?7lx").assertLineIs(0, "x    ")
    terminal(5, 2).enterString("$ESC[>0cx").assertLineIs(0, "x    ")
  }

  /** So is an intermediate byte the reference gives a state of its own. P4f-3 and P4f-5 implement them. */
  @Test
  fun anIntermediateByteIsSwallowedToItsFinalByte() {
    terminal(5, 2).enterString("$ESC[!px").assertLineIs(0, "x    ")
    terminal(5, 2).enterString("$ESC[1 qx").assertLineIs(0, "x    ")
  }

  /**
   * An introducer no arm of the `ESC` dispatch claims ends the sequence taking only itself, which
   * is the reference's `unknownSequence`. `%` is one for good: it selects a character set upstream
   * declares a state for (`ESC_PERCENT`) and then routes nothing to, so no sub-step reaches it.
   */
  @Test
  fun anEscapeIntroducerNoArmClaimsEndsTheSequence() {
    terminal(5, 2).enterString("$ESC%x").assertLineIs(0, "x    ")
  }

  /**
   * `TerminalTest.testTab`'s second line: a tab moves over cells without painting them, so the
   * background set before it reaches only the characters actually written. The reference asserts
   * the text alone; the background is what the case is about, so it is asserted too.
   */
  @Test
  fun aTabDoesNotPaintTheCellsItPassesOver() {
    val fixture = terminal(11, 2).enterString("01234567890$ESC[44m\r\tXX")
    fixture.assertLinesAre("01234567XX0", "           ")
    assertEquals(CellStyle.COLOR_INDEX_BACKGROUND, backgroundAt(fixture, 0, 0))
    assertEquals(CellStyle.COLOR_INDEX_BACKGROUND, backgroundAt(fixture, 0, 7))
    assertEquals(4, backgroundAt(fixture, 0, 8))
  }

  @Test
  fun aResetForgetsAPartialSequence() {
    val fixture = terminal(5, 2)
    fixture.append("$ESC[31".toByteArray())
    fixture.emulator.reset()
    fixture.enterString("mx").assertLineIs(0, "mx   ")
  }

  private companion object {
    const val ESC = "\u001B"
    const val CANCEL = "\u0018"
    const val SUBSTITUTE = "\u001A"
    const val DELETE = 127
  }
}
