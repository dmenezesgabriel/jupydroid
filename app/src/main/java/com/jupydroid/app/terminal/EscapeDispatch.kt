package com.jupydroid.app.terminal

/**
 * The byte after a bare `ESC`: either it opens a state of its own, or it is a whole command - and
 * what each of those commands does to the screen. **Every entry point returns where the sequence
 * goes next**, so [EscapeParser] routes and never decides.
 *
 * Transcribed from `TerminalEmulator.doEsc` and `doEscPound`, plus the two charset-designation arms
 * of its state dispatch (termux-app `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 - see
 * [TerminalGrid]).
 *
 * **The operations are here rather than on [TerminalScreen], which is where the reference puts
 * them too.** Each is reachable from an `ESC` sequence and from nowhere else, so `TerminalScreen`
 * would be carrying it for one caller apiece. Three go elsewhere for a reason of their own: DECSC
 * and DECRC stay on the screen because `CSI s` and `CSI u` reach them as well (P4f-3b), and
 * lower-left is a cursor motion with no grid in it, so it is [TerminalCursor.moveToLowerLeft].
 * detekt's `TooManyFunctions` asked the question at P4f-3a; `docs/DECISIONS.md` records the answer.
 *
 * **What is not here, and which sub-step has it**: `P` (DCS), `]` (OSC) and `_` (APC) are P4f-6,
 * which is where a reply path to the pty arrives; `=` and `>` (DECKPAM/DECKPNM) are P4f-4, where
 * the DEC private mode they set lives. Until then each ends the sequence taking only itself, which
 * is what the reference does with an introducer it does not implement.
 *
 * ```
 * val dispatch = EscapeDispatch(screen)
 * dispatch.introduce('7'.code)   // DECSC: the cursor is put away
 * ```
 */
class EscapeDispatch(private val screen: TerminalScreen) {
  private val grid get() = screen.grid
  private val cursor get() = screen.cursor

  /**
   * What DECBI and DECFI blank the column they open with: the current colours and **no effects**,
   * which is `TextStyle.encode(mForeColor, mBackColor, 0)` upstream rather than its `getStyle()`.
   */
  private val blankStyle: CellStyle
    get() = CellStyle.encode(screen.currentStyle.foreColor, screen.currentStyle.backColor, 0)

  /** Where the sequence goes next. Everything that is not an introducer is applied and ends here. */
  fun introduce(codePoint: Int): EscapeState = when (codePoint) {
    POUND -> EscapeState.EscapePound
    LEFT_PARENTHESIS -> EscapeState.DesignateG0
    RIGHT_PARENTHESIS -> EscapeState.DesignateG1
    CONTROL_SEQUENCE_INTRODUCER -> EscapeState.ControlSequence
    else -> {
      applyCommand(codePoint)
      EscapeState.Ground
    }
  }

  /** `ESC ( C`: `0` designates the DEC Special Graphics set into G0, anything else ASCII. */
  fun designateG0(codePoint: Int): EscapeState {
    screen.charset.designateG0(codePoint == DEC_SPECIAL_GRAPHICS)
    return EscapeState.Ground
  }

  /** `ESC ) C`, the same for G1 - which SO invokes and SI puts away again. */
  fun designateG1(codePoint: Int): EscapeState {
    screen.charset.designateG1(codePoint == DEC_SPECIAL_GRAPHICS)
    return EscapeState.Ground
  }

  /**
   * `ESC # C`: DECALN, the screen alignment test, is the only one the reference implements. It
   * paints every cell an `E` and leaves the cursor where it stands.
   */
  fun applyPound(codePoint: Int): EscapeState {
    if (codePoint != ALIGNMENT_TEST) return EscapeState.Ground
    grid.blockSet(0, 0, screen.columns, screen.rows, ALIGNMENT_FILL, screen.currentStyle)
    return EscapeState.Ground
  }

  /**
   * The `ESC` sequences that are one byte long. A byte no arm claims is **consumed and does
   * nothing**, which is the reference's `unknownSequence` arm: it logs, where there is nothing here
   * to log to, and then ends the sequence exactly as the ignored bytes do. SS2 and SS3 are among
   * those - the reference gives them arms of their own and both are empty, and it writes `'0'`
   * where SS3 is `'O'`, which no screen can tell apart from an unknown byte.
   */
  private fun applyCommand(codePoint: Int) {
    when (codePoint) {
      BACK_INDEX -> backIndex()
      SAVE_CURSOR -> screen.saveCursor()
      RESTORE_CURSOR -> screen.restoreCursor()
      FORWARD_INDEX -> forwardIndex()
      INDEX -> screen.newLine()
      NEXT_LINE -> nextLine()
      LOWER_LEFT -> cursor.moveToLowerLeft()
      TAB_SET -> cursor.tabs.setAtCursor()
      REVERSE_INDEX -> reverseIndex()
      RESET_TO_INITIAL_STATE -> resetToInitialState()
      APPLICATION_KEYPAD -> screen.modes.set(DecMode.APPLICATION_KEYPAD, true)
      NUMERIC_KEYPAD -> screen.modes.set(DecMode.APPLICATION_KEYPAD, false)
      else -> Unit
    }
  }

