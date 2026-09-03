package com.jupydroid.app.terminal

/**
 * The `OSC` numbers that set, query and reset a colour: `4` and `104` for the indexed table,
 * `10`/`11`/`12` and `110`/`111`/`112` for the foreground, background and cursor, and `119`, which
 * resets a highlight colour this terminal does not have.
 *
 * Transcribed from the `4`, `10`-`12`, `104`, `110`-`112` and `119` arms of
 * `TerminalEmulator.doOscSetTextParameters` (termux-app `3df69d1d`, Apache 2.0 - see
 * [TerminalGrid]). A class of its own because that method is 143 lines upstream and this is the
 * larger half of it.
 *
 * **`4` has no query and `10`/`11`/`12` do.** xterm answers `OSC 4 ; n ; ?` and this reference does
 * not - the `?` falls into the colour spec, fails to parse and leaves the entry alone. Transcribed
 * as it stands, and measured rather than read.
 *
 * **`4` guards its index to `0..255` and `104` guards nothing.** The two arms disagree about the
 * range on purpose enough to be worth stating: `104` will reset the cursor colour at 258 and `4`
 * will not set it. What `104` does *not* survive is an index outside the table at all - see
 * [resetIndexed].
 *
 * ```
 * ColorCommands(colors, replies).apply(4, "5;#00FF00", "\u0007")   // index 5 is now green
 * ```
 */
class ColorCommands(private val colors: TerminalColors, private val replies: TerminalReplies) {
  /** Says whether [value] was one of these numbers; [terminator] is echoed back by a query. */
  fun apply(value: Int, textParameter: String, terminator: String): Boolean {
    when (value) {
      SET_INDEXED -> setIndexed(textParameter)
      in SET_DYNAMIC -> setDynamic(value, textParameter, terminator)
      RESET_INDEXED -> resetIndexed(textParameter)
      in RESET_DYNAMIC -> colors.reset(CellStyle.COLOR_INDEX_FOREGROUND + value - RESET_DYNAMIC.first)
      RESET_HIGHLIGHT -> Unit
      else -> return false
    }
    return true
  }

  /**
   * `OSC 4 ; c ; spec [; c ; spec]…`. A token that is not an index in `0..255` **abandons the whole
   * command**, pairs already applied and all, which is upstream's `unknownSequence` and `return`.
   * A trailing index with no spec after it does nothing.
   */
  private fun setIndexed(textParameter: String) {
    val tokens = textParameter.split(SEPARATOR)
    var pair = 0
    while (pair + 1 < tokens.size) {
      val index = indexedOrNull(tokens[pair]) ?: return
      colors.tryParse(index, tokens[pair + 1])
      pair += 2
    }
  }

  /** Digits only, and `0..255`: `+5` is an index to `Integer.parseInt` and not to the reference. */
  private fun indexedOrNull(token: String): Int? {
    if (token.isEmpty() || token.any { it !in '0'..'9' }) return null
    return token.toIntOrNull()?.takeIf { it <= LAST_INDEXED }
  }

  /**
   * `OSC 10`/`11`/`12`. **Each successive spec changes the next colour in the list**, so
   * `OSC 10 ; a ; b ; c` sets the foreground, the background and the cursor; the walk stops at the
   * cursor or when the specs run out. A `?` asks instead of setting.
   *
   * The one empty token a **trailing** `;` leaves is dropped, because upstream's `++charIndex >=
   * length` breaks before reading it - while an *interior* empty token is read and fails to parse,
   * which is a report either way.
   */
  private fun setDynamic(value: Int, textParameter: String, terminator: String) {
    val tokens = textParameter.split(SEPARATOR).toMutableList()
    if (tokens.size > 1 && tokens.last().isEmpty()) tokens.removeAt(tokens.lastIndex)
    var index = CellStyle.COLOR_INDEX_FOREGROUND + value - SET_DYNAMIC.first
    for (spec in tokens) {
      if (index > CellStyle.COLOR_INDEX_CURSOR) return
      if (spec == QUERY) reportColor(value, index, terminator) else colors.tryParse(index, spec)
      index++
    }
  }

  /**
   * `OSC 104 [; c]…`, and **the one place this diverges from the reference to avoid a crash it
   * cannot survive.** Upstream puts the end-of-input `break` inside a `try` that swallows
   * `NumberFormatException`, so a token that is not a number - or a `;` in the last position -
   * walks past the end of the string and throws; and it hands the index to an unchecked array
   * write, so anything outside the table throws too. Both are reachable from ordinary shell
   * output. What happens here is the intent that `catch`'s own comment states - `// Ignore.` -
   * applied per token. `evidence/p4-terminal/the-colour-reset-that-walks-off-its-own-string.md`.
   */
  private fun resetIndexed(textParameter: String) {
    if (textParameter.isEmpty()) {
      colors.reset()
      return
    }
    for (token in textParameter.split(SEPARATOR)) {
      val index = token.toIntOrNull() ?: continue
      if (index in 0 until CellStyle.INDEXED_COLOR_COUNT) colors.reset(index)
    }
  }

  /**
   * The answer both xterm and libvte give: the **longest** representation, `rgb:RRRR/GGGG/BBBB`,
   * each eight-bit channel widened to sixteen by `65535 * c / 255`. Terminated the way the request
   * was, which is what a script reading a background colour out of `xterm` relies on.
   *
   * **[number] is the number the shell asked with, not the one the walking index would name.**
   * Measured: `OSC 10 ; ? ; ?` answers `]10;…` **twice**, the second carrying the *background*
   * colour, because upstream interpolates `value` and increments `specialIndex` separately.
   */
  private fun reportColor(number: Int, index: Int, terminator: String) {
    val rgb = colors.at(index)
    val red = wide(rgb shr RED_SHIFT)
    val green = wide(rgb shr GREEN_SHIFT)
    val blue = wide(rgb)
    replies.reply("$OSC$number;rgb:$red/$green/$blue$terminator")
  }

  private companion object {
    const val OSC = "\u001B]"
    const val SEPARATOR = ';'
    const val QUERY = "?"

    const val SET_INDEXED = 4
    const val RESET_INDEXED = 104
    const val RESET_HIGHLIGHT = 119

    /** `10` foreground, `11` background, `12` cursor - and `110`/`111`/`112` to put them back. */
    val SET_DYNAMIC = 10..12
    val RESET_DYNAMIC = 110..112

    /** `OSC 4` reaches the 256 indexed entries and not the three the shell names by number. */
    const val LAST_INDEXED = 255

    const val RED_SHIFT = 16
    const val GREEN_SHIFT = 8
    const val CHANNEL_MASK = 0xFF
    const val SIXTEEN_BIT_MAX = 65535
    const val EIGHT_BIT_MAX = 255

    /** One channel widened to sixteen bits, printed as four lower-case hex digits. */
    fun wide(channel: Int): String = "%04x".format(SIXTEEN_BIT_MAX * (channel and CHANNEL_MASK) / EIGHT_BIT_MAX)
  }
}
