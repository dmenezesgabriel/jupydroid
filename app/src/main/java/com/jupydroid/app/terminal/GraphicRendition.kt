package com.jupydroid.app.terminal

/**
 * Select Graphic Rendition - `ESC [ Pm m` - the sequence that says what the *next* characters are
 * painted with.
 *
 * Transcribed from `TerminalEmulator.selectGraphicRendition`
 * (`terminal-emulator/src/main/java/com/termux/terminal/TerminalEmulator.java`, termux-app
 * `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 under that repository's `LICENSE.md`
 * exception).
 *
 * **Pure, where the reference mutates three emulator fields.** Upstream `mForeColor`,
 * `mBackColor` and `mEffect` are re-packed into a style by `getStyle()` at every write; here the
 * style *is* the state, so SGR is a function of it and the parameters, and needs no terminal to
 * test.
 *
 * ```
 * GraphicRendition.applied(parameters, CellStyle.NORMAL)   // after "31;1"
 * ```
 */
object GraphicRendition {
  /** [style] after every parameter in the sequence has been applied to it, in order. */
  fun applied(parameters: EscapeParameters, style: CellStyle): CellStyle {
    val walk = RenditionWalk(parameters, style)
    walk.run()
    return walk.style
  }
}

/**
 * One pass over a sequence's parameters. A class because three of them - the extended colours -
 * consume the parameters *after* their own, so the walk's position is state rather than a loop
 * variable.
 */
private class RenditionWalk(private val parameters: EscapeParameters, style: CellStyle) {
  private var foreColor = style.foreColor
  private var backColor = style.backColor
  private var effect = style.effect
  private var index = 0

  val style: CellStyle get() = CellStyle.encode(foreColor, backColor, effect)

  /**
   * A sub-parameter the walk has not itself consumed is skipped: it belongs to the code before it,
   * and that code either read it or did not want it.
   */
  fun run() {
    while (index < parameters.count) {
      if (!parameters.isSubParameter(index)) apply(parameters.at(index, RESET, treatZeroAsDefault = false))
      index++
    }
  }

  private fun apply(code: Int) = when (code) {
    RESET -> reset()
    UNDERLINE_ON -> setUnderline()
    EXTENDED_FOREGROUND, EXTENDED_BACKGROUND, EXTENDED_UNDERLINE -> readExtendedColour(code)
    DEFAULT_FOREGROUND -> foreColor = CellStyle.COLOR_INDEX_FOREGROUND
    DEFAULT_BACKGROUND -> backColor = CellStyle.COLOR_INDEX_BACKGROUND
    in FOREGROUND -> foreColor = code - FOREGROUND.first
    in BACKGROUND -> backColor = code - BACKGROUND.first
    in BRIGHT_FOREGROUND -> foreColor = code - BRIGHT_FOREGROUND.first + FIRST_BRIGHT_INDEX
    in BRIGHT_BACKGROUND -> backColor = code - BRIGHT_BACKGROUND.first + FIRST_BRIGHT_INDEX
    else -> effect = effectAfter(code)
  }

  private fun reset() {
    foreColor = CellStyle.COLOR_INDEX_FOREGROUND
    backColor = CellStyle.COLOR_INDEX_BACKGROUND
    effect = 0
  }

  /**
   * Everything that only turns an attribute on or off. `10` and `11` select an alternate charset
   * on `TERM=linux` and fall through to the `else`, ignored as the reference ignores them.
   */
  private fun effectAfter(code: Int): Int = when (code) {
    BOLD -> effect or CellStyle.BOLD
    DIM -> effect or CellStyle.DIM
    ITALIC -> effect or CellStyle.ITALIC
    BLINK -> effect or CellStyle.BLINK
    INVERSE -> effect or CellStyle.INVERSE
    INVISIBLE -> effect or CellStyle.INVISIBLE
    STRIKETHROUGH -> effect or CellStyle.STRIKETHROUGH
    NORMAL_INTENSITY -> effect and (CellStyle.BOLD or CellStyle.DIM).inv()
    NOT_ITALIC -> effect and CellStyle.ITALIC.inv()
    NOT_UNDERLINED -> effect and CellStyle.UNDERLINE.inv()
    NOT_BLINKING -> effect and CellStyle.BLINK.inv()
    NOT_INVERSE -> effect and CellStyle.INVERSE.inv()
    NOT_INVISIBLE -> effect and CellStyle.INVISIBLE.inv()
    NOT_STRIKETHROUGH -> effect and CellStyle.STRIKETHROUGH.inv()
    else -> effect
  }

