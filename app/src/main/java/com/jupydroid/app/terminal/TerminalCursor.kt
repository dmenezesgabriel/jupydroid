package com.jupydroid.app.terminal

/**
 * Where the next character goes, the margins that bound it, and the tab stops.
 *
 * Transcribed from the cursor and margin fields of `TerminalEmulator.java` (termux-app
 * `3df69d1da197dd9bd71a3bafd902dffd720576b4`, Apache 2.0 under that repository's `LICENSE.md`
 * exception), split out of the emulator because a parser that also holds the cursor is the fusion
 * JediTerm avoids by keeping `JediEmulator` and `JediTerminal` apart.
 *
 * **There are two ways to move, and the difference is load-bearing.** [moveToRow], [moveToColumn]
 * and [moveTo] clear [aboutToAutoWrap], because an addressed cursor is no longer pending a wrap;
 * assigning [row] or [column] directly does not, which is what [TerminalScreen]'s write path needs
 * while it is placing a character. The reference has the same pair - its setters against its bare
 * field assignments - without naming it.
 *
 * ```
 * val cursor = TerminalCursor(columns = 80, rows = 24)
 * cursor.moveTo(row = 5, column = 10)
 * ```
 */
class TerminalCursor(private var columns: Int, private var rows: Int) {
  /** 0 is the top row of the screen. */
  var row: Int = 0

  /** 0 is the leftmost column. */
  var column: Int = 0

  /**
   * Set when a character landed in the last column: the cursor stayed there, and the *next*
   * character wraps. It is what tells "sitting on the last column" apart from "past it".
   */
  var aboutToAutoWrap: Boolean = false

  /** The scrolling region, `[topMargin, bottomMargin)`. */
  var topMargin: Int = 0
  var bottomMargin: Int = rows

  /** The horizontal margins, `[leftMargin, rightMargin)`. */
  var leftMargin: Int = 0
  var rightMargin: Int = columns

  /** The tab stops, which read this cursor's column and margins. See [TabStops]. */
  val tabs = TabStops(this, columns)

  fun moveToRow(newRow: Int) {
    row = newRow
    aboutToAutoWrap = false
  }

  fun moveToColumn(newColumn: Int) {
    column = newColumn
    aboutToAutoWrap = false
  }

  /** Move to an absolute position, clamped to the screen - margins are not consulted. */
  fun moveTo(newRow: Int, newColumn: Int) {
    row = newRow.coerceIn(0, rows - 1)
    column = newColumn.coerceIn(0, columns - 1)
    aboutToAutoWrap = false
  }

  /**
   * `setCursorPosition`: move to a position **stated relative to the margins when origin mode is
   * on**, and to the screen's own corner when it is off, clamped to whichever of the two applies.
   *
   * P4f-3b wrote the five sequences that go through this as their origin-mode-off case, because no
   * margin could be set then and the two are identical without one. [ScrollMargins] makes them
   * differ, so they are re-pointed here.
   */
  fun moveWithinMargins(newRow: Int, newColumn: Int, originMode: Boolean) {
    val top = if (originMode) topMargin else 0
    val bottom = if (originMode) bottomMargin else rows
    val left = if (originMode) leftMargin else 0
    val right = if (originMode) rightMargin else columns
    moveTo((top + newRow).coerceIn(top, bottom - 1), (left + newColumn).coerceIn(left, right - 1))
  }

  /** DECLRMM off, and part of what DECCOLM resets: the row keeps its margins, the columns do not. */
  fun resetHorizontalMargins() {
    leftMargin = 0
    rightMargin = columns
  }

  /**
   * `ESC F`. **A declared divergence**: the reference transposes the two arguments of its own
   * `setCursorRowCol` here, so its "lower left" is row 0 at the column its bottom margin names.
   *
   */
  fun moveToLowerLeft() = moveTo(bottomMargin - 1, 0)

  fun resetMargins() {
    topMargin = 0
    leftMargin = 0
    bottomMargin = rows
    rightMargin = columns
  }

  /**
   * `TerminalEmulator.resize`'s two conditionals (`:395-408`): [rows] and its DECSTBM margins reset
   * together when the row count actually changes, [columns] and its DECSLRM margins together with
   * [tabs]' rebuild when the column count does. Neither [row] nor [column] is touched here - the
   * reference does not clamp them in this method either. What [TerminalGrid.resize] returns is what
   * [TerminalScreen.resize] assigns back; a second clamp here would only repeat the mistake
   * an earlier finding already found and centralised away from.
   */
  fun resize(newColumns: Int, newRows: Int) {
    if (rows != newRows) {
      rows = newRows
      topMargin = 0
      bottomMargin = rows
    }
    if (columns != newColumns) {
      columns = newColumns
      leftMargin = 0
      rightMargin = columns
      tabs.resize(columns)
    }
  }
}
