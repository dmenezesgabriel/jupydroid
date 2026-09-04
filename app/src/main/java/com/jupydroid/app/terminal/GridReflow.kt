package com.jupydroid.app.terminal

/**
 * Rebuilds a grid's ring at a new size, replaying every row - screen and scrollback both - through
 * the grid's normal write surface so cursor addressing, wide characters and combining marks land
 * exactly where a shell's own reflow would put them.
 *
 * Transcribed from `TerminalBuffer.resize`'s `else` branch (`TerminalBuffer.java:233-350`,
 * termux-app `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 under that repository's
 * `LICENSE.md` exception - see [TerminalGrid]): the fast, rows-only path is [RowRing.resize]; this
 * is the general one, taken whenever the column count changes, or [grid] is shaped like the
 * alternate screen (`totalRows == screenRows`) and grows - which fails the fast path's own guard
 * even though its columns never move.
 *
 * Reads [oldRing] directly - the only reader left once [TerminalGrid.resize] has already swapped a
 * fresh ring into [grid] - and writes back through [grid]'s own public surface
 * ([TerminalGrid.setChar], [TerminalGrid.scrollDownOneLine], `rowAt(...).lineWrap`) rather than a
 * second ring's internals, mirroring how the reference's own slow path calls back into its own
 * `setChar`/`scrollDownOneLine`/`setLineWrap`.
 */
internal class GridReflow(private val oldRing: RowRing, private val grid: TerminalGrid) {
  private var outputRow = 0
  private var outputColumn = 0
  private var newCursorRow = -1
  private var newCursorColumn = -1
  private var cursorPlaced = false
  private var styleAtColumn = CellStyle(0L)

  /**
   * Replay every old row and report where ([cursorRow], [cursorColumn]) landed - `(0, 0)` if the
   * old cursor's own row and column were never matched, the reference's own shared tail
   * (`TerminalBuffer.java:352-353`) applied to both coordinates rather than the row alone, since a
   * reflow can move either.
   */
  fun replay(cursorRow: Int, cursorColumn: Int, style: CellStyle): GridCursor {
    var skippedBlankRows = 0
    for (oldRow in -oldRing.activeTranscriptRows until oldRing.screenRows) {
      val line = oldRing.rowAt(oldRow)
      val protectsCursor = oldRow == cursorRow && !cursorPlaced
      if (line.isBlank() && !protectsCursor) {
        skippedBlankRows++
        continue
      }
      repeat(skippedBlankRows) { advanceOutputRow(style, adjustPlacedCursor = false) }
      skippedBlankRows = 0
      replayRow(line, oldRow, cursorRow, cursorColumn, style)
    }
    return GridCursor.clamped(newCursorRow, newCursorColumn)
  }

  /** Take the whole line for the cursor's row or a wrapped one; trim trailing blanks otherwise. */
  private fun replayRow(line: TerminalRow, oldRow: Int, cursorRow: Int, cursorColumn: Int, style: CellStyle) {
    val cursorOnThisRow = oldRow == cursorRow
    val end = if (cursorOnThisRow || line.lineWrap) line.spaceUsed else trimmedEnd(line)
    writeCharacters(line, end, cursorOnThisRow, cursorColumn, style)
    if (oldRow != oldRing.screenRows - 1 && !line.lineWrap) advanceOutputRow(style)
  }

  /** The java-char index one past the last non-space column - trailing blanks are not copied. */
  private fun trimmedEnd(line: TerminalRow): Int {
    var lastNonSpace = 0
    for (index in 0 until line.spaceUsed) if (line.text[index] != ' ') lastNonSpace = index + 1
    return lastNonSpace
  }

  /**
   * Walk [line]'s text up to [end] one code point at a time, wrapping the output row whenever one
   * would overflow the grid's new width, and stop the moment the old cursor's own column is placed
   * if [cursorOnThisRow] - the reference's own `justToCursor`, true for the cursor's row whether or
   * not it is also wrapped, which can drop a wrapped row's tail past the cursor. Transcribed as the
   * reference has it; the differential is what would catch this being wrong, not a re-reading.
   */
  private fun writeCharacters(
    line: TerminalRow,
    end: Int,
    cursorOnThisRow: Boolean,
    cursorColumn: Int,
    style: CellStyle,
  ) {
    var charIndex = 0
    var oldColumn = 0
    styleAtColumn = CellStyle(0L)
    while (charIndex < end) {
      val codePoint = codePointAt(line.text, charIndex)
      val width = CharacterWidth.of(codePoint)
      if (width > 0) styleAtColumn = line.styles.at(oldColumn)
      placeCodePoint(codePoint, width, style)
      charIndex += charCountAt(line.text, charIndex)
      if (width <= 0) continue
      if (matchCursorAndAdvance(cursorOnThisRow, oldColumn, cursorColumn, width)) return
      oldColumn += width
    }
  }

  /** Wrap the output row first if [codePoint] would overflow it, then write it at [outputColumn]. */
  private fun placeCodePoint(codePoint: Int, width: Int, style: CellStyle) {
    if (outputColumn + width > grid.columns) {
      grid.rowAt(outputRow).lineWrap = true
      advanceOutputRow(style)
    }
    val combiningOffset = if (width <= 0 && outputColumn > 0) 1 else 0
    grid.setChar(outputColumn - combiningOffset, outputRow, codePoint, styleAtColumn)
  }

  /**
   * Record the cursor if it sits at [oldColumn], advance past this character's width, and say
   * whether that was the character [writeCharacters] was told to stop at.
   */
  private fun matchCursorAndAdvance(cursorOnThisRow: Boolean, oldColumn: Int, cursorColumn: Int, width: Int): Boolean {
    if (cursorOnThisRow && oldColumn == cursorColumn) {
      newCursorRow = outputRow
      newCursorColumn = outputColumn
      cursorPlaced = true
    }
    outputColumn += width
    return cursorOnThisRow && cursorPlaced
  }

  /**
   * At the bottom of the new screen, scroll a fresh line in - decrementing an already-placed
   * cursor's row so it stays correct - and reset the output column; otherwise just move down one.
   * The blank-line reinsertion loop in [replay] passes [adjustPlacedCursor] `false`: the reference's
   * three copies of this branch (`TerminalBuffer.java:276-280` vs `:312-317`/`:338-343`) agree
   * everywhere except there, and this transcribes that difference rather than smoothing it over.
   */
  private fun advanceOutputRow(style: CellStyle, adjustPlacedCursor: Boolean = true) {
    if (outputRow == grid.screenRows - 1) {
      if (adjustPlacedCursor && cursorPlaced) newCursorRow--
      grid.scrollDownOneLine(0, grid.screenRows, style)
    } else {
      outputRow++
    }
    outputColumn = 0
  }
}
