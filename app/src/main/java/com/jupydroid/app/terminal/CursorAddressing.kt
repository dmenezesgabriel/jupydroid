package com.jupydroid.app.terminal

/**
 * The control sequences that address the cursor: the motions, the tab stops, and the ANSI.SYS
 * save and restore.
 *
 * Transcribed from the cursor arms of `TerminalEmulator.doCsi` (termux-app
 * `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 - see [TerminalGrid]). A class of its own
 * because `doCsi` is 327 lines and detekt's `LongMethod` is 20: the final bytes arrive in groups,
 * and each group gets the applier that owns it - this one, and P4f-3c's for the erases and edits.
 *
 * **Six of these respect origin mode upstream** - CUP, HVP, CNL, CPL, HPA and VPR all go through
 * `setCursorPosition`, whose effective margins are the scrolling region when DECOM is on and the
 * whole screen when it is off. Origin mode is **P4f-4**, so what is written here is the
 * origin-mode-off case, spelled as a clamp to the screen rather than read off [TerminalCursor]'s
 * margins - which would silently change meaning the moment P4f-5 can set one.
 *
 * ```
 * CursorAddressing(screen).apply('H'.code, parameters)   // CUP: row and column, one-based
 * ```
 */
class CursorAddressing(private val screen: TerminalScreen) {
  private val cursor get() = screen.cursor

  /** Act on [finalByte] if it is one of these sequences; anything else is left for another applier. */
  fun apply(finalByte: Int, parameters: EscapeParameters) {
    if (moveCursor(finalByte, parameters)) return
    storeCursorOrClearTabs(finalByte, parameters)
  }

  /**
   * The motions. Every one reads its count with a zero meaning "unstated", so `CSI 0 A` moves one
   * row exactly as a bare `CSI A` does.
   */
  private fun moveCursor(finalByte: Int, parameters: EscapeParameters): Boolean {
    val count = parameters.at(0, default = 1, treatZeroAsDefault = true)
    when (finalByte) {
      CURSOR_UP -> cursor.moveToRow(maxOf(0, cursor.row - count))
      CURSOR_DOWN -> cursor.moveToRow(minOf(screen.rows - 1, cursor.row + count))
      CURSOR_FORWARD, COLUMN_RELATIVE -> cursor.moveToColumn(minOf(cursor.rightMargin - 1, cursor.column + count))
      CURSOR_BACK -> cursor.moveToColumn(maxOf(cursor.leftMargin, cursor.column - count))
      NEXT_LINE -> cursor.moveTo(cursor.row + count, 0)
      PREVIOUS_LINE -> cursor.moveTo(cursor.row - count, 0)
      COLUMN_ABSOLUTE -> cursor.moveToColumn(minOf(maxOf(1, count), screen.columns) - 1)
      POSITION, POSITION_ALIAS -> cursor.moveTo(count - 1, parameters.at(1, 1, treatZeroAsDefault = true) - 1)
      COLUMN_ABSOLUTE_RELATIVE_TO_ORIGIN -> cursor.moveTo(cursor.row, count - 1)
      ROW_ABSOLUTE -> cursor.moveToRow(minOf(maxOf(1, count), screen.rows) - 1)
      ROW_RELATIVE -> cursor.moveTo(cursor.row + count, cursor.column)
      FORWARD_TABULATION -> cursor.moveToColumn(cursor.nextTabStop(count))
      BACKWARD_TABULATION -> cursor.column = cursor.previousTabStop(count)
      else -> return false
    }
    return true
  }

  /**
   * The cursor store and the tab clears. `CSI s` saves **unconditionally** here; upstream it is
   * guarded on DECLRMM, where it sets the left and right margins instead - and DECLRMM cannot be
   * turned on until P4f-5, which is where that guard belongs.
   */
  private fun storeCursorOrClearTabs(finalByte: Int, parameters: EscapeParameters) {
    when (finalByte) {
      SAVE_CURSOR -> screen.saveCursor()
      RESTORE_CURSOR -> screen.restoreCursor()
      CLEAR_TAB_STOP -> clearTabStop(parameters.at(0, default = 0, treatZeroAsDefault = true))
      else -> Unit
    }
  }

  /** Every other parameter of TBC is specified to have no effect, and the reference obeys that. */
  private fun clearTabStop(mode: Int) {
    if (mode == CLEAR_STOP_AT_CURSOR) cursor.clearTabStopAtCursor()
    if (mode == CLEAR_EVERY_STOP) cursor.clearAllTabStops()
  }

  private companion object {
    const val CURSOR_UP = 'A'.code
    const val CURSOR_DOWN = 'B'.code
    const val CURSOR_FORWARD = 'C'.code
    const val CURSOR_BACK = 'D'.code
    const val NEXT_LINE = 'E'.code
    const val PREVIOUS_LINE = 'F'.code

    /** CHA. */
    const val COLUMN_ABSOLUTE = 'G'.code

    /** CUP, and HVP under a second final byte that means the same thing. */
    const val POSITION = 'H'.code
    const val POSITION_ALIAS = 'f'.code

    /** CHT and CBT. */
    const val FORWARD_TABULATION = 'I'.code
    const val BACKWARD_TABULATION = 'Z'.code

    /** HPA, which is CHA through `setCursorPosition` and so respects origin mode where CHA does not. */
    const val COLUMN_ABSOLUTE_RELATIVE_TO_ORIGIN = '`'.code

    /** HPR, which ISO 6429 defines and xterm answers exactly as it answers CUF. */
    const val COLUMN_RELATIVE = 'a'.code

    /** VPA and VPR. */
    const val ROW_ABSOLUTE = 'd'.code
    const val ROW_RELATIVE = 'e'.code

    /** TBC, and the two of its parameters that do anything. */
    const val CLEAR_TAB_STOP = 'g'.code
    const val CLEAR_STOP_AT_CURSOR = 0
    const val CLEAR_EVERY_STOP = 3

    /** ANSI.SYS's cursor store, which DECSC and DECRC share the saved state with. */
    const val SAVE_CURSOR = 's'.code
    const val RESTORE_CURSOR = 'u'.code
  }
}
