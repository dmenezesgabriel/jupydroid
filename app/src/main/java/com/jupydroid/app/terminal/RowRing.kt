package com.jupydroid.app.terminal

/**
 * The circular buffer of [TerminalRow]s behind a terminal screen, and the map between the rows a
 * caller names and the slots they live in.
 *
 * Transcribed from the ring half of
 * `terminal-emulator/src/main/java/com/termux/terminal/TerminalBuffer.java` (termux-app
 * `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 under that repository's `LICENSE.md`
 * exception); [TerminalGrid] is the screen written through it and `GridSelection` reads text back
 * out. Split for the reason [RowText] is: one class for the storage, one for what is stored.
 *
 * **Two coordinate systems.** External runs `-activeTranscriptRows` through `screenRows - 1`, with
 * the visible screen at `0..screenRows-1` and scrollback below zero. Internal is the slot in
 * [lines]: the screen occupies `screenRows` slots from `screenFirstRow`, and the transcript is the
 * `activeTranscriptRows` slots ending just before it, wrapping.
 *
 * Scrolling moves `screenFirstRow`, never the rows themselves - which is the whole point of the
 * ring, and why a screen of scrolling output costs no copying.
 */
internal class RowRing(val columns: Int, val totalRows: Int, val screenRows: Int) {
  /** Slots, lazily filled: a row nothing has written to is null until something asks for it. */
  private val lines = arrayOfNulls<TerminalRow>(totalRows)

  /** How many rows of scrollback are live, i.e. how far below zero an external row may go. */
  var activeTranscriptRows: Int = 0
    private set

  private var screenFirstRow: Int = 0

  /** The scrollback plus the screen. */
  val activeRows: Int get() = activeTranscriptRows + screenRows

  /** The slot [externalRow] currently lives in. */
  fun externalToInternalRow(externalRow: Int): Int {
    require(externalRow >= -activeTranscriptRows && externalRow <= screenRows) {
      "row out of range: externalRow=$externalRow, expected -$activeTranscriptRows..$screenRows"
    }
    val internalRow = screenFirstRow + externalRow
    return if (internalRow < 0) totalRows + internalRow else internalRow % totalRows
  }

  /** The row at [externalRow], created blank if nothing has written to it yet. */
  fun rowAt(externalRow: Int): TerminalRow = allocate(externalToInternalRow(externalRow))

  /** The row in slot [internalRow], created blank if nothing has written to it yet. */
  fun allocate(internalRow: Int): TerminalRow =
    lines[internalRow] ?: TerminalRow(columns, CellStyle(0L)).also { lines[internalRow] = it }

  /**
   * Scroll the region `[topMargin, bottomMargin)` down one line, pushing the row above the top
   * margin into the transcript and blanking the newly revealed one with [style].
   */
  fun scrollDownOneLine(topMargin: Int, bottomMargin: Int, style: CellStyle) {
    require(topMargin <= bottomMargin - 1 && topMargin >= 0 && bottomMargin <= screenRows) {
      "scroll region out of range: topMargin=$topMargin, bottomMargin=$bottomMargin, " +
        "expected 0 <= topMargin < bottomMargin <= $screenRows"
    }
    // The lines outside the region stay where they are on screen, so they move with the ring.
    rotateDown(screenFirstRow, topMargin)
    rotateDown(externalToInternalRow(bottomMargin), screenRows - bottomMargin)

    screenFirstRow = (screenFirstRow + 1) % totalRows
    if (activeTranscriptRows < totalRows - screenRows) activeTranscriptRows++

    val blankRow = externalToInternalRow(bottomMargin - 1)
    lines[blankRow]?.clear(style) ?: run { lines[blankRow] = TerminalRow(columns, style) }
  }

  /** Forget the scrollback, keeping the screen. */
  fun clearTranscript() {
    if (screenFirstRow < activeTranscriptRows) {
      lines.fill(null, totalRows + screenFirstRow - activeTranscriptRows, totalRows)
      lines.fill(null, 0, screenFirstRow)
    } else {
      lines.fill(null, screenFirstRow - activeTranscriptRows, screenFirstRow)
    }
    activeTranscriptRows = 0
  }

  /** Move [length] slots from [from] one slot down, wrapping the one they displace to the top. */
  private fun rotateDown(from: Int, length: Int) {
    if (length == 0) return
    val displaced = lines[(from + length) % totalRows]
    for (offset in length - 1 downTo 0) {
      lines[(from + offset + 1) % totalRows] = lines[(from + offset) % totalRows]
    }
    lines[from % totalRows] = displaced
  }
}
