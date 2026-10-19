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
 * a rename away if anything ever wants one.
 *
 * **Three neighbours own the rest**, each split off here on detekt's `TooManyFunctions`:
 * [CharacterPlacement] writes one character, [EscapeDispatch] performs the `ESC` sequences, and
 * [ScreenBuffers] holds the two screens and the cursor each one saved.
 * [grid] is whichever of those two is live, so nothing here names either by name.
 *
 * ```
 * val screen = TerminalScreen(columns = 80, rows = 24, totalRows = 2000)
 * screen.writeCodePoint('a'.code)
 * ```
 */
class TerminalScreen(var columns: Int, var rows: Int, totalRows: Int, val colors: TerminalColors) {
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

  /**
   * DECSACE (`CSI 2 * x`). **A mode with no DECSET number** - `mapDecSetBitToInternalBit` returns
   * `-1` for it upstream, so [ScreenModes] has nowhere to put it and it sits here beside
   * [insertMode]. It decides whether [RectangularAttributes] writes a rectangle or a stream.
   */
  var rectangularAttributeChange: Boolean = false

  /** What a written cell is painted with. [GraphicRendition] - `ESC [ Pm m` - is what changes it. */
  var currentStyle: CellStyle = CellStyle.NORMAL

  /** The URI a written cell is linked to, or `null`. [HyperlinkCommand] - `OSC 8` - is what changes it. */
  var currentLink: String? = null

  /**
   * What REP (`CSI Pn b`) repeats: the code point [writeCodePoint] was last given, **before** the
   * graphic set substituted for it, so a repeat under a different set draws what that set says.
   * A reset does not clear it, which is the reference's behaviour rather than an oversight kept.
   */
  var lastEmittedCodePoint: Int = NOTHING_EMITTED
    internal set

  /**
   * Rows the content has moved up since [takeScrollCount] last read it - the reference's
   * `mScrollCounter`, which its view takes after every update so a text selection moves up with
   * the text it was made over (`TerminalView.onScreenUpdated`, termux-app `3df69d1d`).
   */
  internal var scrollCounter: Int = 0

  /** Draw [codePoint] at the cursor, wrapping, inserting and advancing as the modes require. */
  fun writeCodePoint(codePoint: Int) = placement.write(codePoint)

  /**
   * BS. At the left margin this steps back onto the row above, if that row wrapped into this one.
   *
   * **A declared divergence, and the smallest one that removes a crash.** The reference tests
   * `mCursorCol == mLeftMargin`, which holds only *at* the margin - so with the cursor **left of**
   * it the else-branch decrements, and the column goes negative. That state is reachable from a
   * shell: `CSI ? 69 h` then `CSI 2 s` sets a left margin of 1 and homes the cursor to column 0,
   * outside its own margin, because DECSLRM's home ignores the margin when origin mode is off.
   * One backspace then puts the reference at column **-1**, which it papers over in `setChar`
   * (`if (column < 0) column = 0`) and **throws** on in the insert path.
   *
   * `>` instead of `==` differs from the reference **only** when the cursor is already left of the
   * margin - the state the reference cannot represent - and is identical everywhere else, margin
   * or no margin. Measured against the reference, not argued.
   */
  fun backspace() {
    if (cursor.column > cursor.leftMargin) {
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
   * DECSC. `CSI s` reaches it too, under the margin-mode condition [ScrollMargins] carries. What
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

  /** DECRC, and `CSI u` with it. */
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
    scrollCounter++
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
    currentLink = null
    buffers.forgetSavedCursors()
    colors.reset()
  }

  /**
   * `TerminalEmulator.resize` fused with its own private `resizeScreen` (`:385-422`): a no-op when
   * neither dimension moves, an `IllegalArgumentException` below two of either, otherwise the
   * cursor's own size/margins/tabs brought to the new one ([TerminalCursor.resize]), the grid
   * reflowed at it, and the cursor's post-reflow position read back from what
   * [ScreenBuffers.resizeLiveGrid] returns - not clamped again here, see [TerminalCursor.resize]'s
   * own KDoc. [ModeSwitching] calls [ScreenBuffers.resizeLiveGrid] the same way, directly, when the
   * grid catching up is not this call's - see that method's own KDoc.
   */
  fun resize(newColumns: Int, newRows: Int) {
    if (columns == newColumns && rows == newRows) return
    require(newColumns >= 2 && newRows >= 2) { "rows=$newRows, columns=$newColumns" }
    cursor.resize(newColumns, newRows)
    columns = newColumns
    rows = newRows
    val result = buffers.resizeLiveGrid(columns, rows, cursor.row, cursor.column, currentStyle)
    cursor.row = result.row
    cursor.column = result.column
  }

  companion object {
    /** [lastEmittedCodePoint] before anything has been drawn, so REP has nothing to repeat. */
    const val NOTHING_EMITTED = -1
  }
}

/**
 * [TerminalScreen.scrollCounter], set back to `0` - `getScrollCounter` then `clearScrollCounter`
 * in the reference, read together because nothing reads one without the other. An extension
 * because [TerminalScreen] is at detekt's `TooManyFunctions` cap.
 *
 * ```
 * emulator.append(bytes, bytes.size)
 * val rowShift = emulator.screen.takeScrollCount()
 * ```
 */
fun TerminalScreen.takeScrollCount(): Int = scrollCounter.also { scrollCounter = 0 }
