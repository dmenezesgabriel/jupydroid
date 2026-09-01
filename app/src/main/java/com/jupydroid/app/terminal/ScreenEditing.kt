package com.jupydroid.app.terminal

/**
 * The control sequences that erase and edit: ED and EL, ECH, ICH and DCH, IL and DL, SU and SD,
 * and REP.
 *
 * Transcribed from the erase and edit arms of `TerminalEmulator.doCsi` and its `blockClear`
 * (termux-app `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 - see [TerminalGrid]). The
 * second applier beside [CursorAddressing], for the reason that one exists: `doCsi` is 327 lines
 * and detekt's `LongMethod` is 20.
 *
 * **What an erase blanks a cell *to* is the current style, not the terminal's defaults** - the
 * reference's `blockClear` passes `getStyle()`, so a shell that sets a background and then clears
 * paints the screen that colour. `TerminalTest.testBackgroundColorErase` is what pins it.
 *
 * **Six of these clear the pending wrap and two do not**, which is transcribed rather than
 * regularised: ED and EL clear it only when their parameter was one they understood, IL does not
 * clear it at all, and SU/SD/REP never touch it.
 *
 * **`CSI 3 J` clears the *main* screen's scrollback**, not the live buffer's - upstream that arm
 * names `mMainBuffer` - so it goes through [ScreenBuffers.clearTranscript] rather than
 * `screen.grid`. A full-screen program clearing the scrollback from the alternate screen is
 * clearing the one the user sees when it exits.
 *
 * ```
 * ScreenEditing(screen).apply('J'.code, parameters)   // ED: erase from the cursor down
 * ```
 */
class ScreenEditing(private val screen: TerminalScreen) {
  private val cursor get() = screen.cursor

  /** Act on [finalByte] if it is one of these sequences. Anything else is nobody's and is swallowed. */
  fun apply(finalByte: Int, parameters: EscapeParameters) {
    val count = parameters.at(0, default = 1, treatZeroAsDefault = true)
    when (finalByte) {
      INSERT_CHARACTERS -> insertCharacters(count)
      ERASE_IN_DISPLAY -> if (eraseInDisplay(parameters.at(0, 0, treatZeroAsDefault = true))) clearPendingWrap()
      ERASE_IN_LINE -> if (eraseInLine(parameters.at(0, 0, treatZeroAsDefault = true))) clearPendingWrap()
      INSERT_LINES -> insertLines(count)
      DELETE_LINES -> deleteLines(count)
      DELETE_CHARACTERS -> deleteCharacters(count)
      SCROLL_UP -> repeat(count) { screen.scrollUpOneLine() }
      SCROLL_DOWN -> if (parameters.count == 1) scrollDown(count)
      ERASE_CHARACTERS -> eraseCharacters(count)
      REPEAT -> repeatLastCharacter(count)
      else -> Unit
    }
  }

  /**
   * ED. **Ignores the scrolling margins**, as the specification says it must, and `3` is xterm's
   * non-standard "forget the scrollback". Returns false for a parameter nothing claims, which is
   * what leaves the pending wrap alone.
   */
  private fun eraseInDisplay(mode: Int): Boolean {
    when (mode) {
      ERASE_TO_END -> {
        screen.blockClear(cursor.column, cursor.row, screen.columns - cursor.column)
        screen.blockClear(0, cursor.row + 1, screen.columns, screen.rows - (cursor.row + 1))
      }

      ERASE_TO_START -> {
        screen.blockClear(0, 0, screen.columns, cursor.row)
        screen.blockClear(0, cursor.row, cursor.column + 1)
      }

      ERASE_EVERYTHING -> screen.blockClear(0, 0, screen.columns, screen.rows)
      ERASE_SCROLLBACK -> screen.buffers.clearTranscript()
      else -> return false
    }
    return true
  }

  /** EL, the same three halves for one row. */
  private fun eraseInLine(mode: Int): Boolean {
    when (mode) {
      ERASE_TO_END -> screen.blockClear(cursor.column, cursor.row, screen.columns - cursor.column)
      ERASE_TO_START -> screen.blockClear(0, cursor.row, cursor.column + 1)
      ERASE_EVERYTHING -> screen.blockClear(0, cursor.row, screen.columns)
      else -> return false
    }
    return true
  }

