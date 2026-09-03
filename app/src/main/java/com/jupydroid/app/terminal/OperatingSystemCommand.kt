package com.jupydroid.app.terminal

/**
 * `ESC ]`, the Operating System Command: a string collected until a terminator, then read as a
 * number and a text parameter.
 *
 * Transcribed from `TerminalEmulator.doOsc`, `doOscEsc`, `collectOSCArgs` and the header parse of
 * `doOscSetTextParameters` (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]). **The window
 * title is the only number acted on here**; the colours are [ColorCommands]', the clipboard is
 * [ClipboardCommand]'s, and a number none of them claims is dropped - the frame P4f-2 left for the
 * CSI final bytes.
 *
 * **Two terminators, and neither is a byte the emulator can dispatch blindly.** A `BEL` ends the
 * string here and rings the bell everywhere else, and an `ESC` is the first half of `ESC \` here
 * and the start of a new sequence everywhere else - so
 * [TerminalEmulator.handleControlCharacter] asks [EscapeParser.isCollectingString] before it
 * chooses. Both guards are the reference's, at its `case 7` and `case 27`.
 *
 * ```
 * val strings = OperatingSystemCommand(title)
 * strings.startString()                          // ESC ] arrived
 * "0;~/work".forEach { strings.collect(it.code) }
 * strings.collect(7)                             // BEL: the title is set
 * ```
 */
class OperatingSystemCommand(
  private val title: WindowTitle,
  private val colorCommands: ColorCommands,
  private val clipboardCommand: ClipboardCommand,
) {
  private val argument = StringBuilder()

  /** `ESC ]`: the buffer is emptied and the string starts, which is upstream's `setLength(0)`. */
  fun startString(): EscapeState {
    argument.setLength(0)
    return EscapeState.OperatingSystemCommand
  }

  /**
   * A code point inside the string. `BEL` ends it, `ESC` opens the two-byte terminator, and the
   * byte that would overflow the buffer **abandons the sequence** - which is why the `BEL` after an
   * over-long string rings instead of setting a title.
   */
  fun collect(codePoint: Int): EscapeState = when {
    codePoint == BELL -> applyAndFinish(BELL_TERMINATOR)
    codePoint == ESCAPE -> EscapeState.OperatingSystemCommandEscape
    collectArgument(codePoint) -> EscapeState.OperatingSystemCommand
    else -> EscapeState.Ground
  }

  /**
   * The code point after an `ESC` inside the string. A `\` ends it; **anything else puts the `ESC`
   * and that byte into the string and goes on collecting**, which is the reference's own comment -
   * "The ESC character was not followed by a \\". An overflow here does not abandon the sequence,
   * because upstream's `continueSequence(ESC_OSC)` runs after the append either way.
   */
  fun afterEscape(codePoint: Int): EscapeState {
    if (codePoint == STRING_TERMINATOR_FINAL) return applyAndFinish(STRING_TERMINATOR)
    collectArgument(ESCAPE)
    collectArgument(codePoint)
    return EscapeState.OperatingSystemCommand
  }

  /** Says whether there was room; at the cap the reference calls this an unknown sequence. */
  private fun collectArgument(codePoint: Int): Boolean {
    if (argument.length >= MAX_STRING_LENGTH) return false
    argument.appendCodePoint(codePoint)
    return true
  }

  /**
   * The string is over: the leading number, then the text parameter after the first `;`. A byte
   * before that `;` which is not a digit makes the whole command unknown, and so does an empty
   * string - which leaves the number at [NO_VALUE] and matches no arm.
   *
   * [terminator] is carried this far because a colour **query** echoes it back: `OSC 10 ; ? BEL` is
   * answered with a `BEL` and `OSC 10 ; ? ST` with an `ST`.
   */
  private fun applyAndFinish(terminator: String): EscapeState {
    val body = argument.toString()
    val separator = body.indexOf(PARAMETER_SEPARATOR)
    val digits = if (separator < 0) body else body.substring(0, separator)
    if (digits.any { it !in '0'..'9' }) return EscapeState.Ground
    val textParameter = if (separator < 0) "" else body.substring(separator + 1)
    apply(leadingValue(digits), textParameter, terminator)
    return EscapeState.Ground
  }

  /**
   * The title's numbers, then the colours', then the clipboard's; a number none of them claims is
   * logged upstream and lost.
   */
  private fun apply(value: Int, textParameter: String, terminator: String) {
    when {
      value in SET_TITLE_CODES -> title.set(textParameter)
      colorCommands.apply(value, textParameter, terminator) -> Unit
      value == CLIPBOARD -> clipboardCommand.apply(textParameter)
      else -> Unit
    }
  }

  /**
   * Upstream's digit fold, kept rather than `toIntOrNull` so that a number too long for an `Int`
   * wraps the way the reference's `value * 10 + digit` wraps. An empty run leaves [NO_VALUE].
   */
  private fun leadingValue(digits: String): Int {
    var value = NO_VALUE
    for (digit in digits) value = (if (value < 0) 0 else value * DECIMAL) + (digit - '0')
    return value
  }

  private companion object {
    const val BELL = 7
    const val ESCAPE = 27
    const val STRING_TERMINATOR_FINAL = '\\'.code
    const val PARAMETER_SEPARATOR = ';'

    /** What a query echoes back: the terminator the request itself used. */
    const val BELL_TERMINATOR = "\u0007"
    const val STRING_TERMINATOR = "\u001B\\"

    /** Upstream's `MAX_OSC_STRING_LENGTH`, and its reason: an `OSC 52` paste has to fit. */
    const val MAX_STRING_LENGTH = 8192

    /** No digits at all, which upstream reaches with `value` still at its initial `-1`. */
    const val NO_VALUE = -1

    /** The radix the digit fold shifts by, which upstream writes inline as `value * 10`. */
    const val DECIMAL = 10

    /** `0` sets the icon name and the window title, `1` the icon name, `2` the window title. */
    val SET_TITLE_CODES = 0..2

    /** `OSC 52`, Manipulate Selection Data. */
    const val CLIPBOARD = 52
  }
}
