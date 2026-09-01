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
 * **Six of these respect origin mode** - CUP, HVP, CNL, CPL, HPA and VPR all go through
 * [TerminalCursor.moveWithinMargins], whose effective margins are the scrolling region when DECOM
 * is on and the whole screen when it is off. P4f-3b wrote them as the origin-mode-off case for want
 * of any way to set a margin; P4f-5a re-pointed them at the real clamp.
 *
 * **CUU and CUD do not** - they clamp to the screen, which is the reference's behaviour and
 * termux-app#1340's fix: a scrolling region bounds what scrolls, not where the cursor may go.
 *
 * ```
 * CursorAddressing(screen).apply('H'.code, parameters)   // CUP: row and column, one-based
 * ```
 */
class CursorAddressing(private val screen: TerminalScreen) {
  private val cursor get() = screen.cursor

  /** Act on [finalByte] if it is one of these sequences, and say whether it was. */
  fun apply(finalByte: Int, parameters: EscapeParameters): Boolean =
    moveCursor(finalByte, parameters) || storeCursorOrClearTabs(finalByte, parameters)

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
      NEXT_LINE -> moveWithinMargins(cursor.row + count, 0)
      PREVIOUS_LINE -> moveWithinMargins(cursor.row - count, 0)
      COLUMN_ABSOLUTE -> cursor.moveToColumn(minOf(maxOf(1, count), screen.columns) - 1)
      POSITION, POSITION_ALIAS -> moveWithinMargins(count - 1, parameters.at(1, 1, treatZeroAsDefault = true) - 1)
      COLUMN_ABSOLUTE_RELATIVE_TO_ORIGIN -> moveWithinMargins(cursor.row, count - 1)
      ROW_ABSOLUTE -> cursor.moveToRow(minOf(maxOf(1, count), screen.rows) - 1)
      ROW_RELATIVE -> moveWithinMargins(cursor.row + count, cursor.column)
      FORWARD_TABULATION -> cursor.moveToColumn(cursor.tabs.next(count))
      BACKWARD_TABULATION -> cursor.column = cursor.tabs.previous(count)
      else -> return false
    }
    return true
  }

  /** [TerminalCursor.moveWithinMargins] with the mode this screen is in, which is all five want. */
  private fun moveWithinMargins(row: Int, column: Int) =
    cursor.moveWithinMargins(row, column, screen.modes.isSet(DecMode.ORIGIN))

  /**
   * The cursor store and the tab clears. **`CSI s` only reaches here when DECLRMM is off**, which
   * is the reference's own guard: with the mode on it is DECSLRM, and [ScrollMargins] claims the
   * byte before this applier is offered it.
   */
  private fun storeCursorOrClearTabs(finalByte: Int, parameters: EscapeParameters): Boolean {
    when (finalByte) {
      SAVE_CURSOR -> screen.saveCursor()
      RESTORE_CURSOR -> screen.restoreCursor()
      CLEAR_TAB_STOP -> clearTabStop(parameters.at(0, default = 0, treatZeroAsDefault = true))
      else -> return false
    }
    return true
  }

  /** Every other parameter of TBC is specified to have no effect, and the reference obeys that. */
  private fun clearTabStop(mode: Int) {
    if (mode == CLEAR_STOP_AT_CURSOR) cursor.tabs.clearAtCursor()
    if (mode == CLEAR_EVERY_STOP) cursor.tabs.clearAll()
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