  /**
   * `4`, optionally with kitty's sub-parameter (https://sw.kovidgoyal.net/kitty/underlines/):
   * `4:0` is no underline and `4:1`..`4:5` are five styles of one, all of which this - like the
   * reference - draws as the single underline [CellStyle] has a bit for.
   */
  private fun setUnderline() {
    val variant = index + 1
    if (variant >= parameters.count || !parameters.isSubParameter(variant)) {
      effect = effect or CellStyle.UNDERLINE
      return
    }
    index = variant
    val off = parameters.at(variant, ABSENT, treatZeroAsDefault = false) == 0
    effect = if (off) effect and CellStyle.UNDERLINE.inv() else effect or CellStyle.UNDERLINE
  }

  /**
   * ISO-8613-3: `38`/`48`/`58` are followed by `2;R;G;B` for a 24-bit colour or `5;index` for one
   * of the 256. **A form that is neither sets nothing and does not consume its arguments**, so the
   * walk reads them again as codes of their own - the reference's behaviour, and the reason
   * `48;8;129` ends up invisible.
   */
  private fun readExtendedColour(code: Int) {
    if (index + FORM_OFFSET >= parameters.count) return
    when (parameters.at(index + 1, ABSENT, treatZeroAsDefault = false)) {
      TRUECOLOUR_FORM -> readTruecolour(code)
      INDEXED_FORM -> readIndexedColour(code)
      else -> Unit
    }
  }

  /** `2;R;G;B`. The three channels are consumed whether or not they are in range. */
  private fun readTruecolour(code: Int) {
    if (index + TRUECOLOUR_ARGUMENTS >= parameters.count) return
    val red = parameters.at(index + 2, 0, treatZeroAsDefault = false)
    val green = parameters.at(index + 3, 0, treatZeroAsDefault = false)
    val blue = parameters.at(index + 4, 0, treatZeroAsDefault = false)
    index += TRUECOLOUR_ARGUMENTS
    if (red !in CHANNEL || green !in CHANNEL || blue !in CHANNEL) return
    assign(code, OPAQUE_ALPHA or (red shl RED_SHIFT) or (green shl GREEN_SHIFT) or blue)
  }

  /** `5;index`, into the 256 colours plus the three the terminal names for itself. */
  private fun readIndexedColour(code: Int) {
    val colour = parameters.at(index + FORM_OFFSET, 0, treatZeroAsDefault = false)
    index += FORM_OFFSET
    if (colour in 0 until CellStyle.INDEXED_COLOR_COUNT) assign(code, colour)
  }

  /**
   * `58` names an **underline colour**, which is parsed and then dropped: the reference keeps it in
   * `mUnderlineColor`, a field its own `getStyle()` does not encode and nothing in
   * `terminal-emulator/` or `terminal-view/` ever reads. Parsing it is still load-bearing - see
   * [readExtendedColour].
   */
  private fun assign(code: Int, colour: Int) = when (code) {
    EXTENDED_FOREGROUND -> foreColor = colour
    EXTENDED_BACKGROUND -> backColor = colour
    else -> Unit
  }

  private companion object {
    const val RESET = 0
    const val BOLD = 1
    const val DIM = 2
    const val ITALIC = 3
    const val UNDERLINE_ON = 4
    const val BLINK = 5
    const val INVERSE = 7
    const val INVISIBLE = 8
    const val STRIKETHROUGH = 9
    const val NORMAL_INTENSITY = 22
    const val NOT_ITALIC = 23
    const val NOT_UNDERLINED = 24
    const val NOT_BLINKING = 25
    const val NOT_INVERSE = 27
    const val NOT_INVISIBLE = 28
    const val NOT_STRIKETHROUGH = 29
    const val EXTENDED_FOREGROUND = 38
    const val DEFAULT_FOREGROUND = 39
    const val EXTENDED_BACKGROUND = 48
    const val DEFAULT_BACKGROUND = 49
    const val EXTENDED_UNDERLINE = 58

    val FOREGROUND = 30..37
    val BACKGROUND = 40..47
    val BRIGHT_FOREGROUND = 90..97
    val BRIGHT_BACKGROUND = 100..107

    /** The eight bright colours are the eight standard ones offset by eight. */
    const val FIRST_BRIGHT_INDEX = 8

    /** A parameter nobody wrote a digit into, as [EscapeParameters] reports it. */
    const val ABSENT = -1

    const val TRUECOLOUR_FORM = 2
    const val INDEXED_FORM = 5

    /** `5;index` consumes two parameters past the code; the form itself sits at the first. */
    const val FORM_OFFSET = 2

    /** `2;R;G;B` consumes four. */
    const val TRUECOLOUR_ARGUMENTS = 4

    val CHANNEL = 0..255
    const val RED_SHIFT = 16
    const val GREEN_SHIFT = 8

    /** What marks a colour as 24-bit rather than an index, in [CellStyle]'s packing. */
    const val OPAQUE_ALPHA = 0xFF shl 24
  }
}
