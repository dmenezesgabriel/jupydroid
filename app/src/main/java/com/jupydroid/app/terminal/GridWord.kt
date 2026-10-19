package com.jupydroid.app.terminal

/**
 * The columns of the word under ([column], [row]): every printing neighbour on that row, both ways,
 * which is what a long press selects first. `TextSelectionCursorController
 * .setInitialTextSelectionPosition` (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]).
 *
 * **A blank cell is selected alone.** The reference grows from a blank cell too, so a press on the
 * space after `ab` takes `ab `; selecting nothing wider than what was pressed is the plainer answer.
 *
 * An extension rather than a member: [TerminalGrid] is at detekt's `TooManyFunctions` cap.
 *
 * ```
 * grid.wordColumnsAt(column = 4, row = 0)   // 3..5 over "ab cde f"
 * ```
 */
fun TerminalGrid.wordColumnsAt(column: Int, row: Int): IntRange {
  if (!printsAt(column, row)) return column..column
  var first = column
  while (first > 0 && printsAt(first - 1, row)) first--
  var last = column
  while (last < columns - 1 && printsAt(last + 1, row)) last++
  return first..last
}

/**
 * Not blank rather than not empty: a wrapped row reads a blank last column back as a space, since
 * [GridSelection] keeps a wrapped row's trailing spaces.
 */
private fun TerminalGrid.printsAt(column: Int, row: Int): Boolean = selectedText(column, row, column, row).isNotBlank()
