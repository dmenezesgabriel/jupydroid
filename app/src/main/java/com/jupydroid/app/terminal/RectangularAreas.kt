package com.jupydroid.app.terminal

/**
 * The rectangular operations under the `$` intermediate: the fill and the two erases - DECFRA
 * (`CSI Pch;Pt;Pl;Pb;Pr $ x`), DECERA (`CSI Pt;Pl;Pb;Pr $ z`) and DECSERA (`$ {`) - and the copy,
 * DECCRA (`CSI Pts;Pls;Pbs;Prs;Pps;Ptd;Pld;Ppd $ v`).
 *
 * Transcribed from `ESC_CSI_DOLLAR`'s `x`/`z`/`{`/`v` arms (termux-app `3df69d1d`, Apache 2.0 -
 * see [TerminalGrid]). DECCARA and DECRARA are beside them; the arm is ~120 lines upstream over
 * three groups that share only their effective margins, which is why they are apart here.
 *
 * **DECCRA's coordinates are zero-based where the other three's are not**: it subtracts one from
 * its top and left, and they carry theirs one-based into the loop. Same arm, two conventions,
 * transcribed rather than regularised.
 *
 * **The coordinates are stated relative to the margins when origin mode is on**, exactly as
 * [TerminalCursor.moveWithinMargins] is - the reference recomputes the same four effective margins
 * at the top of this arm.
 *
 * **Three differences between the three operations, and each is the reference's**:
 * DECFRA reads its fill character from the **first** parameter and the other two start at the top
 * row; only DECSERA respects [CellStyle.PROTECTED]; and only DECSERA keeps the colours it found,
 * where the other two repaint with the style in force.
 *
 * ```
 * RectangularAreas(screen).apply('z'.code, parameters)   // DECERA: blank a rectangle
 * ```
 */
class RectangularAreas(private val screen: TerminalScreen) {
  /**
   * The rectangle a sequence names, one-based on the wire and offset by the effective margins.
   * A row is `top - 1` in the loop and `bottom` is not decremented, which is the reference's own
   * asymmetry rather than an off-by-one.
   */
  private data class Bounds(val top: Int, val left: Int, val bottom: Int, val right: Int)

  /** Act on [finalByte] if it is one of these. Anything else is swallowed with its parameters. */
  fun apply(finalByte: Int, parameters: EscapeParameters) {
    when (finalByte) {
      COPY -> copyArea(parameters)
      FILL, ERASE, SELECTIVE_ERASE -> paintArea(finalByte, parameters)
      else -> Unit
    }
  }

  /** DECFRA, DECERA and DECSERA, which differ only in what they write and what they spare. */
  private fun paintArea(finalByte: Int, parameters: EscapeParameters) {
    val fillFrom = if (finalByte == FILL) 1 else 0
    val fillChar = if (finalByte == FILL) parameters.at(0, default = -1, treatZeroAsDefault = true) else ' '.code
    if (fillChar !in PRINTABLE && fillChar !in PRINTABLE_HIGH) return
    paint(boundsFrom(parameters, fillFrom), fillChar, selective = finalByte == SELECTIVE_ERASE)
  }

