package com.jupydroid.app.terminal

/**
 * The terminal operations a parser drives: put a character somewhere, move the cursor, scroll.
 *
 * Transcribed from the screen-facing half of `TerminalEmulator.java` (termux-app
 * `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 under that repository's `LICENSE.md`
 * exception) - `emitCodePoint`, `doLinefeed`, `scrollDownOneLine` and the C0 motions.
 *
 * **The split is JediTerm's, not the reference's.** Termux's parser reaches into `mScreen`
 * directly; JediTerm keeps `JediEmulator` (bytes to operations) apart from `JediTerminal`
 * (operations to buffer) behind a `Terminal` interface. That seam is taken here - [TerminalEmulator]
 * calls this and never touches [TerminalGrid] - **as a class rather than an interface**, because
 * nothing has a second implementation and no test drives a fake of it. Extracting the interface is
 * a rename away if P4j's type-ahead ever wants one.
 *
 * ```
 * val screen = TerminalScreen(columns = 80, rows = 24, totalRows = 2000)
 * screen.writeCodePoint('a'.code)
 * ```
 */
class TerminalScreen(val columns: Int, val rows: Int, totalRows: Int) {
  val grid = TerminalGrid(columns, totalRows, rows)
  val cursor = TerminalCursor(columns, rows)
  val charset = LineDrawingCharset()

  /** DECAWM. A character written past the right margin starts a new row instead of overwriting. */
  var autoWrap: Boolean = true

  /** IRM. A written character pushes what is already there to the right instead of replacing it. */
  var insertMode: Boolean = false

  /** What a written cell is painted with. [GraphicRendition] - `ESC [ Pm m` - is what changes it. */
  var currentStyle: CellStyle = CellStyle.NORMAL

  /** Draw [codePoint] at the cursor, wrapping, inserting and advancing as the modes require. */
  fun writeCodePoint(codePoint: Int) {
    val drawn = charset.substitute(codePoint)
    val width = CharacterWidth.of(drawn)
    if (!wrapBeforeWriting(width)) return
    makeRoomForInsert(width)
    grid.setChar(columnToWrite(width), cursor.row, drawn, currentStyle)
    if (autoWrap && width > 0) cursor.aboutToAutoWrap = cursor.column == cursor.rightMargin - width
    cursor.column = minOf(cursor.column + width, cursor.rightMargin - 1)
  }

  /** BS. At the left margin this steps back onto the row above, if that row wrapped into this one. */
  fun backspace() {
    if (cursor.column != cursor.leftMargin) {
      cursor.moveToColumn(cursor.column - 1)
      return
    }
    val previousRow = cursor.row - 1
    if (previousRow < 0 || !grid.rowAt(previousRow).lineWrap) return
    grid.rowAt(previousRow).lineWrap = false
    cursor.moveTo(previousRow, cursor.rightMargin - 1)
  }

  /**
   * HT. The reference leaves `aboutToAutoWrap` alone here - it assigns the column rather than
   * going through its setter - so tabbing onto the last column still wraps on the next character.
   */
  fun horizontalTab() {
    cursor.column = cursor.nextTabStop(1)
  }

  /** LF, VT and FF alike: down one row, scrolling when that would leave the region. */
  fun newLine() {
    if (cursor.row >= cursor.bottomMargin) {
      if (cursor.row != rows - 1) cursor.moveToRow(cursor.row + 1)
      return
    }
    var nextRow = cursor.row + 1
    if (nextRow == cursor.bottomMargin) {
      scrollUpOneLine()
      nextRow = cursor.bottomMargin - 1
    }
    cursor.moveToRow(nextRow)
  }

  /** CR. */
  fun carriageReturn() {
    cursor.moveToColumn(cursor.leftMargin)
  }

  /**
   * The content moves up one row and a blank one appears at the bottom of the region.
   *
   * Named for what the reader sees, where [TerminalGrid.scrollDownOneLine] is named for which way
   * the ring's rows move - the two are the same event from opposite ends. **With a horizontal
   * margin set, nothing reaches the transcript**: only part of each row is scrolling, so the row
   * leaving the top is not a whole line of history.
   */
  fun scrollUpOneLine() {
    if (cursor.leftMargin == 0 && cursor.rightMargin == columns) {
      grid.scrollDownOneLine(cursor.topMargin, cursor.bottomMargin, currentStyle)
      return
    }
    val width = cursor.rightMargin - cursor.leftMargin
    val height = cursor.bottomMargin - cursor.topMargin - 1
    grid.blockCopy(cursor.leftMargin, cursor.topMargin + 1, width, height, cursor.leftMargin, cursor.topMargin)
    grid.blockSet(cursor.leftMargin, cursor.bottomMargin - 1, width, 1, ' '.code, currentStyle)
  }

  /** Back to the state a freshly opened terminal is in, keeping what is on screen. */
  fun reset() {
    cursor.resetMargins()
    cursor.resetTabStops()
    cursor.aboutToAutoWrap = false
    charset.reset()
    autoWrap = true
    insertMode = false
    currentStyle = CellStyle.NORMAL
  }

  /**
   * Wrap to the next row if this character cannot fit in the last column. Returns false when the
   * character is to be dropped instead, which is a wide one at the right margin with [autoWrap]
   * off - a case the specification leaves open and the reference resolves by ignoring it.
   */
  private fun wrapBeforeWriting(width: Int): Boolean {
    val inLastColumn = cursor.column == cursor.rightMargin - 1
    val wideCannotFit = inLastColumn && width == WIDE
    val pendingWrap = inLastColumn && cursor.aboutToAutoWrap && width == 1
    if (!autoWrap) return !wideCannotFit
    if (wideCannotFit || pendingWrap) wrapToNextRow()
    return true
  }

  /**
   * The row the cursor leaves is marked as continuing into the next one, which is what [backspace]
   * reads to step back over a wrap.
   */
  private fun wrapToNextRow() {
    grid.rowAt(cursor.row).lineWrap = true
    cursor.column = cursor.leftMargin
    if (cursor.row + 1 < cursor.bottomMargin) cursor.row++ else scrollUpOneLine()
  }

  /** Under IRM, shift the rest of the row right by the width about to be written over it. */
  private fun makeRoomForInsert(width: Int) {
    if (!insertMode || width <= 0) return
    val destination = cursor.column + width
    if (destination >= cursor.rightMargin) return
    grid.blockCopy(cursor.column, cursor.row, cursor.rightMargin - destination, 1, destination, cursor.row)
  }

  /**
   * A combining mark belongs to the column *before* the cursor, since the cursor has already moved
   * past the character it modifies - unless a wrap is pending, in which case that character is the
   * one the cursor is still sitting on.
   */
  private fun columnToWrite(width: Int): Int {
    val combining = width <= 0 && cursor.column > 0 && !cursor.aboutToAutoWrap
    return maxOf(0, cursor.column - if (combining) 1 else 0)
  }

  private companion object {
    const val WIDE = 2
  }
}
