package com.jupydroid.app.terminal

/**
 * The colours in force: a [TerminalPalette]'s defaults until a shell overrides one with `OSC 4`,
 * `OSC 10`, `OSC 11` or `OSC 12`, and back to them on `OSC 104`, `OSC 110`-`112` or a terminal
 * reset.
 *
 * Transcribed from `TerminalColors.java` (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]).
 * Three things are not carried over. `COLOR_SCHEME` is a **process-global static** upstream and is
 * a constructor parameter here - see [TerminalPalette]. `updateWith(Properties)` reads Termux's own
 * theme file, which nothing here has. And `getPerceivedBrightnessOfColor` needs
 * `android.graphics.Color`, which `terminal/` may not import and which only `updateWith` called.
 *
 * **Every attempted write reports, including one that parsed nothing.** The reference calls
 * `onColorsChanged` beside `tryParseColor` rather than inside it and never asks whether it worked,
 * so `ESC ] 4 ; 5 ; ? BEL` leaves the table alone and still reports. Measured, not read.
 *
 * ```
 * val colors = TerminalColors(TerminalPalette.REFERENCE, notices)
 * colors.tryParse(5, "#00FF00")   // index 5 is now 0xFF00FF00
 * colors.reset(5)                 // and back to the palette's
 * ```
 */
class TerminalColors(private var palette: TerminalPalette, private val notices: TerminalNotices) {
  private val current = IntArray(CellStyle.INDEXED_COLOR_COUNT).also(palette::copyInto)

  /** The colour in force at [index], as opaque `0xFFRRGGBB`. */
  fun at(index: Int): Int = current[index]

  /** Put every colour back to the palette's. */
  fun reset() {
    palette.copyInto(current)
    notices.onColorsChanged()
  }

  /**
   * Make [palette] the one every reset returns to, and put every colour back to it - so a colour a
   * program set by `OSC` is dropped with the palette it was set against. Both references do the
   * same: termux-app `3df69d1d` `TermuxTerminalSessionActivityClient.checkForFontAndColors` calls
   * `mColors.reset()` after `updateWith`, and xterm.js 5.5.0 `ThemeService._setTheme` rebuilds the
   * ANSI table from the theme. The palette already in force is a no-op, so a relaunch that
   * changed no theme keeps the shell's colours.
   */
  fun adopt(palette: TerminalPalette) {
    if (palette === this.palette) return
    this.palette = palette
    reset()
  }

  /** Put one colour back to the palette's. */
  fun reset(index: Int) {
    current[index] = palette.at(index)
    notices.onColorsChanged()
  }

  /**
   * Read [spec] as a colour and store it at [index]; a spec that names none **leaves the entry
   * alone**, which is upstream's `if (c != 0)`.
   */
  fun tryParse(index: Int, spec: String) {
    val color = parse(spec)
    if (color != PARSE_FAILED) current[index] = color
    notices.onColorsChanged()
  }

  companion object {
    /** What [parse] answers for a spec it cannot read, and what upstream tests `!= 0` against. */
    const val PARSE_FAILED = 0

    /**
     * XParseColor's two numeric forms: `#RGB` through `#RRRRGGGGBBBB`, whose digits are the *most
     * significant* bits of each channel, and `rgb:<r>/<g>/<b>`, which is scaled. Anything else -
     * a colour *name*, which the reference does not implement either - is [PARSE_FAILED].
     *
     * **The reference reaches this answer by exception where this reaches it by guard**, and the
     * two agree because every guard here is a case it would have thrown on: an empty spec
     * (`charAt(0)`), a component width of zero (`parseInt("")`) and a component too wide for an
     * `Int`. `TerminalColorsTest.aSpecThatNamesNoColourIsRefused` pins each.
     */
    fun parse(spec: String): Int {
      val (skipInitial, skipBetween) = when {
        spec.startsWith(HEX_PREFIX) -> HEX_PREFIX.length to 0
        spec.startsWith(RGB_PREFIX) -> RGB_PREFIX.length to 1
        else -> return PARSE_FAILED
      }
      val digits = spec.length - skipInitial - 2 * skipBetween
      return if (digits <= 0 || digits % CHANNELS != 0) {
        PARSE_FAILED
      } else {
        channels(spec, skipInitial, skipBetween, digits / CHANNELS)
      }
    }

    /**
     * The three components at [width] hex digits each, scaled to eight bits: one digit is
     * multiplied by seventeen, two by one, and four by `255/65535`. The division is `Double` as
     * upstream's is, and the cast truncates as upstream's does.
     */
    private fun channels(spec: String, skipInitial: Int, skipBetween: Int, width: Int): Int {
      val scale = MAX_CHANNEL / (Math.pow(2.0, (width * BITS_PER_HEX_DIGIT).toDouble()) - 1)
      var color = OPAQUE
      var position = skipInitial
      repeat(CHANNELS) {
        val value = spec.substring(position, position + width).toIntOrNull(HEX) ?: return PARSE_FAILED
        color = color or ((value * scale).toInt() shl (BITS_PER_CHANNEL * (CHANNELS - 1 - it)))
        position += width + skipBetween
      }
      return color
    }

    private const val HEX_PREFIX = "#"
    private const val RGB_PREFIX = "rgb:"
    private const val CHANNELS = 3
    private const val BITS_PER_CHANNEL = 8
    private const val BITS_PER_HEX_DIGIT = 4
    private const val MAX_CHANNEL = 255.0
    private const val HEX = 16
    private const val OPAQUE = 0xFF shl 24
  }
}