  /**
   * DECCRA. **The destination is clipped to the screen, not to the source's size**, so a block
   * copied near the right or bottom edge arrives truncated rather than wrapping or being refused.
   * The two page parameters are read and discarded upstream, and are not read at all here.
   */
  private fun copyArea(parameters: EscapeParameters) {
    val origin = screen.modes.isSet(DecMode.ORIGIN)
    val top = if (origin) screen.cursor.topMargin else 0
    val left = if (origin) screen.cursor.leftMargin else 0
    val sourceTop = minOf(parameters.at(TOP, 1, treatZeroAsDefault = true) - 1 + top, screen.rows)
    val sourceLeft = minOf(parameters.at(LEFT, 1, treatZeroAsDefault = true) - 1 + left, screen.columns)
    val statedBottom = parameters.at(BOTTOM, screen.rows, treatZeroAsDefault = true) + top
    val statedRight = parameters.at(RIGHT, screen.columns, treatZeroAsDefault = true) + left
    val sourceBottom = clampedTo(statedBottom, sourceTop, screen.rows)
    val sourceRight = clampedTo(statedRight, sourceLeft, screen.columns)
    val toTop = minOf(parameters.at(DESTINATION_TOP, 1, treatZeroAsDefault = true) - 1 + top, screen.rows)
    val toLeft = minOf(parameters.at(DESTINATION_LEFT, 1, treatZeroAsDefault = true) - 1 + left, screen.columns)
    val height = minOf(screen.rows - toTop, sourceBottom - sourceTop)
    val width = minOf(screen.columns - toLeft, sourceRight - sourceLeft)
    screen.grid.blockCopy(sourceLeft, sourceTop, width, height, toLeft, toTop)
  }

  /** The reference's `min(max(value, floor), ceiling)`, which keeps an inverted rectangle empty. */
  private fun clampedTo(value: Int, floor: Int, ceiling: Int): Int = minOf(maxOf(value, floor), ceiling)

  /**
   * The four coordinates, each clamped to the effective margin on its own axis. [from] is 1 for
   * DECFRA, whose first parameter is the character rather than the top row.
   */
  private fun boundsFrom(parameters: EscapeParameters, from: Int): Bounds {
    val origin = screen.modes.isSet(DecMode.ORIGIN)
    val cursor = screen.cursor
    val top = if (origin) cursor.topMargin else 0
    val left = if (origin) cursor.leftMargin else 0
    val bottom = if (origin) cursor.bottomMargin else screen.rows
    val right = if (origin) cursor.rightMargin else screen.columns
    return Bounds(
      minOf(parameters.at(from + TOP, 1, treatZeroAsDefault = true) + top, bottom + 1),
      minOf(parameters.at(from + LEFT, 1, treatZeroAsDefault = true) + left, right + 1),
      minOf(parameters.at(from + BOTTOM, screen.rows, treatZeroAsDefault = true) + top, bottom),
      minOf(parameters.at(from + RIGHT, screen.columns, treatZeroAsDefault = true) + left, right),
    )
  }

  /**
   * The reference's double loop. **Only a selective erase spares a protected cell, and only a
   * selective erase keeps the cell's own style** - `keepVisualAttributes = erase && selective`
   * upstream, where `erase` is true for everything but DECFRA.
   */
  private fun paint(bounds: Bounds, fillChar: Int, selective: Boolean) {
    for (row in bounds.top - 1 until bounds.bottom) {
      for (column in bounds.left - 1 until bounds.right) {
        val found = screen.grid.styleAt(row, column)
        if (selective && found.effect and CellStyle.PROTECTED != 0) continue
        screen.grid.setChar(column, row, fillChar, if (selective) found else screen.currentStyle)
      }
    }
  }

  private companion object {
    /** DECFRA, DECERA, DECSERA and DECCRA. */
    const val FILL = 'x'.code
    const val ERASE = 'z'.code
    const val SELECTIVE_ERASE = '{'.code
    const val COPY = 'v'.code

    /**
     * Where each coordinate sits, counted from the first one - which is parameter 0 for DECERA and
     * DECSERA and parameter 1 for DECFRA, whose character comes first.
     */
    const val TOP = 0
    const val LEFT = 1
    const val BOTTOM = 2
    const val RIGHT = 3

    /** DECCRA's destination corner. `4` and `7` are its two pages, which the reference discards. */
    const val DESTINATION_TOP = 5
    const val DESTINATION_LEFT = 6

    /**
     * DECFRA's stated range: *"Pch can be any value from 32 to 126 or from 160 to 255. If Pch is
     * not in this range, then the terminal ignores the DECFRA command"* - and the reference obeys
     * that literally, dropping the whole sequence rather than substituting a space.
     */
    val PRINTABLE = 32..126
    val PRINTABLE_HIGH = 160..255
  }
}
