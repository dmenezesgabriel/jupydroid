package com.jupydroid.app.terminal

/**
 * DECCARA (`CSI Pt;Pl;Pb;Pr;Ps... $ r`) and DECRARA (`$ t`): set, clear or flip character
 * attributes over a region, keeping every cell's colours and what is drawn there.
 *
 * Transcribed from `ESC_CSI_DOLLAR`'s `r`/`t` arm (termux-app `3df69d1d`, Apache 2.0 - see
 * [TerminalGrid]). Split from [RectangularAreas] because the two share no arithmetic: this arm
 * computes its edges a **third** way, `min(arg + 1, margin - 1)`, where the fill and erase group
 * clamps to `margin + 1` and the copy subtracts one. Three conventions in one `$` arm, transcribed
 * rather than regularised.
 *
 * **The region is a stream, not a rectangle, unless DECSACE says otherwise.** With
 * [TerminalScreen.rectangularAttributeChange] off, only the first row starts at the left edge and
 * only the last stops at the right one; the rows between run the full margins, the way selected
 * text does. `CSI 2 * x` turns it into a true rectangle.
 *
 * **A reverse ignores the clearing codes.** `22`, `24`, `25` and `27` mean "no bold", "no
 * underline" and so on, and flipping a bit that is being asked to clear has no meaning - so
 * DECRARA drops them where DECCARA honours them.
 *
 * ```
 * RectangularAttributes(screen).apply('r'.code, parameters)   // DECCARA: bold a region
 * ```
 */
class RectangularAttributes(private val screen: TerminalScreen) {
  /** Act on [finalByte] if it is DECCARA or DECRARA, and say whether it was. */
  fun apply(finalByte: Int, parameters: EscapeParameters): Boolean {
    if (finalByte != CHANGE && finalByte != REVERSE) return false
    // With no attribute codes the loop below is empty, which is the reference's "do nothing" arm.
    val reverse = finalByte == REVERSE
    for (index in ATTRIBUTES_FROM until parameters.count) {
      applyOneAttribute(parameters.at(index, 0, treatZeroAsDefault = false), reverse, parameters)
    }
    return true
  }

  /** One attribute code. A code nothing claims carries no bits, so it writes the region unchanged. */
  private fun applyOneAttribute(code: Int, reverse: Boolean, parameters: EscapeParameters) {
    val bits = BITS_FOR[code] ?: 0
    val setOrClear = code !in CLEARING || reverse && code == ALL_OFF
    // Flipping a bit that is being asked to clear has no meaning, so a reverse drops those codes.
    if (reverse && !setOrClear) return
    writeRegion(bits, setOrClear, reverse, parameters)
  }

  /**
   * The four edges, and the margins the stream form runs between. The reference notes in a `FIXME`
   * that DECCARA is specified to respect origin mode and its coordinates do not - only the margins
   * it clamps to are conditioned on the mode. Transcribed with the defect, which the differential
   * would otherwise report as ours.
   */
  private fun writeRegion(bits: Int, setOrClear: Boolean, reverse: Boolean, parameters: EscapeParameters) {
    val origin = screen.modes.isSet(DecMode.ORIGIN)
    val cursor = screen.cursor
    val topMargin = if (origin) cursor.topMargin else 0
    val leftMargin = if (origin) cursor.leftMargin else 0
    val bottomMargin = if (origin) cursor.bottomMargin else screen.rows
    val rightMargin = if (origin) cursor.rightMargin else screen.columns
    val top = minOf(parameters.at(TOP, 1, treatZeroAsDefault = true) - 1, bottomMargin) + topMargin
    val left = minOf(parameters.at(LEFT, 1, treatZeroAsDefault = true) - 1, rightMargin) + leftMargin
    val bottom = minOf(parameters.at(BOTTOM, screen.rows, treatZeroAsDefault = true) + 1, bottomMargin - 1) + topMargin
    val right = minOf(parameters.at(RIGHT, screen.columns, treatZeroAsDefault = true) + 1, rightMargin - 1) + leftMargin
    screen.grid.setOrClearEffect(
      bits, setOrClear, reverse, screen.rectangularAttributeChange,
      leftMargin, rightMargin, top, left, bottom, right,
    )
  }

  private companion object {
    /** DECCARA and DECRARA. */
    const val CHANGE = 'r'.code
    const val REVERSE = 't'.code

    /** The four edges, then the attribute codes - of which there may be none, and then nothing happens. */
    const val TOP = 0
    const val LEFT = 1
    const val BOTTOM = 2
    const val RIGHT = 3
    const val ATTRIBUTES_FROM = 4

    /** `0` is "attributes off", which sets those bits under a reverse and clears them otherwise. */
    const val ALL_OFF = 0

    /** The nine codes this arm implements - a subset of SGR's, and the reference names no others. */
    val BITS_FOR = mapOf(
      ALL_OFF to (CellStyle.BOLD or CellStyle.UNDERLINE or CellStyle.BLINK or CellStyle.INVERSE),
      1 to CellStyle.BOLD,
      4 to CellStyle.UNDERLINE,
      5 to CellStyle.BLINK,
      7 to CellStyle.INVERSE,
      22 to CellStyle.BOLD,
      24 to CellStyle.UNDERLINE,
      25 to CellStyle.BLINK,
      27 to CellStyle.INVERSE,
    )

    /** The codes that clear rather than set - `0` among them, but only when this is not a reverse. */
    val CLEARING = setOf(ALL_OFF, 22, 24, 25, 27)
  }
}
