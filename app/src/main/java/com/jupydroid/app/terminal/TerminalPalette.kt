package com.jupydroid.app.terminal

/**
 * The 259 colours a terminal starts with and resets to: the sixteen ANSI entries, the 216-colour
 * cube, the 24 greys, and the three the shell calls foreground, background and cursor.
 *
 * Transcribed from `TerminalColorScheme.DEFAULT_COLORSCHEME` (termux-app `3df69d1d`, Apache 2.0 -
 * see [TerminalGrid]), with **two departures**.
 *
 * **It is a value, not a process-global static.** Upstream `TerminalColors.COLOR_SCHEME` is one
 * mutable array shared by every emulator in the process, which `updateWith(Properties)` rewrites
 * from a theme file. Here the palette is a constructor argument, because this app's ANSI ramp is
 * **theme-dependent** - VS Code registers `terminal.ansiGreen` as `#107C10` light and `#0DBC79`
 * dark - and `terminal/` may not reach into `theme/` to ask which. **P4g** resolves that ramp and
 * hands it in; [REFERENCE] is what the differential compares against and what a caller gets until
 * then.
 *
 * **The cube and the greys are generated, not transcribed.** They are arithmetic - six levels per
 * channel and an even ramp - and 240 literals would be 240 chances to mistype one. [CUBE_LEVELS]
 * and the grey step are checked against the reference's own table at its corners and seams in
 * `TerminalColorsTest`. That is the call P4e-1 made for the East Asian width ranges.
 *
 * ```
 * TerminalPalette.REFERENCE.at(1)   // 0xFFCD0000, the reference's dim red
 * ```
 */
class TerminalPalette(private val defaults: IntArray) {
  init {
    require(defaults.size == CellStyle.INDEXED_COLOR_COUNT) {
      "a palette is ${CellStyle.INDEXED_COLOR_COUNT} colours, was ${defaults.size}"
    }
  }

  /** The default colour at [index], as opaque `0xFFRRGGBB`. */
  fun at(index: Int): Int = defaults[index]

  /** Fill [target] with these defaults; it must be the same length. */
  fun copyInto(target: IntArray) {
    defaults.copyInto(target)
  }

  companion object {
    /** The six levels each channel of the 216-colour cube takes, in order. */
    private val CUBE_LEVELS = intArrayOf(0, 0x5F, 0x87, 0xAF, 0xD7, 0xFF)

    /**
     * The sixteen ANSI entries, dim then bright. The only ones that are literals: they are a
     * palette choice rather than arithmetic, and they are what P4g replaces.
     */
    private val ANSI = intArrayOf(
      0xFF000000.toInt(), 0xFFCD0000.toInt(), 0xFF00CD00.toInt(), 0xFFCDCD00.toInt(),
      0xFF6495ED.toInt(), 0xFFCD00CD.toInt(), 0xFF00CDCD.toInt(), 0xFFE5E5E5.toInt(),
      0xFF7F7F7F.toInt(), 0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFFFFFF00.toInt(),
      0xFF5C5CFF.toInt(), 0xFFFF00FF.toInt(), 0xFF00FFFF.toInt(), 0xFFFFFFFF.toInt(),
    )

    private const val CUBE_FIRST = 16
    private const val GREY_FIRST = 232
    private const val GREY_COUNT = 24
    private const val GREY_START = 8
    private const val GREY_STEP = 10
    private const val OPAQUE = 0xFF shl 24
    private const val RED_SHIFT = 16
    private const val GREEN_SHIFT = 8

    private fun referenceDefaults(): IntArray {
      val colors = IntArray(CellStyle.INDEXED_COLOR_COUNT)
      ANSI.copyInto(colors)
      for (level in 0 until CUBE_LEVELS.size * CUBE_LEVELS.size * CUBE_LEVELS.size) {
        colors[CUBE_FIRST + level] = cubeEntry(level)
      }
      for (step in 0 until GREY_COUNT) colors[GREY_FIRST + step] = grey(GREY_START + GREY_STEP * step)
      colors[CellStyle.COLOR_INDEX_FOREGROUND] = 0xFFFFFFFF.toInt()
      colors[CellStyle.COLOR_INDEX_BACKGROUND] = OPAQUE
      colors[CellStyle.COLOR_INDEX_CURSOR] = 0xFFFFFFFF.toInt()
      return colors
    }

    /** Red is the outermost channel, so entry 22 is `(0, 1, 0)` and entry 21 is `(0, 0, 5)`. */
    private fun cubeEntry(level: Int): Int {
      val sides = CUBE_LEVELS.size
      val red = CUBE_LEVELS[level / (sides * sides)]
      val green = CUBE_LEVELS[(level / sides) % sides]
      val blue = CUBE_LEVELS[level % sides]
      return OPAQUE or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue
    }

    private fun grey(value: Int): Int = OPAQUE or (value shl RED_SHIFT) or (value shl GREEN_SHIFT) or value

    /**
     * Termux's own scheme - *"the xterm 256-colour chart, but with blue brighter"*. The
     * differential compares against this, so it must stay byte-for-byte the reference's.
     *
     * **Declared after everything it reads.** A companion's properties initialise in source order,
     * so a `REFERENCE` written at the top of the block runs `referenceDefaults()` while [ANSI] and
     * [CUBE_LEVELS] are still null - which is an `ExceptionInInitializerError` on first touch, not
     * a compile error.
     */
    val REFERENCE: TerminalPalette = TerminalPalette(referenceDefaults())
  }
}