  /** ICH: blanks open at the cursor and the rest of the row moves right, losing what falls off. */
  private fun insertCharacters(count: Int) {
    clearPendingWrap()
    val columnsAfterCursor = screen.columns - cursor.column
    val spacesToInsert = minOf(count, columnsAfterCursor)
    val charactersToMove = columnsAfterCursor - spacesToInsert
    screen.grid.blockCopy(cursor.column, cursor.row, charactersToMove, 1, cursor.column + spacesToInsert, cursor.row)
    screen.blockClear(cursor.column, cursor.row, spacesToInsert)
  }

  /** DCH: the rest of the row moves left, and blanks with no attributes appear at the margin. */
  private fun deleteCharacters(count: Int) {
    clearPendingWrap()
    val cellsAfterCursor = screen.columns - cursor.column
    val cellsToDelete = minOf(count, cellsAfterCursor)
    val cellsToMove = cellsAfterCursor - cellsToDelete
    screen.grid.blockCopy(cursor.column + cellsToDelete, cursor.row, cellsToMove, 1, cursor.column, cursor.row)
    screen.blockClear(cursor.column + cellsToMove, cursor.row, cellsToDelete)
  }

  /** IL. The one edit that leaves the pending wrap alone, which the reference does not explain. */
  private fun insertLines(count: Int) {
    val linesAfterCursor = cursor.bottomMargin - cursor.row
    val linesToInsert = minOf(count, linesAfterCursor)
    val linesToMove = linesAfterCursor - linesToInsert
    screen.grid.blockCopy(0, cursor.row, screen.columns, linesToMove, 0, cursor.row + linesToInsert)
    screen.blockClear(0, cursor.row, screen.columns, linesToInsert)
  }

  /** DL. */
  private fun deleteLines(count: Int) {
    clearPendingWrap()
    val linesAfterCursor = cursor.bottomMargin - cursor.row
    val linesToDelete = minOf(count, linesAfterCursor)
    val linesToMove = linesAfterCursor - linesToDelete
    screen.grid.blockCopy(0, cursor.row + linesToDelete, screen.columns, linesToMove, 0, cursor.row)
    screen.blockClear(0, cursor.row + linesToMove, screen.columns, linesToDelete)
  }

  /**
   * SD. **Only the one-parameter form**: with more, the sequence is xterm's highlight mouse
   * tracking, which the reference leaves unimplemented and so does this.
   */
  private fun scrollDown(count: Int) {
    val linesBetweenMargins = cursor.bottomMargin - cursor.topMargin
    val linesToScroll = minOf(linesBetweenMargins, count)
    val width = cursor.rightMargin - cursor.leftMargin
    val height = linesBetweenMargins - linesToScroll
    val destination = cursor.topMargin + linesToScroll
    screen.grid.blockCopy(cursor.leftMargin, cursor.topMargin, width, height, cursor.leftMargin, destination)
    screen.blockClear(cursor.leftMargin, cursor.topMargin, width, linesToScroll)
  }

  /** ECH: blanks in place, moving nothing - which is what tells it apart from DCH. */
  private fun eraseCharacters(count: Int) {
    clearPendingWrap()
    val width = minOf(count, screen.columns - cursor.column)
    screen.blockClear(cursor.column, cursor.row, width)
  }

  /**
   * REP draws the **last code point emitted**, not the one under the cursor, so a motion between
   * the character and the repeat changes where it lands and not what it is. Before anything has
   * been drawn there is nothing to repeat.
   */
  private fun repeatLastCharacter(count: Int) {
    val codePoint = screen.lastEmittedCodePoint
    if (codePoint == TerminalScreen.NOTHING_EMITTED) return
    repeat(count) { screen.writeCodePoint(codePoint) }
  }

  private fun clearPendingWrap() {
    cursor.aboutToAutoWrap = false
  }

  private companion object {
    const val INSERT_CHARACTERS = '@'.code
    const val ERASE_IN_DISPLAY = 'J'.code
    const val ERASE_IN_LINE = 'K'.code
    const val INSERT_LINES = 'L'.code
    const val DELETE_LINES = 'M'.code
    const val DELETE_CHARACTERS = 'P'.code
    const val SCROLL_UP = 'S'.code
    const val SCROLL_DOWN = 'T'.code
    const val ERASE_CHARACTERS = 'X'.code
    const val REPEAT = 'b'.code

    /** ED's and EL's parameters. `3` is ED's alone, and EL rejects it. */
    const val ERASE_TO_END = 0
    const val ERASE_TO_START = 1
    const val ERASE_EVERYTHING = 2
    const val ERASE_SCROLLBACK = 3
  }
}
