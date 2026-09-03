package com.jupydroid.app.terminal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `OSC 52`, Manipulate Selection Data - a shell writing to the clipboard.
 *
 * `OperatingSystemControlTest.disabledTestSetClipboard` is transcribed and, unlike upstream, **run**
 * here: its own name and comment say why it never ran there - *"Cannot run this as a unit test since
 * Base64 is a android.util class."* `terminal/` is `android.*`-free by `ArchitectureGuardTest`
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]), so `java.util.Base64` does the same job
 * and the case is a real test. The rest was measured off `RefScreen`.
 */
class OperatingSystemClipboardTest {
  private fun terminal(columns: Int, rows: Int) = EmulatorFixture(columns, rows)

  /** `disabledTestSetClipboard`, with the selection parameter `c` (clipboard). */
  @Test
  fun aBase64PayloadIsDecodedAndCopiedToTheClipboard() {
    val fixture = terminal(4, 4)
    val payload = base64("Hello, world")
    fixture.enterString("$ESC]52;c;$payload$BEL")
    assertEquals(listOf("Hello, world"), fixture.notices.clipboardCopies)
  }

  /** The selection parameter is skipped whatever it is, single letter or several. */
  @Test
  fun anySelectionParameterIsSkippedTheSameWay() {
    val fixture = terminal(4, 4)
    val payload = base64("Hello, world")
    fixture.enterString("$ESC]52;pc;$payload$BEL")
    assertEquals(listOf("Hello, world"), fixture.notices.clipboardCopies)
  }

  /**
   * **No `;` at all is not an error.** `indexOf(";")` answers `-1`, `+1` makes the start index `0`,
   * and the whole parameter is read as the payload - upstream has no selection parameter to skip
   * and does not require one.
   */
  @Test
  fun aPayloadWithNoSelectionParameterIsReadWhole() {
    val fixture = terminal(4, 4)
    val payload = base64("Hello, world")
    fixture.enterString("$ESC]52;$payload$BEL")
    assertEquals(listOf("Hello, world"), fixture.notices.clipboardCopies)
  }

  /** Non-ASCII round-trips through UTF-8, not through the platform default charset. */
  @Test
  fun aUnicodePayloadRoundTripsThroughUtf8() {
    val fixture = terminal(4, 4)
    val payload = base64("Goodbye, ñ world")
    fixture.enterString("$ESC]52;c;$payload$BEL")
    assertEquals(listOf("Goodbye, ñ world"), fixture.notices.clipboardCopies)
  }

  /** Both terminators end the string, exactly as they do for the title and the colours. */
  @Test
  fun bothTerminatorsEndTheString() {
    val fixture = terminal(4, 4)
    val payload = base64("Hello, world")
    fixture.enterString("$ESC]52;c;$payload$ST")
    assertEquals(listOf("Hello, world"), fixture.notices.clipboardCopies)
  }

  /**
   * **Invalid Base64 is swallowed, not thrown.** Measured - `clipboard = ` (empty) - which is
   * upstream's `catch (Exception e) { Logger.logError(...) }`: no clipboard write, and nothing else
   * about the terminal moves either.
   */
  @Test
  fun invalidBase64IsSwallowedAndNothingIsCopied() {
    val fixture = terminal(4, 4)
    fixture.enterString("$ESC]52;c;not_valid_base64!!!${BEL}ZZ")
    assertTrue(fixture.notices.clipboardCopies.isEmpty())
    fixture.assertLineIs(0, "ZZ  ")
  }

  /**
   * **A payload wrapped across lines still decodes, and for a reason worth stating**: a `\n`
   * inside the string is a control character, not a byte for the collector - it moves the cursor
   * on the screen and is never appended to the buffer, so what reaches the decoder is already
   * newline-free. Measured against the reference with a payload long enough that `base64`'s
   * default wrapping introduces a line break, and pinned here on the screen it leaves as well as
   * the clipboard it fills.
   */
  @Test
  fun aPayloadWrappedAcrossLinesStillDecodesBecauseTheLineFeedNeverReachesIt() {
    val fixture = terminal(4, 4)
    val text = "This is a longer payload that will wrap across multiple lines when base64 encoded"
    val wrapped = java.util.Base64.getMimeEncoder(
      WRAP_WIDTH,
      "\n".toByteArray(),
    ).encodeToString(text.toByteArray(Charsets.UTF_8))
    fixture.enterString("$ESC]52;c;$wrapped$BEL")
    assertEquals(listOf(text), fixture.notices.clipboardCopies)
    // The embedded \n moved the cursor down a row, which is the C0 dispatch's own evidence.
    fixture.assertCursorAt(1, 0)
  }

  /** An empty payload after the selection parameter decodes to an empty string, and is copied. */
  @Test
  fun anEmptyPayloadCopiesAnEmptyString() {
    val fixture = terminal(4, 4)
    fixture.enterString("$ESC]52;c;$BEL")
    assertEquals(listOf(""), fixture.notices.clipboardCopies)
  }

  /**
   * **`OSC 52` writes to the clipboard, and to nothing else.** Measured: no reply, no bell, no
   * colour-change report - it is the one string command with no side effect but the one notice.
   */
  @Test
  fun aClipboardWriteRepliesNothingAndRingsNothing() {
    val fixture = terminal(4, 4)
    val payload = base64("Hello")
    val colorsBefore = fixture.notices.colorsChanged
    val bellsBefore = fixture.notices.bellsRung
    fixture.assertReplyTo("$ESC]52;c;$payload$BEL", "")
    assertEquals(colorsBefore, fixture.notices.colorsChanged)
    assertEquals(bellsBefore, fixture.notices.bellsRung)
  }

  private fun base64(text: String): String =
    java.util.Base64.getEncoder().encodeToString(text.toByteArray(Charsets.UTF_8))

  private companion object {
    /** Android's default `Base64.encodeToString` line width. */
    const val WRAP_WIDTH = 76

    const val ESC = "\u001B"
    const val BEL = "\u0007"
    const val ST = "\u001B\\"
  }
}
