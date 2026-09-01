package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numeric parameters of an escape sequence, from `TerminalEmulator.parseArg` / `getArg` /
 * `startEscapeSequence` (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]).
 *
 * Upstream these are private fields reachable only by driving a whole terminal, so the cases below
 * are written against the behaviours the reference's own SGR tests depend on -
 * `TerminalTest.testSelectGraphics` for the separators and the defaults,
 * `ControlSequenceIntroducerTest.testManyParameters` for the cap.
 */
class EscapeParametersTest {
  private val parameters = EscapeParameters()

  private fun enter(text: String): EscapeParameters {
    parameters.startSequence()
    text.forEach { assertTrue("'$it' was refused", parameters.accept(it.code)) }
    return parameters
  }

  @Test
  fun anAbsentParameterIsItsDefault() {
    assertEquals(7, enter("").at(0, 7, treatZeroAsDefault = true))
  }

  @Test
  fun anEmptySequenceStillHasOneParameter() {
    // What makes `ESC [ m` a reset rather than nothing: SGR walks 0 until count.
    assertEquals(1, enter("").count)
  }

  @Test
  fun digitsAccumulateIntoOneParameter() {
    assertEquals(123, enter("123").at(0, -1, treatZeroAsDefault = false))
  }

  @Test
  fun aSemicolonStartsTheNextParameter() {
    val entered = enter("31;41")
    assertEquals(2, entered.count)
    assertEquals(31, entered.at(0, -1, treatZeroAsDefault = false))
    assertEquals(41, entered.at(1, -1, treatZeroAsDefault = false))
  }

  @Test
  fun aColonMarksTheParameterItOpensAsASubParameter() {
    val entered = enter("38:5:119")
    assertFalse(entered.isSubParameter(0))
    assertTrue(entered.isSubParameter(1))
    assertTrue(entered.isSubParameter(2))
  }

  @Test
  fun aSemicolonMarksNoSubParameter() {
    assertFalse(enter("38;5").isSubParameter(1))
  }

  @Test
  fun aValueIsClampedAtFourDigits() {
    assertEquals(9999, enter("123456").at(0, -1, treatZeroAsDefault = false))
  }

  @Test
  fun zeroIsTheDefaultOnlyWhenTheCallerAsksForThat() {
    assertEquals(5, enter("0").at(0, 5, treatZeroAsDefault = true))
    assertEquals(0, enter("0").at(0, 5, treatZeroAsDefault = false))
  }

  /**
   * `ControlSequenceIntroducerTest.testManyParameters`: past the thirty-second parameter the
   * separator is dropped and the digits after it land on the parameter already open, rather than
   * the sequence being abandoned.
   */
  @Test
  fun aSeparatorPastTheLastParameterIsDroppedAndTheSequenceGoesOn() {
    val entered = enter("0;".repeat(31) + "4;7")
    assertEquals(EscapeParameters.MAX_PARAMETERS, entered.count)
    assertEquals(47, entered.at(EscapeParameters.MAX_PARAMETERS - 1, -1, treatZeroAsDefault = false))
  }

  @Test
  fun aByteThatIsNotADigitOrASeparatorIsRefused() {
    parameters.startSequence()
    assertFalse(parameters.accept('m'.code))
    assertFalse(parameters.accept(0x1B))
  }

  @Test
  fun startingASequenceForgetsTheOneBefore() {
    enter("31:2")
    val entered = enter("7")
    assertEquals(1, entered.count)
    assertEquals(7, entered.at(0, -1, treatZeroAsDefault = false))
    assertFalse(entered.isSubParameter(1))
  }
}
