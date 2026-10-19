package com.jupydroid.app.ui.terminal

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.IntSize
import com.jupydroid.app.terminal.TerminalGrid
import com.jupydroid.app.terminal.wordColumnsAt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * A span of the terminal's text, from ([startColumn], [startRow]) to ([endColumn], [endRow]), both
 * ends inclusive, in the grid's external rows (negative is scrollback) - the four `mSel*` fields of
 * `TextSelectionCursorController` (termux-app `3df69d1d`).
 *
 * ```
 * TerminalTextSelection(startColumn = 3, startRow = 1, endColumn = 5, endRow = 1).covers(4, 1)   // true
 * ```
 */
data class TerminalTextSelection(val startColumn: Int, val startRow: Int, val endColumn: Int, val endRow: Int) {
  /** Whether the cell at ([column], [row]) is drawn as selected - `TerminalRenderer.render`'s `insideSelection`. */
  fun covers(column: Int, row: Int): Boolean = when {
    row < startRow || row > endRow -> false
    row == startRow && column < startColumn -> false
    else -> row != endRow || column <= endColumn
  }

  fun textIn(grid: TerminalGrid): String = grid.selectedText(startColumn, startRow, endColumn, endRow)

  /** The same span, [rows] higher, for text that output has moved up. */
  fun movedUp(rows: Int): TerminalTextSelection = copy(startRow = startRow - rows, endRow = endRow - rows)

  /**
   * The pixels this span covers, from the top-left of column `0` of a view whose top row is
   * [topRow]: what the platform's floating toolbar is placed against, as
   * `TextSelectionCursorController.onGetContentRect` (termux-app `3df69d1d`) does. A span over
   * several rows covers them across all [columns].
   */
  fun boundsIn(cell: IntSize, columns: Int, topRow: Int): Rect {
    val oneRow = startRow == endRow
    val left = if (oneRow) startColumn else 0
    val right = if (oneRow) endColumn + 1 else columns
    return Rect(
      left = (left * cell.width).toFloat(),
      top = ((startRow - topRow) * cell.height).toFloat(),
      right = (right * cell.width).toFloat(),
      bottom = ((endRow - topRow + 1) * cell.height).toFloat(),
    )
  }
}

/**
 * The terminal's one selection: made by a long press, read by Copy, and moved along with the text
 * as output scrolls it. Its own class because `TerminalPaneViewModel` is at detekt's
 * `TooManyFunctions` cap.
 *
 * [grid] is read on every call rather than held, since the live grid is the alternate one while a
 * full-screen program runs.
 *
 * ```
 * val selector = TerminalTextSelector { emulator.screen.grid }
 * selector.selectWordAt(column = 4, row = 1)
 * selector.selectedText()   // "-la" over "ls -la x"
 * ```
 */
class TerminalTextSelector(private val grid: () -> TerminalGrid) {
  private val _selection = MutableStateFlow<TerminalTextSelection?>(null)
  val selection: StateFlow<TerminalTextSelection?> = _selection.asStateFlow()

  /** What a long press selects first: the word under it ([wordColumnsAt]). */
  fun selectWordAt(column: Int, row: Int) {
    val word = grid().wordColumnsAt(column, row)
    _selection.value = TerminalTextSelection(word.first, row, word.last, row)
  }

  fun clear() {
    _selection.value = null
  }

  /** The selected text, or `null` when nothing is selected. */
  fun selectedText(): String? = _selection.value?.textIn(grid())

  /** Copy: the selected text goes to [copyToClipboard] and the selection ends - the reference's `ACTION_COPY`. */
  fun copyInto(copyToClipboard: (String) -> Unit) {
    selectedText()?.let(copyToClipboard)
    clear()
  }

  /**
   * Where the view's top row goes after output moved the content up [rowShift] rows.
   * `TerminalView.onScreenUpdated` (termux-app `3df69d1d`): with nothing selected the view returns
   * to the live screen (`0`); with a selection the view and the selection both move up with the
   * text, unless that would reach past the oldest row kept, where the selection is given up.
   */
  fun topRowAfterOutput(topRow: Int, rowShift: Int): Int {
    val selection = _selection.value
    val held = selection != null && -topRow + rowShift <= grid().activeTranscriptRows
    _selection.value = selection?.takeIf { held }?.movedUp(rowShift)
    return if (held) topRow - rowShift else 0
  }
}