  /**
   * NEL. A linefeed that also returns to the first column - **which under origin mode is the left
   * margin**, not column 0. P4f-3a wrote the off-case for want of any way to set a margin;
   * `ScrollRegionTest.nextLineRespectsTheLeftMargin` is what makes the two differ.
   */
  private fun nextLine() {
    cursor.moveToColumn(if (screen.modes.isSet(DecMode.ORIGIN)) cursor.leftMargin else 0)
    screen.newLine()
  }

  /**
   * RI. Up one row, and at the top margin the screen moves *down* instead - there is no row above
   * to move onto. The row is decremented directly, as the reference does, so a pending wrap
   * survives the motion.
   */
  private fun reverseIndex() {
    if (cursor.row > cursor.topMargin) {
      cursor.row--
      return
    }
    val width = cursor.rightMargin - cursor.leftMargin
    val height = cursor.bottomMargin - (cursor.topMargin + 1)
    grid.blockCopy(cursor.leftMargin, cursor.topMargin, width, height, cursor.leftMargin, cursor.topMargin + 1)
    screen.blockClear(cursor.leftMargin, cursor.topMargin, width)
  }

  /** RIS. Everything [TerminalScreen.reset] does, and then the screen, the scrollback and home. */
  private fun resetToInitialState() {
    screen.reset()
    screen.buffers.clearTranscript()
    screen.blockClear(0, 0, screen.columns, screen.rows)
    cursor.moveWithinMargins(0, 0, screen.modes.isSet(DecMode.ORIGIN))
  }

  /**
   * DECBI. Left one column; at the left margin the region shifts right instead and a blank column
   * opens at the margin, with whatever passes the right margin lost.
   */
  private fun backIndex() {
    if (cursor.column > cursor.leftMargin) {
      cursor.column--
      return
    }
    val height = cursor.bottomMargin - cursor.topMargin
    val width = cursor.rightMargin - cursor.leftMargin - 1
    grid.blockCopy(cursor.leftMargin, cursor.topMargin, width, height, cursor.leftMargin + 1, cursor.topMargin)
    grid.blockSet(cursor.leftMargin, cursor.topMargin, 1, height, ' '.code, blankStyle)
  }

  /** DECFI. Right one column; at the right margin the region shifts left and the first is lost. */
  private fun forwardIndex() {
    if (cursor.column < cursor.rightMargin - 1) {
      cursor.column++
      return
    }
    val height = cursor.bottomMargin - cursor.topMargin
    val width = cursor.rightMargin - cursor.leftMargin - 1
    grid.blockCopy(cursor.leftMargin + 1, cursor.topMargin, width, height, cursor.leftMargin, cursor.topMargin)
    grid.blockSet(cursor.rightMargin - 1, cursor.topMargin, 1, height, ' '.code, blankStyle)
  }

  private companion object {
    /** DECKPAM and DECKPNM. The flag is all there is until P4j sends a key. */
    const val APPLICATION_KEYPAD = '='.code
    const val NUMERIC_KEYPAD = '>'.code

    const val POUND = '#'.code
    const val LEFT_PARENTHESIS = '('.code
    const val RIGHT_PARENTHESIS = ')'.code
    const val CONTROL_SEQUENCE_INTRODUCER = '['.code

    /** The final byte of `ESC ( C` that means DEC Special Graphics rather than a Latin set. */
    const val DEC_SPECIAL_GRAPHICS = '0'.code

    /** `ESC # 8`, DECALN, and the letter it paints every cell with. */
    const val ALIGNMENT_TEST = '8'.code
    const val ALIGNMENT_FILL = 'E'.code

    const val BACK_INDEX = '6'.code
    const val SAVE_CURSOR = '7'.code
    const val RESTORE_CURSOR = '8'.code
    const val FORWARD_INDEX = '9'.code
    const val INDEX = 'D'.code
    const val NEXT_LINE = 'E'.code
    const val LOWER_LEFT = 'F'.code
    const val TAB_SET = 'H'.code
    const val REVERSE_INDEX = 'M'.code
    const val RESET_TO_INITIAL_STATE = 'c'.code
  }
}
