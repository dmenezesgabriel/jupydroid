package com.jupydroid.app.terminal

/**
 * `ESC P`, Device Control String: a string collected until its terminator, then read as a DECRQSS
 * request (`$q`) or a termcap/terminfo query (`+q`).
 *
 * Transcribed from `TerminalEmulator.doDeviceControl` (termux-app `3df69d1d`, Apache 2.0 - see
 * [TerminalGrid]). **One state, not two** - unlike [OperatingSystemCommand], an `ESC` mid-string is
 * not a lookahead here: the reference's guard at its `case 27` drops the byte outright while
 * `mEscapeState == ESC_P`, so [TerminalEmulator]'s own C0 dispatch swallows it before this class
 * ever sees it, and the terminator is a **bare** `\` with no `ESC` of its own.
 *
 * **The termcap table stops at what the reference's own test exercises** -
 * `Co`/`colors`/`TN`/`name` and `kB`.
 *
 * ```
 * val dcs = DeviceControlString(replies)
 * dcs.startString()                       // ESC P arrived
 * "+q436f".forEach { dcs.collect(it.code) }
 * dcs.collect('\\'.code)                  // answers Co's colour count
 * ```
 */
class DeviceControlString(private val replies: TerminalReplies) {
  private val argument = StringBuilder()

  /** `ESC P`: the buffer is emptied and the string starts. */
  fun startString(): EscapeState {
    argument.setLength(0)
    return EscapeState.DeviceControlString
  }

  /**
   * A code point inside the string. A bare `\` ends it - **no `ESC` needed**, unlike `OSC` - and
   * the byte that would overflow the buffer clears it and abandons the sequence silently, which is
   * the reference's own off-by-one from [OperatingSystemCommand]'s cap: it bails once the buffer is
   * already *past* [MAX_STRING_LENGTH], not at it.
   */
  fun collect(codePoint: Int): EscapeState = when (codePoint) {
    TERMINATOR -> applyAndFinish()
    else -> collectOrOverflow(codePoint)
  }

  private fun collectOrOverflow(codePoint: Int): EscapeState {
    if (argument.length > MAX_STRING_LENGTH) {
      argument.setLength(0)
      return EscapeState.Ground
    }
    argument.appendCodePoint(codePoint)
    return EscapeState.DeviceControlString
  }

  /**
   * The string is over. `$q` is DECRQSS, `+q` is the termcap/terminfo query; anything else is the
   * reference's own "unrecognized device control string" - logged upstream, dropped here too.
   */
  private fun applyAndFinish(): EscapeState {
    val body = argument.toString()
    when {
      body.startsWith(REQUEST_STATUS_STRING) -> applyRequestStatusString(body.removePrefix(REQUEST_STATUS_STRING))
      body.startsWith(TERMCAP_QUERY) -> applyTermcapQuery(body.removePrefix(TERMCAP_QUERY))
      else -> Unit
    }
    return EscapeState.Ground
  }

  /**
   * DECRQSS. **Only DECSCL is answered**, echoing the sequence that would restore it - `64;1"p`,
   * conformance level 64 with feature bit 1. Any other request is the reference's own "Unrecognized
   * DECRQSS string": logged upstream, silently dropped here.
   */
  private fun applyRequestStatusString(request: String) {
    if (request == DECSCL_REQUEST) replies.reply(DEVICE_CONTROL_STRING + "1\$r" + DECSCL_RESPONSE + STRING_TERMINATOR)
  }

  /**
   * The termcap/terminfo query: one or more `;`-separated capability names, each two hex digits per
   * character. An odd-length name is the reference's own logged error and gets no reply at all;
   * every other name answers per-part, valid or not.
   */
  private fun applyTermcapQuery(body: String) {
    for (part in body.split(PART_SEPARATOR)) {
      if (part.length % 2 != 0) continue
      replies.reply(replyFor(part))
    }
  }

  /** One `+q` part: `DCS 1 + r <part>=<hex(value)> ST` if known, `DCS 0 + r <part> ST` if not. */
  private fun replyFor(part: String): String {
    val value = CAPABILITIES[hexDecode(part)] ?: return DEVICE_CONTROL_STRING + "0+r" + part + STRING_TERMINATOR
    return DEVICE_CONTROL_STRING + "1+r" + part + "=" + hexEncode(value) + STRING_TERMINATOR
  }

  /**
   * A chunk that fails to parse is **skipped rather than thrown**, matching the reference's own
   * `catch (NumberFormatException e) { ...; continue; }` - a shell that sends a non-hex pair asks
   * an unanswerable question, not one that should crash the terminal over.
   */
  private fun hexDecode(hex: String): String =
    hex.chunked(HEX_DIGITS_PER_CHAR).mapNotNull { it.toIntOrNull(HEX_RADIX)?.toChar() }.joinToString("")

  private fun hexEncode(text: String): String = text.map { "%02X".format(it.code) }.joinToString("")

  private companion object {
    const val TERMINATOR = '\\'.code
    const val PART_SEPARATOR = ';'

    /** A termcap/terminfo name is two hex digits per character, per the reference's own comment. */
    const val HEX_DIGITS_PER_CHAR = 2
    const val HEX_RADIX = 16

    const val DEVICE_CONTROL_STRING = "\u001BP"
    const val STRING_TERMINATOR = "\u001B\\"

    /** Upstream's `MAX_OSC_STRING_LENGTH`, shared by name only - each string state owns its own cap. */
    const val MAX_STRING_LENGTH = 8192

    const val REQUEST_STATUS_STRING = "\$q"
    const val DECSCL_REQUEST = "\"p"
    const val DECSCL_RESPONSE = "64;1\"p"

    const val TERMCAP_QUERY = "+q"

    /**
     * The termcap/terminfo names `DeviceControlStringTest` exercises. Every other name the
     * reference resolves through `KeyHandler.getCodeFromTermcap`, which needs an Android key
     * identity and the cursor/keypad application modes this package has no caller for until P4j -
     * `docs/DECISIONS.md`. `kB`, back-tab, is the one the reference's own test names, and it is a
     * fixed answer regardless of either mode.
     */
    val CAPABILITIES = mapOf(
      "Co" to "256",
      "colors" to "256",
      "TN" to "xterm",
      "name" to "xterm",
      "kB" to "\u001B[Z",
    )
  }
}
