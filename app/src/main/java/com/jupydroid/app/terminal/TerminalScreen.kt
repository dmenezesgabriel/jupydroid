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
 * **Three neighbours own the rest**, each split off here on detekt's `TooManyFunctions`:
 * [CharacterPlacement] writes one character and [EscapeDispatch] performs the `ESC` sequences, both
 * from P4f-3a; [ScreenBuffers] holds the two screens and the cursor each one saved, from P4f-4b.
 * [grid] is whichever of those two is live, so nothing here names either by name.
 *
 * ```
 * val screen = TerminalScreen(columns = 80, rows = 24, totalRows = 2000)
 * screen.writeCodePoint('a'.code)
 * ```
 */
class TerminalScreen(val columns: Int, val rows: Int, totalRows: Int) {
  /** The main and alternate screens, and the cursor each one saved. `CSI ? 1049` moves between them. */
  val buffers = ScreenBuffers(columns, rows, totalRows)

  /** The buffer being written to, which is the main one until a shell asks for the alternate. */
  val grid: TerminalGrid get() = buffers.live

  val cursor = TerminalCursor(columns, rows)
  val charset = LineDrawingCharset()
  private val placement = CharacterPlacement(this)

  /** The DEC private modes. [ModeSwitching] is what turns one on or off. */
  val modes = ScreenModes()

  /** DECAWM. A character written past the right margin starts a new row instead of overwriting. */
  val autoWrap: Boolean get() = modes.isSet(DecMode.AUTOWRAP)

  /** IRM. A written character pushes what is already there to the right instead of replacing it. */
  var insertMode: Boolean = false

  /** What a written cell is painted with. [GraphicRendition] - `ESC [ Pm m` - is what changes it. */
  var currentStyle: CellStyle = CellStyle.NORMAL

  /**
   * What REP (`CSI Pn b`) repeats: the code point [writeCodePoint] was last given, **before** the
   * graphic set substituted for it, so a repeat under a different set draws what that set says.
   * A reset does not clear it, which is the reference's behaviour rather than an oversight kept.
   */
  var lastEmittedCodePoint: Int = NOTHING_EMITTED
    internal set

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
    cursor.column = cursor.tabs.next(1)
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

  /**
   * DECSC. `CSI s` joins it at P4f-3b, and P4f-5 puts the margin-mode condition on that one. What
   * it writes belongs to the **live buffer** - see [ScreenBuffers.savedCursor].
   */
  fun saveCursor() {
    buffers.savedCursor = SavedCursor(
      cursor.row,
      cursor.column,
      currentStyle,
      charset.snapshot(),
      autoWrap = modes.isSet(DecMode.AUTOWRAP),
      originMode = modes.isSet(DecMode.ORIGIN),
    )
  }

  /** DECRC, and `CSI u` from P4f-3b. */
  fun restoreCursor() {
    val saved = buffers.savedCursor
    cursor.moveTo(saved.row, saved.column)
    currentStyle = saved.style
    charset.restore(saved.graphicSets)
    modes.set(DecMode.AUTOWRAP, saved.autoWrap)
    modes.set(DecMode.ORIGIN, saved.originMode)
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
    blockClear(cursor.leftMargin, cursor.bottomMargin - 1, width)
  }

  /**
   * The reference's `blockClear`: a rectangle of spaces in **whatever style is in force**, which is
   * what makes a shell that sets a background and then clears paint the screen that colour. Every
   * erase goes through here, and so do RIS and the blank row a reverse index opens.
   *
   * `EscapeDispatch`'s DECBI and DECFI do **not** - they blank with the colours and no effects,
   * which is a different style and a declared divergence; see `blankStyle` there.
   */
  fun blockClear(x: Int, y: Int, width: Int, height: Int = 1) {
    grid.blockSet(x, y, width, height, ' '.code, currentStyle)
  }

  /**
   * Back to the state a freshly opened terminal is in, keeping what is on screen. **It does not
   * switch back to the main buffer** - the reference's `reset()` clears both saved cursors and
   * leaves the pointer where it is, so a RIS inside the alternate screen stays there.
   */
  fun reset() {
    cursor.resetMargins()
    cursor.tabs.reset()
    cursor.aboutToAutoWrap = false
    charset.reset()
    modes.reset()
    insertMode = false
    currentStyle = CellStyle.NORMAL
    buffers.forgetSavedCursors()
  }

  companion object {
    /** [lastEmittedCodePoint] before anything has been drawn, so REP has nothing to repeat. */
    const val NOTHING_EMITTED = -1
  }
}
