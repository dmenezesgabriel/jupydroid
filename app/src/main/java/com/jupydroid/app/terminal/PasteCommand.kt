package com.jupydroid.app.terminal

/**
 * `paste(String)`: text pasted into the terminal as though it had been typed, bracketed under
 * DECSET 2004.
 *
 * Transcribed from `TerminalEmulator.paste` (termux-app `3df69d1d`, Apache 2.0 - see
 * [TerminalGrid]). `ESC` and the C1 control range are stripped outright - a pasted escape
 * sequence is not something a shell should see reinterpreted - and CRLF/LF become CR, matching
 * what a key press would send.
 *
 * ```
 * val paste = PasteCommand(modes, replies)
 * paste.paste("a")   // replies "a"
 * ```
 */
class PasteCommand(private val modes: ScreenModes, private val replies: TerminalReplies) {
  fun paste(text: String) {
    val sanitized = text.replace(STRIP_PATTERN, "").replace(NEWLINE_PATTERN, "\r")
    val bracketed = modes.isSet(DecMode.BRACKETED_PASTE)
    if (bracketed) replies.reply(START_PASTE)
    replies.reply(sanitized)
    if (bracketed) replies.reply(END_PASTE)
  }

  private companion object {
    /** `ESC` and the C1 control range `0x80..0x9F`, removed outright before anything else. */
    val STRIP_PATTERN = Regex("[\u001B\u0080-\u009F]")
    val NEWLINE_PATTERN = Regex("\r?\n")
    const val START_PASTE = "\u001B[200~"
    const val END_PASTE = "\u001B[201~"
  }
}
