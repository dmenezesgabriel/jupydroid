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
internal class RowRing(val columns: Int, capacity: Int, screenRows: Int) {
  /** Slots, lazily filled: a row nothing has written to is null until something asks for it. */
  private val lines = arrayOfNulls<TerminalRow>(capacity)

  /**
   * The logical size of the ring - always `<= lines.size`. [resize] only ever narrows it; a resize
   * that needs more slots than [lines] was allocated at is P4h-2's reflow, which reallocates.
   */
  var totalRows: Int = capacity
    private set

  /** How many rows of scrollback are live, i.e. how far below zero an external row may go. */
  var activeTranscriptRows: Int = 0
    private set

  var screenRows: Int = screenRows
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

  /**
   * Resize the screen with the column count held fixed - `TerminalBuffer.resize`'s fast path
   * (`TerminalBuffer.java:205-232`): pure index arithmetic, no [TerminalRow] is read or written
   * except to blank a row a grow reveals beyond what the transcript can supply.
   *
   * A shrink first tries to absorb blank rows below [cursorRow] rather than filing them into
   * history; what's left shifts [screenFirstRow] and grows [activeTranscriptRows]. A grow pulls
   * rows back out of the transcript where it can, and blanks with [style] whatever the transcript
   * can't cover.
   *
   * The reference forces `mActiveTranscriptRows` to zero here when told this is the alternate
   * screen's buffer, since that one is built with `totalRows == screenRows` and so is never
   * meant to carry history. Clamping the result to `newTotalRows - newScreenRows` gets the same
   * effect without a flag: that clamp is `0` exactly when the two are equal, and is unreachable
   * for a ring shaped like the main screen's, where the invariant `activeTranscriptRows <=
   * totalRows - screenRows` already holds before every call and this arithmetic never grows the
   * left side past what the right side allows.
   *
   * @return the cursor's row, shifted by the same amount the screen was and reset to `0` if that
   * shift carried it above the screen - `TerminalBuffer.resize`'s own common tail (`:352-353`),
   * shared by both its paths, resets the cursor whenever either coordinate goes negative; only the
   * row can here; the column, unmoved by a rows-only resize, is the caller's to have kept valid.
   */
  fun resize(newScreenRows: Int, newTotalRows: Int, cursorRow: Int, style: CellStyle): Int {
    var shift = screenRows - newScreenRows
    shift =
      when {
        shift > 0 && shift < screenRows -> shrinkBelowCursor(shift, cursorRow)
        shift < 0 -> growFromTranscript(shift, style)
        else -> shift
      }
    val shifted = screenFirstRow + shift
    screenFirstRow = if (shifted < 0) shifted + totalRows else shifted % totalRows
    totalRows = newTotalRows
    activeTranscriptRows = minOf(maxOf(0, activeTranscriptRows + shift), newTotalRows - newScreenRows)
    screenRows = newScreenRows
    return maxOf(0, cursorRow - shift)
  }

  /** Try to absorb blank rows below [cursorRow] into the shrink rather than filing them as history. */
  private fun shrinkBelowCursor(shift: Int, cursorRow: Int): Int {
    val blankRowsBelowCursor =
      (screenRows - 1 downTo 1).takeWhile { cursorRow < it }.count { row ->
        val line = lines[externalToInternalRow(row)]
        line == null || line.isBlank()
      }
    return shift - minOf(blankRowsBelowCursor, shift)
  }

  /** Pull as many newly-revealed rows out of the transcript as exist; blank the rest with [style]. */
  private fun growFromTranscript(shift: Int, style: CellStyle): Int {
    val actualShift = maxOf(shift, -activeTranscriptRows)
    for (offset in 0 until actualShift - shift) {
      allocate((screenFirstRow + screenRows + offset) % totalRows).clear(style)
    }
    return actualShift
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
