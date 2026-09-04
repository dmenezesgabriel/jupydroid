package com.jupydroid.app.terminal

/**
 * The control sequences a shell asks questions with, and the answers written back through
 * [TerminalReplies]: device attributes, status reports, the DEC mode query and the window
 * operations that report a size.
 *
 * **Two of `CSI t`'s arms answer nothing and push the title stack instead**, which is not a report
 * at all. They are here because they are `CSI t`, and splitting four of that sequence's codes into
 * a class of their own would cost more than the name does - the reference dispatches all of them
 * from one `switch`.
 *
 * Transcribed from the `c`, `n` and `t` arms of `TerminalEmulator.doCsi`, `doCsiBiggerThan`'s `c`,
 * `doCsiQuestionMark`'s `n`, and the `ESC_CSI_QUESTIONMARK_ARG_DOLLAR` state (termux-app
 * `3df69d1d`, Apache 2.0 - see [TerminalGrid]).
 *
 * **DECRQM's `47`/`1047`/`1049` arm reads which buffer is live** rather than a mode flag, because
 * none of the three has one - so this needed P4f-4b's second screen before it could answer.
 *
 * ```
 * TerminalReporting(screen, replies, 13, 15).apply('n'.code, parameters)   // CSI 6 n -> CSI y;x R
 * ```
 */
class TerminalReporting(
  private val screen: TerminalScreen,
  private val replies: TerminalReplies,
  private val title: WindowTitle,
  private var cellWidthPixels: Int,
  private var cellHeightPixels: Int,
) {
  /**
   * P4i-2b: what `CSI 14/16 t` answer after a real cell is measured - `TerminalEmulator.resize`'s
   * own entry point, forwarded here rather than reconstructing this class on every layout.
   */
  fun updateCellPixels(widthPixels: Int, heightPixels: Int) {
    cellWidthPixels = widthPixels
    cellHeightPixels = heightPixels
  }

  /** The ANSI reports: `CSI c`, `CSI n` and `CSI t`. Says whether [finalByte] was one of them. */
  fun apply(finalByte: Int, parameters: EscapeParameters): Boolean {
    val what = parameters.at(0, default = 0, treatZeroAsDefault = true)
    when (finalByte) {
      DEVICE_ATTRIBUTES -> if (what == 0) replies.reply(PRIMARY_ATTRIBUTES)
      STATUS_REPORT -> reportStatus(what)
      WINDOW_OPERATION -> reportWindow(what)
      else -> return false
    }
    return true
  }

  /** `CSI > c`, secondary device attributes: xterm's own answer, which some programs feature-check. */
  fun replySecondaryAttributes() = replies.reply(SECONDARY_ATTRIBUTES)

  /**
   * `CSI ? 6 n`, DECXCPR - the cursor position with a page number, where `CSI 6 n` sends it
   * without one. Says whether [finalByte] was it.
   */
  fun applyPrivate(finalByte: Int, parameters: EscapeParameters): Boolean {
    if (finalByte != STATUS_REPORT) return false
    if (parameters.at(0, default = -1, treatZeroAsDefault = false) == CURSOR_POSITION) {
      replies.reply(CSI + "?${screen.cursor.row + 1};${screen.cursor.column + 1};1R")
    }
    return true
  }

  /**
   * `CSI ? Pm $ p`, DECRQM. **`0` means "this terminal has no such mode"**, which is what a mode
   * outside the twelve gets - the reference logs it and answers `0` rather than staying silent.
   */
  fun replyModeState(parameters: EscapeParameters) {
    val mode = parameters.at(0, default = 0, treatZeroAsDefault = true)
    val state = when (mode) {
      in ALTERNATE_SCREEN_MODES -> if (screen.buffers.isAlternateLive) SET else RESET
      else -> DecMode.ofCode(mode)?.let { if (screen.modes.isSet(it)) SET else RESET } ?: UNRECOGNISED
    }
    replies.reply(CSI + "?$mode;$state\$y")
  }

  /** `CSI Pn n`. Anything but `5` and `6` is silent. */
  private fun reportStatus(what: Int) {
    when (what) {
      TERMINAL_OK -> replies.reply(CSI + "0n")
      CURSOR_POSITION -> replies.reply(CSI + "${screen.cursor.row + 1};${screen.cursor.column + 1}R")
      else -> Unit
    }
  }

  /**
   * `CSI Pn t`. The size reports are the useful ones, the position and state answers are fixed, and
   * the two **label** reports answer with a constant rather than the real title - which upstream
   * does on purpose, its comment beside both reading "Disabled due to security concerns". The
   * stack operations take a parameter and ignore it: `22;0`, `22;1` and `22;2` reach one arm.
   */
  private fun reportWindow(what: Int) {
    when (what) {
      WINDOW_STATE -> replies.reply(CSI + "1t")
      WINDOW_POSITION -> replies.reply(CSI + "3;0;0t")
      WINDOW_PIXELS -> replies.reply(CSI + "4;${screen.rows * cellHeightPixels};${screen.columns * cellWidthPixels}t")
      CELL_PIXELS -> replies.reply(CSI + "6;$cellHeightPixels;${cellWidthPixels}t")
      TEXT_AREA_CELLS -> replies.reply(CSI + "8;${screen.rows};${screen.columns}t")
      SCREEN_CELLS -> replies.reply(CSI + "9;${screen.rows};${screen.columns}t")
      ICON_LABEL -> replies.reply(OSC + "LIconLabel" + STRING_TERMINATOR)
      WINDOW_LABEL -> replies.reply(OSC + "l" + STRING_TERMINATOR)
      PUSH_TITLE -> title.push()
      POP_TITLE -> title.pop()
      else -> Unit
    }
  }

  private companion object {
    const val CSI = "\u001B["
    const val OSC = "\u001B]"
    const val STRING_TERMINATOR = "\u001B\\"

    const val DEVICE_ATTRIBUTES = 'c'.code
    const val STATUS_REPORT = 'n'.code
    const val WINDOW_OPERATION = 't'.code

    /** DA1's answer, sent as xterm sends it: service class 64, "vt420", then the attribute list. */
    const val PRIMARY_ATTRIBUTES = "\u001B[?64;1;2;6;9;15;18;21;22c"

    /**
     * DA2's. The middle field is xterm's release number, which `tmux` and `vim` feature-check -
     * `320` is what the reference claims, and claiming less would turn features off.
     */
    const val SECONDARY_ATTRIBUTES = "\u001B[>41;320;0c"

    const val TERMINAL_OK = 5
    const val CURSOR_POSITION = 6

    /** DECRQM's three answers. */
    const val SET = 1
    const val RESET = 2
    const val UNRECOGNISED = 0

    /** The modes DECRQM answers from the buffer pointer, since none of them has a flag. */
    val ALTERNATE_SCREEN_MODES = setOf(47, 1047, 1049)

    const val WINDOW_STATE = 11
    const val WINDOW_POSITION = 13
    const val WINDOW_PIXELS = 14
    const val CELL_PIXELS = 16
    const val TEXT_AREA_CELLS = 18
    const val SCREEN_CELLS = 19
    const val ICON_LABEL = 20
    const val WINDOW_LABEL = 21
    const val PUSH_TITLE = 22
    const val POP_TITLE = 23
  }
}
