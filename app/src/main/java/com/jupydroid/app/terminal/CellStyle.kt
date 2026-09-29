package com.jupydroid.app.terminal

/**
 * One cell's effects, foreground and background packed into a single `Long`.
 *
 * Transcribed from `terminal-emulator/src/main/java/com/termux/terminal/TextStyle.java`
 * (`termux-app@3df69d1d`, commit `3df69d1da197dd9bd71a3bafd902dffd720576b4`,
 * Apache 2.0 under that repository's `LICENSE.md` exception).
 *
 * **Named `CellStyle`, not `TextStyle`:** `TextStyle` already resolves to Compose's in eighteen
 * files here.
 *
 * **The packing is the reference's and is kept for the reason it exists**: a row of cells is one
 * `LongArray`, not one object per cell, and a screen is hundreds of rows redrawn per frame. The
 * value class buys that back as a type without allocating - [bits] is what [TerminalRow] stores.
 *
 * Layout: 9 effect bits, 2 truecolor markers, then 24 bits of background at bit 16 and 24 bits of
 * foreground at bit 40. A colour is either an index into the 259-entry palette or, when its alpha
 * byte is opaque, a literal RGB.
 *
 * ```
 * CellStyle.encode(CellStyle.COLOR_INDEX_FOREGROUND, 4, CellStyle.BOLD).effect  // CellStyle.BOLD
 * ```
 */
@JvmInline
value class CellStyle(val bits: Long) {
  /** The palette index, or an opaque ARGB colour when the truecolor marker is set. */
  val foreColor: Int get() = colorAt(FOREGROUND_SHIFT, TRUECOLOR_FOREGROUND)

  /** The palette index, or an opaque ARGB colour when the truecolor marker is set. */
  val backColor: Int get() = colorAt(BACKGROUND_SHIFT, TRUECOLOR_BACKGROUND)

  /** The set attribute bits: [BOLD], [ITALIC] and the rest, or'd together. */
  val effect: Int get() = (bits and EFFECT_MASK).toInt()

  private fun colorAt(shift: Int, truecolorMarker: Long): Int {
    val field = (bits ushr shift)
    if (bits and truecolorMarker == 0L) return (field and INDEXED_COLOR_MASK).toInt()
    return OPAQUE_ALPHA or (field and RGB_MASK).toInt()
  }

  companion object {
    const val BOLD = 1
    const val ITALIC = 1 shl 1
    const val UNDERLINE = 1 shl 2
    const val BLINK = 1 shl 3
    const val INVERSE = 1 shl 4
    const val INVISIBLE = 1 shl 5
    const val STRIKETHROUGH = 1 shl 6

    /**
     * Set by DECSCA on the characters that follow it, marking them erasable by the *selective*
     * erases (DECSED, DECSEL) and only by those.
     */
    const val PROTECTED = 1 shl 7

    /** Dim, also known as faint or half intensity. */
    const val DIM = 1 shl 8

    /** The palette entry meaning "the terminal's own foreground", rather than one of the 256. */
    const val COLOR_INDEX_FOREGROUND = 256
    const val COLOR_INDEX_BACKGROUND = 257
    const val COLOR_INDEX_CURSOR = 258

    /** The 256 standard entries plus the three special ones above. */
    const val INDEXED_COLOR_COUNT = 259

    /** What a blank cell is cleared to: the terminal's own colours, no effects. */
    val NORMAL: CellStyle = encode(COLOR_INDEX_FOREGROUND, COLOR_INDEX_BACKGROUND, 0)

    fun encode(foreColor: Int, backColor: Int, effect: Int): CellStyle = CellStyle(
      (effect.toLong() and EFFECT_MASK) or
        colorBits(foreColor, FOREGROUND_SHIFT, TRUECOLOR_FOREGROUND) or
        colorBits(backColor, BACKGROUND_SHIFT, TRUECOLOR_BACKGROUND),
    )

    /**
     * Written once for both colours where the reference writes it twice: the two branches differ
     * only in which shift and which marker they use.
     */
    private fun colorBits(color: Int, shift: Int, truecolorMarker: Long): Long {
      if (color and OPAQUE_ALPHA != OPAQUE_ALPHA) return (color.toLong() and INDEXED_COLOR_MASK) shl shift
      return truecolorMarker or ((color.toLong() and RGB_MASK) shl shift)
    }

    /** Nine bits, [BOLD] through [DIM] - the width `encode` masks the effect to. */
    private const val EFFECT_MASK = 0b1_1111_1111L

    private const val TRUECOLOR_FOREGROUND = 1L shl 9
    private const val TRUECOLOR_BACKGROUND = 1L shl 10

    private const val BACKGROUND_SHIFT = 16
    private const val FOREGROUND_SHIFT = 40

    /** Nine bits, enough for all [INDEXED_COLOR_COUNT] palette entries. */
    private const val INDEXED_COLOR_MASK = 0b1_1111_1111L

    private const val RGB_MASK = 0x00FF_FFFFL

    /** An opaque alpha byte is what marks a colour as 24-bit rather than an index. */
    private const val OPAQUE_ALPHA = 0xFF shl 24
  }
}
