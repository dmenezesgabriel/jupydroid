package com.jupydroid.app.terminal

/**
 * The cursor's position after a resize - both coordinates, since a reflow can move either where
 * the fast, rows-only path ([RowRing.resize]) only ever moves the row.
 */
data class GridCursor(val row: Int, val column: Int) {
  companion object {
    /**
     * `TerminalBuffer.resize`'s own shared tail (`:352-353`): either coordinate landing negative
     * resets *both* to `0`, not just the one that went there. [RowRing.resize] returns a raw,
     * possibly-negative row for exactly this reason - clamping it alone, before a caller's column
     * is known, would leave that column untouched where the reference zeroes it too.
     */
    fun clamped(row: Int, column: Int): GridCursor =
      if (row < 0 || column < 0) GridCursor(0, 0) else GridCursor(row, column)
  }
}

/**
 * A terminal's screen and its scrollback: [screenRows] visible rows of [columns] columns, over a
 * ring of [RowRing] slots that also holds what has scrolled off the top.
 *
 * Transcribed from `terminal-emulator/src/main/java/com/termux/terminal/TerminalBuffer.java`
 * (`termux-app@3df69d1d`, commit `3df69d1da197dd9bd71a3bafd902dffd720576b4`,
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
class TerminalGrid(columns: Int, totalRows: Int, screenRows: Int) {
  /** Mutable only through [resize] - a reflow replaces this along with [ring]. */
  var columns: Int = columns
    private set

  private var ring = RowRing(columns, totalRows, screenRows)
  private val selection = GridSelection(this)

  /** The visible screen's row count. Mutable only through [resize]. */
  val screenRows: Int get() = ring.screenRows

  /** `mMainBuffer.mTotalRows`'s equivalent read: the ring's current capacity, scrollback included. */
  val totalRows: Int get() = ring.totalRows

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
   * Resize the screen. `TerminalBuffer.resize`'s own guard (`:204`) - the column count unchanged
   * and the new row count no larger than the ring's *current* capacity - is [RowRing.resize]'s
   * pure index arithmetic. Everything else rebuilds through [GridReflow]: a column
   * change, or a grow of a ring shaped like the alternate screen (`totalRows == screenRows`, which
   * fails the guard's second half even though its columns never move - `ScreenBuffers` is what
   * builds a ring that shape).
   *
   * A reflow's fresh ring starts out only as blank as [TerminalGrid]'s own constructor makes a new
   * one - the visible [newScreenRows] filled with [style], the rest left to allocate lazily the way
   * every ring here already does - which is [GridReflow]'s replay to fill in beyond that.
   *
   * @return the cursor's row and column after the resize - the fast path never moves the column,
   * a reflow can.
   */
  fun resize(
    newColumns: Int,
    newScreenRows: Int,
    newTotalRows: Int,
    cursorRow: Int,
    cursorColumn: Int,
    style: CellStyle,
  ): GridCursor {
    if (newColumns == columns && newScreenRows <= ring.totalRows) {
      return GridCursor.clamped(ring.resize(newScreenRows, newTotalRows, cursorRow, style), cursorColumn)
    }
    val oldRing = ring
    ring = RowRing(newColumns, newTotalRows, newScreenRows)
    columns = newColumns
    blockSet(0, 0, newColumns, newScreenRows, ' '.code, style)
    return GridReflow(oldRing, this).replay(cursorRow, cursorColumn, style)
  }

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

/**
 * [current] shifted by [deltaRows] rows, clamped to what this grid can actually show - no
 * further down than the live screen (`0`), no further up than [TerminalGrid.activeTranscriptRows]
 * - the gesture-scrollback viewport's own arithmetic. Termux's own
 * `TerminalView.doScroll`: `Math.min(0, Math.max(-activeTranscriptRows, mTopRow + delta))`.
 *
 * [current] need not already be in range - a stale value (e.g. after a resize shrinks
 * [TerminalGrid.activeTranscriptRows] out from under it) still lands back inside it in one call,
 * the same defensive re-clamp `TerminalView.onScreenUpdated` applies before trusting `mTopRow`.
 *
 * An extension rather than a member - [TerminalGrid] is already at detekt's `TooManyFunctions`
 * cap (P4h-1's own "spends its one remaining function slot" note), and this needs nothing of the
 * class's own internals beyond the one property it already exposes.
 */
fun TerminalGrid.clampedTopRow(current: Int, deltaRows: Int): Int =
  (current + deltaRows).coerceIn(-activeTranscriptRows, 0)
