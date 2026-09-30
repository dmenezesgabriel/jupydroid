package com.jupydroid.app.terminal

/**
 * The sequences that set the margins: DECSTBM (`CSI Pt;Pb r`), DECSLRM (`CSI Pl;Pr s`), and the
 * column insert and delete that scroll within them - DECIC (`CSI Pn ' }`) and DECDC (`CSI Pn ' ~`).
 *
 * Transcribed from `doCsi`'s `r` and `s` arms and the `ESC_CSI_SINGLE_QUOTE` state (termux-app
 * `3df69d1d`, Apache 2.0 - see [TerminalGrid]). The applier beside [CursorAddressing],
 * [ScreenEditing] and [ModeSwitching], for the reason those exist.
 *
 * **`CSI s` is two sequences, and DECLRMM decides which.** With the mode off it is the ANSI.SYS
 * cursor save [CursorAddressing] performs; with it on it is DECSLRM. [apply] therefore declines
 * `s` when the mode is off, and the parser falls through to the cursor applier.
 *
 * **Both margin setters home the cursor**, and under origin mode home is the margin rather than the
 * corner - see [TerminalCursor.moveWithinMargins].
 *
 * ```
 * ScrollMargins(screen).apply('r'.code, parameters)   // DECSTBM: rows 2..3 scroll, the rest hold
 * ```
 */
class ScrollMargins(private val screen: TerminalScreen) {
  private val cursor get() = screen.cursor

  /** Act on [finalByte] if it is one of these, and say whether it was. */
  fun apply(finalByte: Int, parameters: EscapeParameters): Boolean {
    val isDecslrm = finalByte == HORIZONTAL_MARGINS && screen.modes.isSet(DecMode.LEFT_RIGHT_MARGIN)
    if (finalByte != SCROLL_REGION && !isDecslrm) return false
    if (isDecslrm) setHorizontalMargins(parameters) else setScrollRegion(parameters)
    cursor.moveWithinMargins(0, 0, screen.modes.isSet(DecMode.ORIGIN))
    return true
  }

  /**
   * `CSI ' }` and `CSI ' ~`, which arrive through their own parser state. Both work on the **whole
   * screen height** rather than the scrolling region, which is the reference's arithmetic
   * transcribed rather than regularised.
   */
  fun applyColumnEdit(finalByte: Int, parameters: EscapeParameters) {
    val afterCursor = cursor.rightMargin - cursor.column
    val count = minOf(parameters.at(0, default = 1, treatZeroAsDefault = true), afterCursor)
    when (finalByte) {
      INSERT_COLUMNS -> {
        screen.grid.blockCopy(cursor.column, 0, afterCursor - count, screen.rows, cursor.column + count, 0)
        screen.blockClear(cursor.column, 0, count, screen.rows)
      }

      DELETE_COLUMNS ->
        screen.grid.blockCopy(cursor.column + count, 0, afterCursor - count, screen.rows, cursor.column, 0)

      else -> Unit
    }
  }

  /**
   * DECSTBM. The top is one-based on the wire and zero-based here, so it loses one; the bottom is
   * the first row *below* the region and so keeps its number. **The region is at least two rows**,
   * which is what `max(top + 2, …)` enforces - a one-row region cannot scroll.
   */
  private fun setScrollRegion(parameters: EscapeParameters) {
    val top = maxOf(0, minOf(parameters.at(0, default = 1, treatZeroAsDefault = true) - 1, screen.rows - 2))
    val bottom = parameters.at(1, default = screen.rows, treatZeroAsDefault = true)
    cursor.topMargin = top
    cursor.bottomMargin = maxOf(top + 2, minOf(bottom, screen.rows))
  }

  /** DECSLRM, the same shape one axis over - and at least **one** column wide, not two. */
  private fun setHorizontalMargins(parameters: EscapeParameters) {
    val left = minOf(parameters.at(0, default = 1, treatZeroAsDefault = true) - 1, screen.columns - 2)
    val right = parameters.at(1, default = screen.columns, treatZeroAsDefault = true)
    cursor.leftMargin = left
    cursor.rightMargin = maxOf(left + 1, minOf(right, screen.columns))
  }

  private companion object {
    /** DECSTBM. */
    const val SCROLL_REGION = 'r'.code

    /** DECSLRM, but only while DECLRMM is on - otherwise this byte is the ANSI.SYS cursor save. */
    const val HORIZONTAL_MARGINS = 's'.code

    /** DECIC and DECDC, both under the `'` intermediate byte. */
    const val INSERT_COLUMNS = '}'.code
    const val DELETE_COLUMNS = '~'.code
  }
}
