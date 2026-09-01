package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the emulator does with text and C0 controls alone, before any escape sequence exists.
 *
 * Eight of `UnicodeInputTest`'s eleven cases, plus `TerminalTest`'s `testNormalization`, `testBel`,
 * `testAutomargins` and the first half of `testTab` (termux-app `3df69d1d`, Apache 2.0 - see
 * [TerminalGrid]). **The rest of those classes drive escape sequences and go with the step that
 * adds them**: `testWideCharacterDeletion` and `testWideCharOverwriting` need `CSI D` (P4f-3b),
 * `testWideCharacterWithoutWrapping` needs `CSI ? 7 l` (P4f-4), and `testLineDrawing` is in
 * `EscapeDispatchTest` (P4f-3a) - `LineDrawingCharsetTest` covers the table itself here.
 * `testTab`'s second line needs SGR and is in `EscapeSequenceTest`, which has one.
 *
 * Combining marks stay spelled as `\uXXXX`, as the reference spells them, so a reader can see which
 * mark is meant rather than trusting an editor to render it.
 */
class PlainTextInputTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  @Test
  fun anIllFormedSequenceDoesNotConsumeItsSuccessorByte() {
    terminal(5, 5)
      .append(byteArrayOf(0b1110_1111.toByte(), 'a'.code.toByte()))
      .assertLineIs(0, "�a   ")

    // https://code.google.com/p/chromium/issues/detail?id=212704
    terminal(10, 2)
      .append(
        byteArrayOf(
          0x61, 0xF1.toByte(), 0x80.toByte(), 0x80.toByte(), 0xE1.toByte(), 0x80.toByte(),
          0xC2.toByte(), 0x62, 0x80.toByte(), 0x63, 0x80.toByte(), 0xBF.toByte(), 0x64,
        ),
      )
      .assertLinesAre("a���b�c��d", "          ")
  }

  @Test
  fun surrogateCodePointsSpelledInUtf8BecomeReplacementCharacters() {
    terminal(5, 2)
      .append(
        byteArrayOf(
          0xED.toByte(), 0xA0.toByte(), 0x80.toByte(), 0xED.toByte(), 0xAD.toByte(), 0xBF.toByte(),
          0xED.toByte(), 0xAE.toByte(), 0x80.toByte(), 0xED.toByte(), 0xBF.toByte(), 0xBF.toByte(),
        ),
      )
      .assertLinesAre("���� ", "     ")
  }

  @Test
  fun aTruncatedThreeByteSequenceIsOneReplacementCharacter() {
    // https://bugzilla.mozilla.org/show_bug.cgi?id=746900
    terminal(5, 2).append(byteArrayOf(0xE0.toByte(), 0xA0.toByte(), ' '.code.toByte()))
      .assertLinesAre("�    ", "     ")
  }

  @Test
  fun anUnassignedCodePointBecomesOneReplacementCharacter() {
    terminal(3, 3)
      .append(byteArrayOf(0xF3.toByte(), 0x82.toByte(), 0x95.toByte(), 0x81.toByte())) // U+C2541
      .enterString("Y")
      .assertLineStartsWith(0, UNICODE_REPLACEMENT_CHAR, 'Y'.code, ' '.code)
  }

  @Test
  fun anOverlongEncodingBecomesOneReplacementCharacter() {
    terminal(5, 5).append(byteArrayOf(0xC0.toByte(), 0xA0.toByte(), 'Y'.code.toByte()))
      .assertLineIs(0, "�Y   ")
  }

  /** `UnicodeInputTest.testStuff`: a stream of mixed rubbish must leave the invariants standing. */
  @Test
  fun aStreamOfMixedRubbishKeepsTheScreenWellFormed() {
    terminal(80, 24).append(
      byteArrayOf(
        0xF3.toByte(), 0x82.toByte(), 0x95.toByte(), 0x81.toByte(), 0x61, 0x38, 0xE7.toByte(),
        0x8F.toByte(), 0xAE.toByte(), 0xC2.toByte(), 0x9F.toByte(), 0xE8.toByte(), 0xA0.toByte(),
        0x9F.toByte(), 0xE8.toByte(), 0x8C.toByte(), 0xA4.toByte(), 0xED.toByte(), 0x93.toByte(),
        0x89.toByte(), 0xEF.toByte(), 0xBF.toByte(), 0xBD.toByte(), 0x42, 0xC2.toByte(),
        0x9B.toByte(), 0xE6.toByte(), 0x87.toByte(), 0x89.toByte(), 0x5A,
      ),
    )
  }

  @Test
  fun aCombiningCharacterAttachesToTheColumnBeforeIt() {
    terminal(3, 2).enterString(" â ").assertLinesAre(" â ", "   ")
  }

  @Test
  fun aCombiningCharacterInTheFirstColumnStandsAlone() {
    terminal(5, 3).enterString("test\r\nhi\r\n").assertLinesAre("test ", "hi   ", "     ")

    // U+0302 COMBINING CIRCUMFLEX ACCENT; the case is mosh's (http://mosh.mit.edu/).
    terminal(5, 5).enterString("test\r\nabc\r\n̂\r\ndef\r\n")
      .assertLinesAre("test ", "abc  ", " ̂    ", "def  ", "     ")
  }

  @Test
  fun aCombiningCharacterInTheLastColumnAttachesRatherThanWrapping() {
    terminal(3, 2).enterString("  â").assertLinesAre("  â", "   ")
    terminal(3, 2).enterString("  à̲").assertLinesAre("  à̲", "   ")
    terminal(3, 2).enterString("Aà̲F").assertLinesAre("Aà̲F", "   ")
  }

  @Test
  fun aWideCharacterThatCannotFitTheLastColumnWrapsWhole() {
    terminal(3, 2).enterString("  枝̂").assertLinesAre("   ", "枝̂ ")

    terminal(3, 2).enterString(" 枝").assertLinesAre(" 枝", "   ").assertCursorAt(0, 2)
      .enterString("a").assertLinesAre(" 枝", "a  ")
  }

  @Test
  fun combiningACharacterOntoAnotherLeavesOneColumn() {
    terminal(3, 3).enterString("ñ").assertLinesAre("ñ  ", "   ", "   ")
    assertEquals(1, CharacterWidth.of(0x006E) + CharacterWidth.of(0x0303))
  }

  @Test
  fun everyBellIsRungWhereverItSitsInTheStream() {
    val terminal = terminal(3, 3)
    assertEquals(0, terminal.notices.bellsRung)
    terminal.enterString(BELL)
    assertEquals(1, terminal.notices.bellsRung)
    terminal.enterString("hello$BELL")
    assertEquals(2, terminal.notices.bellsRung)
    terminal.enterString("${BELL}hello")
    assertEquals(3, terminal.notices.bellsRung)
    terminal.enterString("hello${BELL}world")
    assertEquals(4, terminal.notices.bellsRung)
  }

  @Test
  fun theCursorRestsOnTheLastColumnUntilOneMoreCharacterWrapsIt() {
    terminal(3, 3).enterString("abc").assertLinesAre("abc", "   ", "   ").assertCursorAt(0, 2)
      .enterString("d").assertLinesAre("abc", "d  ", "   ").assertCursorAt(1, 1)

    terminal(3, 3).enterString("abc\r ").assertLinesAre(" bc", "   ", "   ").assertCursorAt(0, 1)
  }

  @Test
  fun aTabMovesToTheNextStopWithoutErasingWhatItPassesOver() {
    terminal(11, 2).enterString("01234567890\r\tXX").assertLinesAre("01234567XX0", "           ")
  }

  /**
   * The byte-level counterpart of JediTerm's `EmulatorTest.testSplitSurrogatePair`, and the case
   * this transport actually has: bytes reach [TerminalEmulator.append] in WebSocket frames, so a
   * multi-byte character straddles a frame boundary whenever the shell writes one near an edge.
   * Neither reference suite covers it.
   */
  @Test
  fun aCharacterSplitAcrossTwoAppendsIsStillOneCharacter() {
    val terminal = terminal(4, 2)
    terminal.append(byteArrayOf(0xE6.toByte(), 0x9E.toByte())) // U+679D, first two bytes
    terminal.append(byteArrayOf(0x9D.toByte(), 'a'.code.toByte()))
    terminal.assertLinesAre("枝a ", "    ")
  }

  private companion object {
    const val BELL = "\u0007"
  }
}
