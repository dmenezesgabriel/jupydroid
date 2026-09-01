package com.jupydroid.app.terminal

/**
 * A terminal's screen: the grid, the cursor, the graphic sets and the modes, plus the operations a
 * control character asks for by name.
 *
 * Transcribed from the screen-facing half of `TerminalEmulator.java` (termux-app
 * `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 under that repository's `LICENSE.md`
 * exception) - `doLinefeed`, `scrollDownOneLine`, `saveCursor`, `restoreCursor` and the C0 motions.
 *
 * **The split is JediTerm's, not the reference's.** Termux's parser reaches into `mScreen`
 * directly; JediTerm keeps `JediEmulator` (bytes to operations) apart from `JediTerminal`
 * (operations to buffer) behind a `Terminal` interface. That seam is taken here - [TerminalEmulator]
 * calls this and never touches [TerminalGrid] - **as a class rather than an interface**, because
 * nothing has a second implementation and no test drives a fake of it. Extracting the interface is
 * a rename away if P4j's type-ahead ever wants one.
 *
 * **Two neighbours own the rest**, both split off here on detekt's `TooManyFunctions` at P4f-3a:
 * [CharacterPlacement] writes one character, and [EscapeDispatch] performs the `ESC` sequences.
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
  private val placement = CharacterPlacement(this)

  /** DECAWM. A character written past the right margin starts a new row instead of overwriting. */
  var autoWrap: Boolean = true

  /** IRM. A written character pushes what is already there to the right instead of replacing it. */
  var insertMode: Boolean = false

  /** What a written cell is painted with. [GraphicRendition] - `ESC [ Pm m` - is what changes it. */
  var currentStyle: CellStyle = CellStyle.NORMAL

  /** DECSC's, and never null: a restore before any save homes the cursor. See [SavedCursor]. */
  private var savedCursor: SavedCursor = SavedCursor.INITIAL

  /** Draw [codePoint] at the cursor, wrapping, inserting and advancing as the modes require. */
  fun writeCodePoint(codePoint: Int) = placement.write(codePoint)

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

  /** LF, VT and FF alike, and IND: down one row, scrolling when that would leave the region. */
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

  /** DECSC. `CSI s` joins it at P4f-3b, and P4f-5 puts the margin-mode condition on that one. */
  fun saveCursor() {
    savedCursor = SavedCursor(cursor.row, cursor.column, currentStyle, charset.snapshot())
  }

  /** DECRC, and `CSI u` from P4f-3b. */
  fun restoreCursor() {
    cursor.moveTo(savedCursor.row, savedCursor.column)
    currentStyle = savedCursor.style
    charset.restore(savedCursor.graphicSets)
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
    savedCursor = SavedCursor.INITIAL
  }
}
