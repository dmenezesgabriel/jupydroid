package com.jupydroid.app.terminal

/**
 * A terminal's screen and its scrollback: [screenRows] visible rows of [columns] columns, over a
 * ring of [RowRing] slots that also holds what has scrolled off the top.
 *
 * Transcribed from `terminal-emulator/src/main/java/com/termux/terminal/TerminalBuffer.java`
 * (`reference/research_repos/termux-app`, commit `3df69d1da197dd9bd71a3bafd902dffd720576b4`,
 * Apache 2.0 under that repository's `LICENSE.md` exception).
 *
 * **Named `TerminalGrid`, not `TerminalBuffer`**: `read_buffer` is already terminado's in this
 * codebase (`RealTerminalGateway`), and what this is, is the logical screen.
 *
 * **Rows are addressed externally**: `0` is the top of the screen, `screenRows - 1` the bottom,
 * and negative rows are scrollback. Nothing outside `RowRing` sees a slot index - which is one
 * departure from the reference, whose `externalToInternalRow` is public because its own tests use
 * it. [rowAt] is what callers want, and this file's tests take it.
 *
 * ```
 * val grid = TerminalGrid(columns = 80, totalRows = 200, screenRows = 24)
 * grid.setChar(column = 0, row = 0, codePoint = 'a'.code, style = CellStyle.NORMAL)
 * ```
 */
class TerminalGrid(val columns: Int, totalRows: Int, screenRows: Int) {
  private val ring = RowRing(columns, totalRows, screenRows)
  private val selection = GridSelection(this)

  /** The visible screen's row count. Mutable only through [resize]. */
  val screenRows: Int get() = ring.screenRows

  /** How many rows of scrollback are live. */
  val activeTranscriptRows: Int get() = ring.activeTranscriptRows

  /** The scrollback plus the screen. */
  val activeRows: Int get() = ring.activeRows

  /** Everything the ring holds, screen and scrollback, trimmed. */
  val transcriptText: String
    get() = selectedText(0, -activeTranscriptRows, columns, screenRows).trim()

  init {
    blockSet(0, 0, columns, screenRows, ' '.code, CellStyle.NORMAL)
  }

  /**
   * The row at [externalRow], created blank if nothing has written to it yet. Its `lineWrap` is
   * read and written directly, where the reference wraps that field in three methods because it
   * is package-private there.
   */
  fun rowAt(externalRow: Int): TerminalRow = ring.rowAt(externalRow)

  fun setChar(column: Int, row: Int, codePoint: Int, style: CellStyle) {
    require(row in 0 until screenRows && column in 0 until columns) {
      "cell out of range: row=$row, column=$column, expected row 0 until $screenRows, " +
        "column 0 until $columns"
    }
    ring.rowAt(row).setChar(column, codePoint, style)
  }

  fun styleAt(externalRow: Int, column: Int): CellStyle = ring.rowAt(externalRow).styles.at(column)

  /** Fill a `w` by `h` rectangle with one code point - how the parser erases. */
  fun blockSet(sx: Int, sy: Int, w: Int, h: Int, codePoint: Int, style: CellStyle) {
    require(sx >= 0 && sx + w <= columns && sy >= 0 && sy + h <= screenRows) {
      "block out of range: sx=$sx, sy=$sy, w=$w, h=$h, expected within $columns x $screenRows"
    }
    for (row in 0 until h) {
      for (column in 0 until w) setChar(sx + column, sy + row, codePoint, style)
    }
  }

  /**
   * Copy a `w` by `h` rectangle from `(sx, sy)` to `(dx, dy)`. The two may overlap: rows are
   * copied bottom-up or top-down so that a move never reads what it has already written.
   */
  fun blockCopy(sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) {
    if (w == 0) return
    require(sx >= 0 && sx + w <= columns && sy >= 0 && sy + h <= screenRows) {
      "source out of range: sx=$sx, sy=$sy, w=$w, h=$h, expected within $columns x $screenRows"
    }
    require(dx >= 0 && dx + w <= columns && dy >= 0 && dy + h <= screenRows) {
      "destination out of range: dx=$dx, dy=$dy, w=$w, h=$h, expected within $columns x $screenRows"
    }
    val copyingUp = sy > dy
    for (offset in 0 until h) {
      val row = if (copyingUp) offset else h - (offset + 1)
      ring.rowAt(dy + row).copyInterval(ring.rowAt(sy + row), sx, sx + w, dx)
    }
  }

