package com.jupydroid.app.terminal

/**
 * Where a tab lands: the stops themselves, and the search in each direction from the cursor.
 *
 * Transcribed from `TerminalEmulator`'s `mTabStop` array, `setDefaultTabStops` and the `nextTabStop`
 * / CBT pair (termux-app `3df69d1d`, Apache 2.0 - see [TerminalGrid]). **Split out of
 * [TerminalCursor]**, on detekt's `TooManyFunctions` - the cursor was at eleven of eleven and the
 * margins would have tripped it anyway. The seam is real rather than
 * arithmetic: where the cursor *is* and where a tab would take it are two questions, and only the
 * second needs an array.
 *
 * It reads the cursor rather than being told, because every stop is relative to where the cursor
 * stands and both margins bound the answer.
 *
 * ```
 * cursor.tabs.next(1)   // the column a single HT would reach
 * ```
 */
class TabStops(private val cursor: TerminalCursor, private var columns: Int) {
  private var stops = BooleanArray(columns)

  init {
    reset()
  }

  /** The column [count] tab stops to the right, never past the right margin. */
  fun next(count: Int): Int {
    var remaining = count
    for (candidate in cursor.column + 1 until columns) {
      if (stops[candidate] && --remaining == 0) return minOf(candidate, cursor.rightMargin)
    }
    return cursor.rightMargin - 1
  }

  /** The column [count] tab stops to the left, never past the left margin. */
  fun previous(count: Int): Int {
    var remaining = count
    for (candidate in cursor.column - 1 downTo 0) {
      if (stops[candidate] && --remaining == 0) return maxOf(candidate, cursor.leftMargin)
    }
    return cursor.leftMargin
  }

  /** HTS. A stop where the cursor stands, which [next] then finds. */
  fun setAtCursor() {
    stops[cursor.column] = true
  }

  /** TBC 0. */
  fun clearAtCursor() {
    stops[cursor.column] = false
  }

  /** TBC 3. Where [reset] puts the eight-column defaults back, this leaves none at all. */
  fun clearAll() {
    stops.fill(false)
  }

  /** A stop every eight columns, and never at column 0. */
  fun reset() {
    for (candidate in 0 until columns) stops[candidate] = candidate % TAB_WIDTH == 0 && candidate != 0
  }

  /**
   * `TerminalEmulator.resize`'s tab-stop rebuild (`:399-403`): a fresh eight-column default at
   * [newColumns], with whatever was actually set below `min(columns, newColumns)` copied back over
   * it - the reference's own order, `setDefaultTabStops()` then `arraycopy` the old array's prefix
   * on top, so a custom stop below the old width survives and everything past it reverts to default.
   */
  fun resize(newColumns: Int) {
    val old = stops
    columns = newColumns
    stops = BooleanArray(newColumns)
    reset()
    old.copyInto(stops, 0, 0, minOf(old.size, newColumns))
  }

  private companion object {
    const val TAB_WIDTH = 8
  }
}
