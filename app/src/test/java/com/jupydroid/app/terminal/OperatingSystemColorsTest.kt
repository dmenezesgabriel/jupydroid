package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The `OSC` numbers that set, query and reset a colour: `4`, `10`/`11`/`12`, `104`, `110`/`111`/
 * `112` and `119`.
 *
 * `OperatingSystemControlTest.testSetColor`, `testResetColor`, `testResettingTerminalResetsColor`,
 * `testSettingDynamicColors` and `testReportSpecialColors` are transcribed (termux-app `3df69d1d`,
 * Apache 2.0 - see [TerminalGrid]); the rest was measured off `RefScreen`.
 *
 * **Two of these tests state an expectation the reference cannot be asked for, because it throws.**
 * `OSC 104` has two unguarded failures upstream - `ColorCommands.resetIndexed`'s own KDoc names
 * both - and the expectation here is the reference's own stated intent
 * (`catch (NumberFormatException) { // Ignore. }`) rather than a reading of what it does.
 */
class OperatingSystemColorsTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  /** `testSetColor`: `OSC 4` takes any number of index-and-spec pairs. */
  @Test
  fun indexedColoursAreSetOneOrManyAtATime() {
    val fixture = terminal(4, 4)
    fixture.enterString("$ESC]4;5;#00FF00$BEL").assertColor(5, 0xFF00FF00.toInt())
    fixture.enterString("$ESC]4;5;#00FFAB$BEL").assertColor(5, 0xFF00FFAB.toInt())
    fixture.enterString("$ESC]4;255;#ABFFAB$BEL").assertColor(255, 0xFFABFFAB.toInt())
    // Two indexed colors at once:
    fixture.enterString("$ESC]4;7;#00FF00;8;#0000FF$BEL")
    fixture.assertColor(7, 0xFF00FF00.toInt()).assertColor(8, 0xFF0000FF.toInt())
  }

  /**
   * `OSC 4`'s index is guarded to `0..255`, so it will not reach the three dynamic entries -
   * **where `OSC 104`'s is not guarded at all and resets 258 happily**. Measured; the two arms
   * disagree about the range and both are transcribed as they stand.
   */
  @Test
  fun anIndexedColourOutsideTheFirst256AbandonsTheSequence() {
    val fixture = terminal(4, 4)
    val wasForeground = fixture.emulator.screen.colors.at(CellStyle.COLOR_INDEX_FOREGROUND)
    fixture.enterString("$ESC]4;256;#00FF00${BEL}ZZ")
    fixture.assertColor(CellStyle.COLOR_INDEX_FOREGROUND, wasForeground).assertLineIs(0, "ZZ  ")
  }

  /**
   * **`OSC 4` has no query, where `OSC 10`/`11`/`12` do.** xterm answers `4;n;?` and this reference
   * does not: the `?` falls through into the colour spec, fails to parse, and leaves the entry
   * alone - while still reporting, because the report is beside the parse and not inside it.
   */
  @Test
  fun anIndexedColourQueryIsNotAnsweredAndStillReports() {
    val fixture = terminal(4, 4)
    val wasFive = fixture.emulator.screen.colors.at(5)
    val reportsBefore = fixture.notices.colorsChanged
    fixture.assertReplyTo("$ESC]4;5;?$BEL", "")
    fixture.assertColor(5, wasFive)
    assertEquals(reportsBefore + 1, fixture.notices.colorsChanged)
  }

  /** `testSettingDynamicColors`: `10`, `11` and `12`, and the walk down the list from any of them. */
  @Test
  fun theDynamicColoursAreSetAndSuccessiveSpecsWalkDownTheList() {
    val fixture = terminal(3, 3)
    fixture.enterString("$ESC]10;#ABCD00$BEL").assertColor(FOREGROUND, 0xFFABCD00.toInt())
    fixture.enterString("$ESC]11;#0ABCD0$BEL").assertColor(BACKGROUND, 0xFF0ABCD0.toInt())
    fixture.enterString("$ESC]12;#00ABCD$BEL").assertColor(CURSOR, 0xFF00ABCD.toInt())

    // "Each successive parameter changes the next color in the list."
    fixture.enterString("$ESC]10;#FF0000;#00FF00$BEL")
    fixture.assertColor(FOREGROUND, 0xFFFF0000.toInt()).assertColor(BACKGROUND, 0xFF00FF00.toInt())
    fixture.enterString("$ESC]10;#0000FF;#00FF00;#FF0000$BEL")
    fixture.assertColor(FOREGROUND, 0xFF0000FF.toInt()).assertColor(BACKGROUND, 0xFF00FF00.toInt())
    fixture.assertColor(CURSOR, 0xFFFF0000.toInt())
  }

  /** `testSettingDynamicColors`' other half: a trailing `;` is tolerated, and so is `ESC \`. */
  @Test
  fun aDynamicColourTakesATrailingSeparatorAndEitherTerminator() {
    val fixture = terminal(3, 3)
    fixture.enterString("$ESC]11;#FFFF00;$BEL").assertColor(BACKGROUND, 0xFFFFFF00.toInt())
    fixture.enterString("$ESC]12;#00FFFF;$BEL").assertColor(CURSOR, 0xFF00FFFF.toInt())
    fixture.enterString("$ESC]10;#FF0000$ST").assertColor(FOREGROUND, 0xFFFF0000.toInt())
    fixture.enterString("$ESC]11;#FFFF00;$ST").assertColor(BACKGROUND, 0xFFFFFF00.toInt())
    fixture.enterString("$ESC]12;#00FFFF;$ST").assertColor(CURSOR, 0xFF00FFFF.toInt())
  }

  /**
   * `testReportSpecialColors`: the answer is the **longest** representation, `rgb:RRRR/GGGG/BBBB`,
   * and it is terminated **the way the request was** - which a script reading a background colour
   * out of `xterm` relies on.
   */
  @Test
  fun aDynamicColourQueryAnswersInSixteenBitsAndEchoesTheTerminator() {
    val fixture = terminal(3, 3)
    fixture.enterString("$ESC]10;#ABCD00$BEL").assertColor(FOREGROUND, 0xFFABCD00.toInt())
    fixture.assertReplyTo("$ESC]10;?$BEL", "$ESC]10;rgb:abab/cdcd/0000$BEL")
    fixture.assertReplyTo("$ESC]10;?$ST", "$ESC]10;rgb:abab/cdcd/0000$ST")
  }

  /** A query names its own number back, so a script asking for three gets three distinct answers. */
  @Test
  fun eachDynamicColourQueryNamesItsOwnNumber() {
    val fixture = terminal(3, 3)
    fixture.enterString("$ESC]10;#000000$BEL$ESC]11;#FFFFFF$BEL$ESC]12;#FF0000$BEL")
    fixture.assertReplyTo("$ESC]11;?$BEL", "$ESC]11;rgb:ffff/ffff/ffff$BEL")
    fixture.assertReplyTo("$ESC]12;?$BEL", "$ESC]12;rgb:ffff/0000/0000$BEL")
  }

  /** `testResetColor`: `OSC 104` resets one index, several, or - with no parameter - all of them. */
  @Test
  fun indexedColoursAreResetOneSeveralOrAll() {
    val fixture = terminal(4, 4)
    val wasFive = fixture.emulator.screen.colors.at(5)
    val wasSix = fixture.emulator.screen.colors.at(6)

    fixture.enterString("$ESC]4;5;#00FF00$BEL$ESC]4;6;#0000FF$BEL$ESC]104;5$BEL")
    fixture.assertColor(5, wasFive).assertColor(6, 0xFF0000FF.toInt())

    fixture.enterString("$ESC]4;5;#00FF00$BEL$ESC]104;5;6$BEL")
    fixture.assertColor(5, wasFive).assertColor(6, wasSix)

    fixture.enterString("$ESC]4;5;#00FF00$BEL$ESC]104$BEL").assertColor(5, wasFive)
    fixture.enterString("$ESC]4;5;#00FF00$ST$ESC]104;5$ST").assertColor(5, wasFive)
  }

  /** `OSC 110`, `111` and `112` put one dynamic colour back and leave the other two alone. */
  @Test
  fun eachDynamicColourIsResetOnItsOwn() {
    val fixture = terminal(4, 4)
    fixture.enterString("$ESC]10;#ABCD00$BEL$ESC]11;#0ABCD0$BEL$ESC]12;#00ABCD$BEL")
    fixture.enterString("$ESC]110$BEL")
    fixture.assertColor(FOREGROUND, WHITE).assertColor(BACKGROUND, 0xFF0ABCD0.toInt())
    fixture.enterString("$ESC]111$BEL$ESC]112$BEL")
    fixture.assertColor(BACKGROUND, BLACK).assertColor(CURSOR, WHITE)
  }

  /** `OSC 119`, reset highlight colour, is accepted and does nothing - it has no colour to reset. */
  @Test
  fun theHighlightResetIsAcceptedAndDoesNothing() {
    val fixture = terminal(4, 4)
    fixture.enterString("$ESC]4;5;#00FF00$BEL")
    val reportsBefore = fixture.notices.colorsChanged
    fixture.enterString("$ESC]119${BEL}ZZ")
    fixture.assertColor(5, 0xFF00FF00.toInt()).assertLineIs(0, "ZZ  ")
    assertEquals(reportsBefore, fixture.notices.colorsChanged)
  }

  /** `testResettingTerminalResetsColor`, and RIS, which reaches the same reset. */
  @Test
  fun aTerminalResetPutsEveryColourBack() {
    val fixture = terminal(4, 4)
    val wasFive = fixture.emulator.screen.colors.at(5)
    fixture.enterString("$ESC]4;5;#00FFAB$BEL").assertColor(5, 0xFF00FFAB.toInt())
    fixture.emulator.reset()
    fixture.assertColor(5, wasFive)

    fixture.enterString("$ESC]4;5;#00FFAB$BEL$ESC]10;#ABCD00$BEL")
    fixture.enterString("${ESC}c")
    fixture.assertColor(5, wasFive).assertColor(FOREGROUND, WHITE)
  }

  /**
   * **The expectation here is ours, because the reference throws.** Its `OSC 104` loop puts the
   * end-of-input `break` inside a `try` that swallows `NumberFormatException`, so a token that is
   * not a number - or a `;` in the last position - walks the index past the end of the string and
   * throws `StringIndexOutOfBoundsException`; and it hands the index to an unchecked array write,
   * so anything outside `0..258` throws `ArrayIndexOutOfBoundsException`. Both are reachable from
   * ordinary shell output. What this does instead is the reference's own stated intent - the
   * comment on that `catch` reads `// Ignore.` - applied to every token: skip it and go on.
   */
  @Test
  fun aResetSkipsATokenThatIsNotAnIndexRatherThanWalkingOffTheString() {
    val fixture = terminal(4, 4)
    val wasOne = fixture.emulator.screen.colors.at(1)
    val wasFive = fixture.emulator.screen.colors.at(5)
    fixture.enterString("$ESC]4;1;#00FF00$BEL$ESC]4;5;#00FF00$BEL")

    fixture.enterString("$ESC]104;1;x${BEL}ZZ").assertColor(1, wasOne).assertColor(5, 0xFF00FF00.toInt())
    fixture.enterString("$ESC]104;5;${BEL}ZZ").assertColor(5, wasFive)
    fixture.assertLineIs(0, "ZZZZ")

    fixture.enterString("$ESC]4;5;#00FF00$BEL$ESC]104;x;5$BEL").assertColor(5, wasFive)
    fixture.enterString("$ESC]4;5;#00FF00$BEL$ESC]104;99999999999;5$BEL").assertColor(5, wasFive)
  }

  /** The same for an index no table has: skipped, where the reference indexes the array and throws. */
  @Test
  fun aResetSkipsAnIndexOutsideTheTableRatherThanThrowing() {
    val fixture = terminal(4, 4)
    val wasFive = fixture.emulator.screen.colors.at(5)
    fixture.enterString("$ESC]4;5;#00FF00$BEL")
    fixture.enterString("$ESC]104;259;5${BEL}ZZ").assertColor(5, wasFive).assertLineIs(0, "ZZ  ")
    fixture.enterString("$ESC]4;5;#00FF00$BEL$ESC]104;-1;5$BEL").assertColor(5, wasFive)
    fixture.enterString("$ESC]4;5;#00FF00$BEL$ESC]104;258$BEL").assertColor(5, 0xFF00FF00.toInt())
  }

  private companion object {
    const val ESC = "\u001B"
    const val BEL = "\u0007"
    const val ST = "\u001B\\"

    const val FOREGROUND = CellStyle.COLOR_INDEX_FOREGROUND
    const val BACKGROUND = CellStyle.COLOR_INDEX_BACKGROUND
    const val CURSOR = CellStyle.COLOR_INDEX_CURSOR

    /** The reference palette's foreground and cursor, and its background. */
    const val WHITE = -1
    const val BLACK = 0xFF shl 24
  }
}
