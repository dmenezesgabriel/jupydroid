package com.jupydroid.app.terminal

/**
 * A rectangle of a [TerminalGrid] read back as text.
 *
 * Transcribed from `TerminalBuffer.getSelectedText` (termux-app `3df69d1d`, Apache 2.0 - see
 * [TerminalGrid]), split out because turning a region into a string is not writing to it, and
 * because the reference's is 48 lines.
 *
 * **Trailing spaces are dropped, and a wrapped row keeps them** - a row that ran off its right
 * edge is one logical line with the row below it, so the spaces between them are real.
 */
internal class GridSelection(private val grid: TerminalGrid) {
  /**
   * The text of `[x1, x2]` on rows `[y1, y2]` inclusive, one `\n` per logical line.
   *
   * [joinBackLines] treats a wrapped row and its continuation as one line; [joinFullLines] does
   * the same for a row that merely fills its width without the wrap flag.
   */
  fun textIn(x1: Int, y1: Int, x2: Int, y2: Int, joinBackLines: Boolean, joinFullLines: Boolean): String {
    val firstRow = maxOf(y1, -grid.activeTranscriptRows)
    val lastRow = minOf(y2, grid.screenRows - 1)
    val builder = StringBuilder()
    for (row in firstRow..lastRow) {
      val fromColumn = if (row == firstRow) x1 else 0
      val toColumn = if (row == lastRow) minOf(x2 + 1, grid.columns) else grid.columns
      val fillsWidth = appendRow(builder, row, fromColumn, toColumn)
      val wrapped = grid.rowAt(row).lineWrap
      val joined = (joinBackLines && wrapped) || (joinFullLines && fillsWidth)
      if (!joined && row < lastRow && row < grid.screenRows - 1) builder.append('\n')
    }
    return builder.toString()
  }

  /** Append `[fromColumn, toColumn)` of [row], and say whether it reached that far. */
  private fun appendRow(builder: StringBuilder, row: Int, fromColumn: Int, toColumn: Int): Boolean {
    val line = grid.rowAt(row)
    val start = line.findStartOfColumn(fromColumn)
    var end = if (toColumn < grid.columns) line.findStartOfColumn(toColumn) else line.spaceUsed
    // An empty span means the selection ended on the first half of a wide character.
    if (end == start) end = line.findStartOfColumn(toColumn + 1)

    val lastPrinting =
      if (line.lineWrap && toColumn == grid.columns) end - 1 else lastPrintingIndex(line.text, start, end)
    if (lastPrinting >= start) builder.appendRange(line.text, start, lastPrinting + 1)
    return lastPrinting == end - 1
  }

  private fun lastPrintingIndex(chars: CharArray, start: Int, end: Int): Int {
    var last = -1
    for (index in start until end) if (chars[index] != ' ') last = index
    return last
  }
}
