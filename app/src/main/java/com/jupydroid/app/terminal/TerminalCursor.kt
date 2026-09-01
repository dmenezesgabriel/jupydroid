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
class TerminalCursor(private val columns: Int, private val rows: Int) {
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

  /** DECLRMM off, and part of what DECCOLM resets: the row keeps its margins, the columns do not. */
  fun resetHorizontalMargins() {
    leftMargin = 0
    rightMargin = columns
  }

  /**
   * `ESC F`. **A declared divergence**: the reference transposes the two arguments of its own
   * `setCursorRowCol` here, so its "lower left" is row 0 at the column its bottom margin names.
   * `docs/DECISIONS.md` carries xterm's wording and the measurement.
   */
  fun moveToLowerLeft() = moveTo(bottomMargin - 1, 0)

  fun resetMargins() {
    topMargin = 0
    leftMargin = 0
    bottomMargin = rows
    rightMargin = columns
  }
}
