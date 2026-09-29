package com.jupydroid.app.terminal

import org.junit.Test

/**
 * `ESC P`, Device Control String: a string collected until its terminator, then read as a
 * termcap/terminfo query (`+q`) or a DECRQSS request (`$q`).
 *
 * Transcribed from `DeviceControlStringTest.java`'s three methods (termux-app `3df69d1d`,
 * Apache 2.0 - see [TerminalGrid]). **The termcap table stops at what that test exercises** -
 * `Co`/`colors`/`TN`/`name` and `kB` - because every other capability the reference answers
 * depends on an Android key identity and the cursor/keypad application modes `terminal/` has no
 * caller for until P4j.
 */
class DeviceControlStringTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  private fun hexEncode(text: String): String = text.map { "%02X".format(it.code) }.joinToString("")

  private fun assertCapabilityResponse(fixture: EmulatorFixture, capability: String, expected: String) {
    val input = "${ESC}P+q${hexEncode(capability)}$ESC\\"
    val response = "${ESC}P1+r${hexEncode(capability)}=${hexEncode(expected)}$ESC\\"
    fixture.assertReplyTo(input, response)
  }

  /** `testReportColorsAndName`. */
  @Test
  fun theColourCountAndTerminalNameAreReportedInHex() {
    val fixture = terminal(3, 3).enterString("A")
    assertCapabilityResponse(fixture, "Co", "256")
    assertCapabilityResponse(fixture, "colors", "256")
    assertCapabilityResponse(fixture, "TN", "xterm")
    assertCapabilityResponse(fixture, "name", "xterm")
    fixture.enterString("B").assertLinesAre("AB ", "   ", "   ")
  }

  /** `testReportKeys`. */
  @Test
  fun backTabIsReportedForKB() {
    assertCapabilityResponse(terminal(3, 3), "kB", "$ESC[Z")
  }

  /** `testReallyLongDeviceControlString`. */
  @Test
  fun anOverlongDeviceControlStringIsDiscarded() {
    val fixture = terminal(3, 3).enterString("${ESC}P")
    repeat(10_000) { fixture.enterString("a".repeat(63)) }
    fixture.assertLineIs(0, "aaa")
    fixture.assertLineIs(1, "aaa")
  }

  /**
   * Read off `RefScreen`, pinning the one mutation the differential's own alphabet cannot reach -
   * every entry it sends is short, so the cap's exact boundary is a unit test's job. At 8192 bytes
   * collected the string is still open: a 8193rd byte still fits under [MAX_STRING_LENGTH], so
   * both it and the byte after it are consumed into the buffer rather than drawn.
   */
  @Test
  fun atTheCapBothTheNextByteAndTheOneAfterItAreStillCollected() {
    val fixture = terminal(5, 2)
    fixture.enterString("${ESC}P" + "a".repeat(8192) + "ZY")
    fixture.assertLinesAre("     ", "     ")
  }

  /**
   * One byte more and the reference's own off-by-one shows: the string is already **past**
   * [MAX_STRING_LENGTH], so the next byte aborts the sequence and is dropped with it, and the byte
   * after that lands on the screen as ordinary text - the reference's `unknownSequence`-free
   * silent abort, one byte later than [OperatingSystemCommand]'s own cap.
   */
  @Test
  fun oneByteMoreAndTheAbortingByteIsDroppedButTheNextIsDrawn() {
    val fixture = terminal(5, 2)
    fixture.enterString("${ESC}P" + "a".repeat(8193) + "ZY")
    fixture.assertLinesAre("Y    ", "     ")
  }

  /**
   * Read off `RefScreen`, since the reference's own suite never sends an `ESC` mid-string. The
   * guard is `TerminalEmulator.java`'s `case 27: if (mEscapeState == ESC_P) return;` - the byte is
   * dropped, not buffered and not a terminator - so a stray `ESC` changes nothing and the **bare**
   * `\` that follows still ends the string, with no `ESC` of its own.
   */
  @Test
  fun aStrayEscapeInsideTheStringIsDroppedAndABareBackslashStillTerminates() {
    val fixture = terminal(3, 3)
    val input = "${ESC}P+q${hexEncode("Co")}" + ESC + "\\"
    val response = "${ESC}P1+r${hexEncode("Co")}=${hexEncode("256")}$ESC\\"
    fixture.assertReplyTo(input, response)
  }

  /** Read off `RefScreen`: the terminator needs no preceding `ESC` at all. */
  @Test
  fun aBareBackslashWithNoEscapeAlsoTerminates() {
    val fixture = terminal(3, 3)
    fixture.assertReplyTo("${ESC}P+q${hexEncode("Co")}\\", "${ESC}P1+r${hexEncode("Co")}=${hexEncode("256")}$ESC\\")
  }

  /**
   * Read off `RefScreen`. A name that is not `Co`/`colors`/`TN`/`name`/`kB` is a valid hex-encoded
   * request the terminal simply does not have an answer for - the reference's own "invalid
   * request", `DCS 0 + r <part> ST`, echoing the request rather than the (nonexistent) value.
   */
  @Test
  fun anUnknownCapabilityNameAnswersInvalid() {
    val hex = hexEncode("xy")
    terminal(3, 3).assertReplyTo("${ESC}P+q$hex$ESC\\", "${ESC}P0+r$hex$ESC\\")
  }

  /**
   * Read off `RefScreen`. `ZZ` is not valid hex - the reference's per-pair
   * `NumberFormatException` catch skips it rather than crashing, leaving the decoded name empty,
   * which resolves to the same "invalid request" as any other name nothing answers. Named because
   * a naive `String.toInt(16)` transcription throws here instead.
   */
  @Test
  fun aMalformedHexPairAnswersInvalidRatherThanCrashing() {
    terminal(3, 3).assertReplyTo("${ESC}P+qZZ$ESC\\", "${ESC}P0+rZZ$ESC\\")
  }

  /** Read off `RefScreen`: an odd-length name is the reference's own logged error, and no reply at all. */
  @Test
  fun anOddLengthCapabilityNameGetsNoReplyAtAll() {
    terminal(3, 3).assertReplyTo("${ESC}P+q617$ESC\\", "")
  }

  /** Read off `RefScreen`: DECRQSS answers only DECSCL, echoing the sequence that restores it. */
  @Test
  fun decrqssAnswersDecsclAndNothingElse() {
    val fixture = terminal(3, 3)
    fixture.assertReplyTo("${ESC}P\$q\"p$ESC\\", "${ESC}P1\$r64;1\"p$ESC\\")
    fixture.assertReplyTo("${ESC}P\$qzz$ESC\\", "")
  }

  /** Read off `RefScreen`: a DCS body that is neither `\$q` nor `+q` is silently dropped. */
  @Test
  fun anUnrecognizedDeviceControlStringGetsNoReply() {
    terminal(3, 3).assertReplyTo("${ESC}Pxyz$ESC\\", "")
  }

  private companion object {
    const val ESC = "\u001B"
  }
}
