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
   * The reference also resizes here when the two buffers disagree on size. **Nothing resizes until
   * P4h**, which owns `resize`, so that branch is unreachable and is left to the step that makes
   * it reachable rather than written blind.
   */
  private fun useAlternateScreen(on: Boolean) {
    if (screen.buffers.isAlternateLive == on) return
    if (on) screen.saveCursor()
    screen.buffers.makeAlternateLive(on)
    if (on) screen.blockClear(0, 0, screen.columns, screen.rows) else screen.restoreCursor()
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
