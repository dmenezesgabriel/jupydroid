package com.jupydroid.app.terminal

/**
 * The protected attribute and the two erases that respect it: DECSCA (`CSI Pn " q`) marks what is
 * written next as protected, DECSED (`CSI ? Pn J`) and DECSEL (`CSI ? Pn K`) erase everything that
 * is not.
 *
 * Transcribed from `doCsiQuestionMark`'s `J`/`K` arms and the `ESC_CSI_DOUBLE_QUOTE` state
 * (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]). A file of its own rather than an
 * addition to [ScreenEditing], which sits at eleven functions against detekt's cap of eleven - and
 * the seam is real: these three share the [CellStyle.PROTECTED] bit and nothing else does.
 *
 * **DECSCA is a style bit, not a screen mode.** `1` protects, `0` and `2` both unprotect, and any
 * other parameter is an unknown sequence that changes nothing - measured on the reference, since
 * upstream tests only `0` and `1`.
 *
 * **A selective erase clears the pending wrap whatever its parameter**, where ED and EL clear it
 * only for a parameter they understood. That is not a regularisation: the reference
 * assigns `mAboutToAutoWrap = false` *before* its switch here and *after* it there.
 *
 * ```
 * SelectiveErasing(screen).applyProtection('q'.code, parameters)   // CSI 1 " q
 * ```
 */
class SelectiveErasing(private val screen: TerminalScreen) {
  private val cursor get() = screen.cursor

  /** `CSI ? Pn J` and `CSI ? Pn K`. Returns false for a byte that is neither, which [ModeSwitching] then sees. */
  fun apply(finalByte: Int, parameters: EscapeParameters): Boolean {
    if (finalByte != ERASE_IN_DISPLAY && finalByte != ERASE_IN_LINE) return false
    cursor.aboutToAutoWrap = false
    val justRow = finalByte == ERASE_IN_LINE
    eraseUnprotected(parameters.at(0, default = 0, treatZeroAsDefault = true), justRow)
    return true
  }

  /**
   * `CSI Pn " q`. The `"` intermediate has one final byte upstream; anything else is an unknown
   * sequence, and so is a parameter outside `0..2`.
   */
  fun applyProtection(finalByte: Int, parameters: EscapeParameters) {
    if (finalByte != SET_PROTECTION) return
    val current = screen.currentStyle
    val effect = when (parameters.at(0, default = 0, treatZeroAsDefault = true)) {
      PROTECT -> current.effect or CellStyle.PROTECTED
      UNPROTECT_ZERO, UNPROTECT_TWO -> current.effect and CellStyle.PROTECTED.inv()
      else -> return
    }
    screen.currentStyle = CellStyle.encode(current.foreColor, current.backColor, effect)
  }

  /**
   * The three regions, which are ED's and EL's: `justRow` is what turns a display erase into a line
   * erase, exactly as the reference's own flag does. A parameter outside `0..2` erases nothing -
   * but the pending wrap is already gone by then, which is the asymmetry this class's header names.
   */
  private fun eraseUnprotected(mode: Int, justRow: Boolean) {
    val firstRow = if (justRow) cursor.row else 0
    val lastRow = if (justRow) cursor.row + 1 else screen.rows
    when (mode) {
      ERASE_TO_END -> eraseCells(cursor.column, cursor.row, screen.columns, lastRow)
      ERASE_TO_START -> eraseCells(0, firstRow, cursor.column + 1, cursor.row + 1)
      ERASE_EVERYTHING -> eraseCells(0, firstRow, screen.columns, lastRow)
      else -> Unit
    }
  }

  /** The reference's own double loop, skipping what DECSCA protected. */
  private fun eraseCells(fromColumn: Int, fromRow: Int, toColumn: Int, toRow: Int) {
    for (row in fromRow until toRow) {
      for (column in fromColumn until toColumn) {
        if (screen.grid.styleAt(row, column).effect and CellStyle.PROTECTED != 0) continue
        screen.grid.setChar(column, row, ' '.code, screen.currentStyle)
      }
    }
  }

  private companion object {
    /** DECSED and DECSEL, both under the `?` private parameter byte. */
    const val ERASE_IN_DISPLAY = 'J'.code
    const val ERASE_IN_LINE = 'K'.code

    /** DECSCA's one final byte, under the `"` intermediate. */
    const val SET_PROTECTION = 'q'.code

    const val PROTECT = 1
    const val UNPROTECT_ZERO = 0
    const val UNPROTECT_TWO = 2

    /** The same three parameters ED and EL take, and the same meanings. */
    const val ERASE_TO_END = 0
    const val ERASE_TO_START = 1
    const val ERASE_EVERYTHING = 2
  }
}