  /**
   * Scroll `[topMargin, bottomMargin)` down one line. The row leaving the top of the region goes
   * into the scrollback, and the newly revealed one is blanked with [style].
   */
  fun scrollDownOneLine(topMargin: Int, bottomMargin: Int, style: CellStyle) =
    ring.scrollDownOneLine(topMargin, bottomMargin, style)

  /**
   * Resize the screen when the column count is unchanged: `TerminalBuffer.resize`'s fast path
   * (`TerminalBuffer.java:204-232`) - pure index arithmetic, no [TerminalRow] touched. A
   * column-changing resize is P4h-2's reflow; nothing calls this with a different [columns] yet,
   * so that branch is left unwritten rather than guessed at.
   *
   * @return the cursor's row after the resize - a shrink or grow shifts which external row it is on.
   */
  fun resize(newScreenRows: Int, newTotalRows: Int, cursorRow: Int, style: CellStyle): Int =
    ring.resize(newScreenRows, newTotalRows, cursorRow, style)

  /**
   * DECCARA and DECRARA's write: set, clear or flip [bits] over a region, keeping every cell's
   * colours. **Left without a caller at P4e-4 on purpose** - the sequences that need it are P4f-5e's.
   *
   * [rectangular] is DECSACE: false makes the region a *stream*, where only the first row starts at
   * [left] and only the last ends at [right] and the rows between run the full margins.
   */
  @Suppress("LongParameterList") // the reference's own signature; every argument is a distinct edge
  fun setOrClearEffect(
    bits: Int,
    setOrClear: Boolean,
    reverse: Boolean,
    rectangular: Boolean,
    leftMargin: Int,
    rightMargin: Int,
    top: Int,
    left: Int,
    bottom: Int,
    right: Int,
  ) {
    // The reference indexes `mLines` directly here where every one of its other six row accesses
    // allocates, and its `bottom` can exceed the screen - so `CSI ? 6 h`, `CSI 3;4 r`,
    // `CSI 1;1;4;4;1 $ r` on four rows walks off the screen and throws. Clamped, which is the
    // smallest change that makes the region defined and a no-op wherever the reference works.
    // See `evidence/p4-terminal/deccara-past-the-last-row.md`.
    for (row in top until minOf(bottom, screenRows)) {
      val line = ring.rowAt(row)
      val from = if (rectangular || row == top) left else leftMargin
      val to = if (rectangular || row + 1 == bottom) right else rightMargin
      for (column in from until to) {
        line.styles.set(column, effectApplied(line.styles.at(column), bits, setOrClear, reverse))
      }
    }
  }

  /** A reverse flips only the bits it is given; otherwise they are set or cleared outright. */
  private fun effectApplied(style: CellStyle, bits: Int, setOrClear: Boolean, reverse: Boolean): CellStyle {
    val effect = when {
      reverse -> (style.effect and bits.inv()) or (bits and style.effect.inv())
      setOrClear -> style.effect or bits
      else -> style.effect and bits.inv()
    }
    return CellStyle.encode(style.foreColor, style.backColor, effect)
  }

  /** Forget the scrollback, keeping the screen. */
  fun clearTranscript() = ring.clearTranscript()

  /**
   * The text of columns `[x1, x2]` on rows `[y1, y2]`, both inclusive.
   *
   * [joinBackLines] treats a row that wrapped and its continuation as one line, which is what a
   * copy out of a terminal wants; [joinFullLines] does the same for a row that merely fills its
   * width.
   */
  fun selectedText(
    x1: Int,
    y1: Int,
    x2: Int,
    y2: Int,
    joinBackLines: Boolean = true,
    joinFullLines: Boolean = false,
  ): String = selection.textIn(x1, y1, x2, y2, joinBackLines, joinFullLines)
}
