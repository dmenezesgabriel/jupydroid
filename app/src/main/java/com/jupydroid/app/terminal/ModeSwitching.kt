package com.jupydroid.app.terminal

/**
 * The sequences that turn a mode on or off: `CSI ? Pm h`/`l` (DECSET/DECRST), the `CSI ? Pm s`/`r`
 * that mark and recall them, and the ANSI `CSI Pm h`/`l`.
 *
 * Transcribed from `doCsiQuestionMark`'s `h`/`l`/`r`/`s` arms, `doDecSetOrReset` and `doSetMode`
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]). The applier beside [CursorAddressing]
 * and [ScreenEditing], for the same reason those exist.
 *
 * **Every parameter is acted on, not just the first** - `CSI ? 1000;1006 h` is one sequence setting
 * two modes, which is how a program enabling mouse reporting actually writes it.
 *
 * ```
 * ModeSwitching(screen).applyPrivate('h'.code, parameters)   // CSI ? Pm h
 * ```
 */
class ModeSwitching(private val screen: TerminalScreen) {
  private val modes get() = screen.modes

  /** `CSI ?` ended by [finalByte]. Anything but `h l s r` is swallowed with its parameters. */
  fun applyPrivate(finalByte: Int, parameters: EscapeParameters) {
    val codes = (0 until maxOf(1, parameters.count)).map { parameters.at(it, 0, treatZeroAsDefault = false) }
    when (finalByte) {
      SET -> codes.forEach { setOrReset(it, on = true) }
      RESET -> codes.forEach { setOrReset(it, on = false) }
      MARK -> codes.forEach { code -> DecMode.ofCode(code)?.let(modes::mark) }
      RECALL -> codes.forEach { code -> DecMode.ofCode(code)?.let { setOrReset(code, modes.recalled(it)) } }
      else -> Unit
    }
  }

  /** The ANSI modes, which carry no `?`. Only IRM has state here; the reference implements no other. */
  fun applyAnsi(finalByte: Int, parameters: EscapeParameters): Boolean {
    if (finalByte != SET && finalByte != RESET) return false
    if (parameters.at(0, 0, treatZeroAsDefault = false) == INSERT_REPLACE) {
      screen.insertMode = finalByte == SET
    }
    return true
  }

  /**
   * One mode, and the side effects the reference hangs off three of them. A code nothing maps to
   * still reaches here, because DECCOLM and DECOM are keyed on the external number.
   */
  private fun setOrReset(code: Int, on: Boolean) {
    DecMode.ofCode(code)?.let { modes.set(it, on) }
    when (code) {
      COLUMN_MODE -> resetToColumnMode()
      ORIGIN_MODE -> if (on) screen.cursor.moveWithinMargins(0, 0, originMode = true)
      LEFT_RIGHT_MARGIN_MODE -> if (!on) screen.cursor.resetHorizontalMargins()
      SAVE_CURSOR -> if (on) screen.saveCursor() else screen.restoreCursor()
      in ALTERNATE_SCREEN -> useAlternateScreen(on)
      else -> Unit
    }
  }

  /**
   * `CSI ? 47`/`1047`/`1049`, which the reference implements as **one arm**: entering saves the
   * cursor and blanks the alternate screen, leaving swaps back *before* restoring, so that the save
   * and the restore both land on the main screen's slot. A switch to the buffer already live is
   * dropped whole - no save, no blanking.
   *
   * **The buffer becoming live can be stale.** Only the live grid is ever resized while it is live
   * (`TerminalScreen.resize` reads [TerminalScreen.grid]), so a buffer that sat inactive through a
   * resize keeps its old shape until it is switched back to - [resized] is exactly the reference's
   * own `newScreen.mColumns == mColumns && newScreen.mScreenRows == mRows` check, read after the
   * switch since [TerminalScreen.grid] already names the buffer becoming live by then. On leave,
   * [TerminalScreen.restoreCursor] runs first as always, then - only when stale - is overridden
   * with the *raw*, unclamped saved coordinates, exactly as the reference's own `mCursorCol =
   * col; mCursorRow = row;` does: it is [ScreenBuffers.resizeLiveGrid]'s clamp that is meant to
   * place the cursor here, not `restoreCursor`'s. On enter there is no such override - `saveCursor`
   * already ran on the pre-switch cursor, and the [screen.blockClear] below wipes whatever the
   * resize left in place regardless.
   */
  private fun useAlternateScreen(on: Boolean) {
    if (screen.buffers.isAlternateLive == on) return
    if (on) screen.saveCursor()
    screen.buffers.makeAlternateLive(on)
    val resized = screen.grid.columns != screen.columns || screen.grid.screenRows != screen.rows
    if (!on) restoreCursorAcrossSwitch(resized)
    if (resized) catchUpLiveGrid()
    if (on) screen.blockClear(0, 0, screen.columns, screen.rows)
  }

  /**
   * The leave-path half of [useAlternateScreen]'s KDoc: [TerminalScreen.restoreCursor] as always,
   * then - only when [resized] - overridden with the raw, unclamped saved coordinates, so
   * [catchUpLiveGrid]'s own clamp is what places the cursor rather than `restoreCursor`'s.
   */
  private fun restoreCursorAcrossSwitch(resized: Boolean) {
    screen.restoreCursor()
    if (!resized) return
    val saved = screen.buffers.savedCursor
    screen.cursor.row = saved.row
    screen.cursor.column = saved.column
  }

  /** [ScreenBuffers.resizeLiveGrid] for the buffer just made live, plus the cursor writeback. */
  private fun catchUpLiveGrid() {
    val result = screen.buffers.resizeLiveGrid(
      screen.columns,
      screen.rows,
      screen.cursor.row,
      screen.cursor.column,
      screen.currentStyle,
    )
    screen.cursor.row = result.row
    screen.cursor.column = result.column
  }

  /**
   * DECCOLM. **The width does not change** - the reference does not resize either - but the four
   * side effects are kept, because `reset(1)` leans on them: the margins go back to the whole
   * screen, DECLRMM goes off with them, the screen is erased and the cursor homes.
   */
  private fun resetToColumnMode() {
    screen.cursor.resetMargins()
    screen.modes.set(DecMode.LEFT_RIGHT_MARGIN, false)
    screen.blockClear(0, 0, screen.columns, screen.rows)
    screen.cursor.moveTo(0, 0)
  }

  private companion object {
    const val SET = 'h'.code
    const val RESET = 'l'.code
    const val MARK = 's'.code
    const val RECALL = 'r'.code

    /** IRM, the one ANSI mode with state. `20` (LNM) and `34` are named upstream and do nothing. */
    const val INSERT_REPLACE = 4

    /** The three whose side effects outlive the flag itself. */
    const val COLUMN_MODE = 3
    const val ORIGIN_MODE = 6
    const val LEFT_RIGHT_MARGIN_MODE = 69

    /** `1048`: DECSC and DECRC under another name, with no buffer switch in it. */
    const val SAVE_CURSOR = 1048

    /**
     * The alternate screen, under the three numbers that reach the same arm upstream - xterm gives
     * them three meanings and the reference gives them one, which is transcribed rather than
     * corrected. **Neither these nor [SAVE_CURSOR] map to a DECSET bit**:
     * `mapDecSetBitToInternalBit` returns `-1` for all four, so which buffer is live carries the
     * state and nothing else does. That is also why `CSI ? 1049 s`/`r` do nothing on either side.
     */
    val ALTERNATE_SCREEN = setOf(47, 1047, 1049)
  }
}
