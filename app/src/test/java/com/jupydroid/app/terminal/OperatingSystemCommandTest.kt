package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `ESC ]`, the Operating System Command: the string state with both its terminators, and the window
 * title the shell sets through it.
 *
 * `OperatingSystemControlTest.testSetTitle` and `testTitleStack` are transcribed whole (termux-app
 * `3df69d1d`, Apache 2.0 - see [TerminalGrid]). The rest had their expectations **measured off
 * `RefScreen`** rather than read off the reference's source, because the string state is where its
 * control-character dispatch and its escape state machine overlap and the source reads either way:
 * a `BEL` ends the string in one state and rings in the next, and an `ESC` is collected in one and
 * would restart a sequence in the other.
 */
class OperatingSystemCommandTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  /**
   * `testSetTitle`'s sets. **`0`, `1` and `2` reach one arm**: the reference draws no distinction
   * between an icon name and a window title. Its stack half is [theTitleStackPushesAndPops]'s and
   * its notice half is [everyTitleChangeIsReportedOnceWithBothSides]'.
   */
  @Test
  fun theTitleIsSetByOscZeroOneAndTwo() {
    val fixture = terminal(10, 10)
    fixture.enterString("$ESC]0;Hello, world$BEL")
    assertEquals("Hello, world", fixture.emulator.title.current)
    fixture.enterString("$ESC]0;Goodbye, ñ world$BEL")
    assertEquals("Goodbye, ñ world", fixture.emulator.title.current)
    // 2 should work as well (0 sets both title and icon).
    fixture.enterString("$ESC]2;Updated$BEL")
    assertEquals("Updated", fixture.emulator.title.current)
    fixture.enterString("$ESC]1;Icon$BEL")
    assertEquals("Icon", fixture.emulator.title.current)
  }

  /**
   * `testSetTitle`'s other half: the notice carries **both** sides of the change, and fires once
   * per change - which is what `MockTerminalOutput.titleChanges` records upstream.
   */
  @Test
  fun everyTitleChangeIsReportedOnceWithBothSides() {
    val fixture = terminal(10, 10)
    fixture.enterString("$ESC]0;Hello$BEL$ESC]0;Goodbye$BEL$ESC]2;Updated$BEL$ESC]2;Updated$BEL")
    assertEquals(
      listOf(null to "Hello", "Hello" to "Goodbye", "Goodbye" to "Updated"),
      fixture.notices.titleChanges,
    )
  }

  /** `testTitleStack`: `CSI 22 t` pushes, `CSI 23 t` pops, and a pop with nothing on it does nothing. */
  @Test
  fun theTitleStackPushesAndPops() {
    val fixture = terminal(10, 10)
    fixture.enterString("$ESC]0;InitialTitle$BEL")
    assertEquals("InitialTitle", fixture.emulator.title.current)
    fixture.enterString("$ESC[22t")
    assertEquals("InitialTitle", fixture.emulator.title.current)
    fixture.enterString("$ESC]0;UpdatedTitle$BEL")
    assertEquals("UpdatedTitle", fixture.emulator.title.current)
    fixture.enterString("$ESC[23t")
    assertEquals("InitialTitle", fixture.emulator.title.current)
    fixture.enterString("$ESC[23t$ESC[23t$ESC[23t")
    assertEquals("InitialTitle", fixture.emulator.title.current)
  }

  /**
   * `testSetTitle`'s stack half: a parameter is accepted and ignored, and a pop **to the title
   * already showing reports nothing** - which is what makes the notice a change rather than an
   * event.
   */
  @Test
  fun aPopToTheTitleAlreadyShowingReportsNothing() {
    val fixture = terminal(10, 10)
    fixture.enterString("$ESC]0;A$BEL$ESC[22;0t$ESC]0;B$BEL$ESC[23;0t")
    assertEquals("A", fixture.emulator.title.current)
    val afterOnePop = fixture.notices.titleChanges.size
    fixture.enterString("$ESC[22;0t$ESC[22;0t$ESC[23;0t$ESC[23;0t")
    assertEquals("A", fixture.emulator.title.current)
    assertEquals(afterOnePop, fixture.notices.titleChanges.size)
  }

  /**
   * The stack holds twenty and **drops the oldest**, not the newest: twenty-one pushes then
   * twenty-one pops lands on the *second* title pushed. Read off `RefScreen`; upstream never tests
   * the cap.
   */
  @Test
  fun theTitleStackHoldsTwentyAndDropsTheOldest() {
    val fixture = terminal(10, 10)
    fixture.enterString("$ESC]0;T0$BEL")
    for (index in 1..21) fixture.enterString("$ESC[22t$ESC]0;T$index$BEL")
    repeat(21) { fixture.enterString("$ESC[23t") }
    assertEquals("T1", fixture.emulator.title.current)
  }

  /**
   * A title pushed before any was set is `null`, and popping it back **reports the change**. Off
   * `RefScreen`: `titles = [null->A][A->null]`.
   */
  @Test
  fun aNullTitleGoesOnTheStackAndComesBackOffIt() {
    val fixture = terminal(10, 10)
    fixture.enterString("$ESC[22t$ESC]0;A$BEL$ESC[23t")
    assertNull(fixture.emulator.title.current)
    assertEquals(listOf(null to "A", "A" to null), fixture.notices.titleChanges)
  }

  /** Both terminators end the string: `BEL`, and `ESC \` - the string terminator. */
  @Test
  fun bothTerminatorsEndTheString() {
    assertEquals("Updated", terminal(10, 2).enterString("$ESC]2;Updated$ST").emulator.title.current)
    assertEquals("Updated", terminal(10, 2).enterString("$ESC]2;Updated$BEL").emulator.title.current)
  }

  /**
   * An `ESC` inside the body that is **not** followed by `\` is no error: the `ESC` and the byte
   * after it are both collected and the string goes on. Measured - `title = a<ESC>Xb`.
   */
  @Test
  fun anEscapeThatDoesNotTerminateIsCollectedWithTheByteAfterIt() {
    val fixture = terminal(10, 2).enterString("$ESC]0;a${ESC}Xb$BEL")
    assertEquals("a${ESC}Xb", fixture.emulator.title.current)
    fixture.assertLineIs(0, "          ")
  }

  /**
   * **A `BEL` after that `ESC` rings rather than terminating**, because the reference guards the
   * bell on the collecting state alone and the escape state is a different one. The bell's own byte
   * is not collected; the `ESC` before it still is, once the next byte arrives. Measured -
   * `title = q<ESC>r`, `bells = 1`.
   */
  @Test
  fun aBellAfterAnEscapeInsideTheStringRingsInsteadOfEndingIt() {
    val fixture = terminal(10, 2).enterString("$ESC]0;q$ESC${BEL}r$BEL")
    assertEquals("q${ESC}r", fixture.emulator.title.current)
    assertEquals(1, fixture.notices.bellsRung)
  }

  /** `TerminalTest.testBel`: outside a string a `BEL` still rings, which is the state this guards. */
  @Test
  fun aBellOutsideAStringStillRings() {
    val fixture = terminal(3, 3)
    assertEquals(0, fixture.notices.bellsRung)
    fixture.enterString(BEL)
    assertEquals(1, fixture.notices.bellsRung)
    fixture.enterString("hello$BEL")
    assertEquals(2, fixture.notices.bellsRung)
    fixture.enterString("${BEL}hello")
    assertEquals(3, fixture.notices.bellsRung)
  }

  /**
   * **Every other C0 control keeps acting on the screen while the string collects.** A carriage
   * return inside an OSC returns the carriage and is not collected - measured, `cursor = 0,0` and
   * `title = xyz`.
   */
  @Test
  fun aControlCharacterInsideTheStringStillActsOnTheScreen() {
    val fixture = terminal(10, 2).enterString("abc$ESC]0;xy\rz$BEL")
    assertEquals("xyz", fixture.emulator.title.current)
    fixture.assertLineIs(0, "abc       ").assertCursorAt(0, 0)
  }

  /**
   * A byte before the `;` that is not a digit abandons the sequence, and nothing is drawn.
   *
   * **`/0` rather than `x0`, and the difference is the whole test.** Dropping the digit guard and
   * folding the bytes anyway gives `x0` the value 400, which claims no arm either - so the obvious
   * case cannot tell a guarded parser from an unguarded one. `/` is `0x2F`, one below `'0'`, so it
   * folds to `-1` and the `0` after it folds that back to **zero**, which is the title code.
   * Measured: the reference sets no title for `ESC ] / 0 ; Hello BEL`.
   */
  @Test
  fun aNonNumericPrefixByteAbandonsTheSequence() {
    terminal(10, 2).enterString("$ESC]x0;Hello${BEL}ZZ").assertLineIs(0, "ZZ        ")
    val fixture = terminal(10, 2).enterString("$ESC]/0;Hello${BEL}ZZ")
    assertNull(fixture.emulator.title.current)
    fixture.assertLineIs(0, "ZZ        ")
  }

  /** An empty body has no number in it at all, so it is an unknown parameter and does nothing. */
  @Test
  fun anEmptyBodyIsAnUnknownParameter() {
    val fixture = terminal(10, 2).enterString("$ESC]${BEL}ZZ")
    assertNull(fixture.emulator.title.current)
    fixture.assertLineIs(0, "ZZ        ")
  }

  /**
   * **A body with no `;` at all is still a number, and `OSC 0` alone sets the title to the empty
   * string** rather than leaving it alone - measured, `titles = [null->]`. A number with no `;`
   * that claims no arm does nothing, which is what separates the two.
   */
  @Test
  fun aNumberWithNoSeparatorSetsAnEmptyTitle() {
    val fixture = terminal(10, 2).enterString("$ESC]0;Something$BEL$ESC]0$BEL")
    assertEquals("", fixture.emulator.title.current)
    assertEquals(listOf(null to "Something", "Something" to ""), fixture.notices.titleChanges)

    val unclaimed = terminal(10, 2).enterString("$ESC]0;Something$BEL$ESC]999$BEL")
    assertEquals("Something", unclaimed.emulator.title.current)
  }

  /**
   * The buffer holds **8192 characters counting the number and its `;`**, checked before each
   * append, and the byte that would overflow it abandons the sequence - so the `BEL` that follows
   * arrives in the ground state and **rings**. Measured at 8189, 8190 and 8191 body characters;
   * upstream never tests the cap.
   */
  @Test
  fun theStringIsCappedAndOverflowAbandonsTheSequence() {
    val atCap = terminal(10, 2).enterString("$ESC]0;${"a".repeat(8190)}${BEL}ZZ")
    assertEquals(8190, atCap.emulator.title.current?.length)
    assertEquals(0, atCap.notices.bellsRung)

    val overCap = terminal(10, 2).enterString("$ESC]0;${"a".repeat(8191)}${BEL}ZZ")
    assertNull(overCap.emulator.title.current)
    assertEquals(1, overCap.notices.bellsRung)
    overCap.assertLineIs(0, "ZZ        ")
  }

  /**
   * `CSI 20 t` and `CSI 21 t`, the label reports [TerminalReporting] deferred here by name. Both
   * answer with a **constant** rather than the real title, which upstream does on purpose - the
   * comment beside them reads "Disabled due to security concerns".
   */
  @Test
  fun theLabelReportsAnswerWithConstantsRatherThanTheTitle() {
    val fixture = terminal(10, 2).enterString("$ESC]0;Set$BEL")
    fixture.assertReplyTo("$ESC[20t", "$ESC]LIconLabel$ST")
    fixture.assertReplyTo("$ESC[21t", "$ESC]l$ST")
  }

  private companion object {
    const val ESC = "\u001B"
    const val BEL = "\u0007"

    /** The string terminator, `ESC \`. */
    const val ST = "\u001B\\"
  }
}
