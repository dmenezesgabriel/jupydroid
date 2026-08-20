package com.jupydroid.app.ui.editor

/**
 * Where in the kernel's input history the two `access-*-history-entry` commands have walked to.
 *
 * `packages/notebook/src/history.ts`, with the fetching left outside: this is the list and the
 * position in it, and nothing here suspends. What it is *not* is a log of this notebook - the
 * entries come off the kernel (`KernelGateway.executionHistory`), so they include cells that have
 * since been deleted and sittings this document was never open for.
 *
 * **The list is not the history.** `setFilter` (`history.ts:355-368`) does three things, and each
 * of them changes what a tap does: a run of the same input counts once, the text already in the
 * cell is *removed*, and that same text is then pushed on as the last entry - which is what makes
 * walking forward off the end restore what was being typed rather than leave the cell holding
 * somebody's old code.
 *
 * ```
 * val cursor = NotebookHistoryCursor(kernelHistory, placeholder = cell.source)
 * if (cursor.back()) input.replaceAll(cursor.current)
 * ```
 */
internal class NotebookHistoryCursor(entries: List<String>, private val placeholder: String) {
  private var filtered = filteredFor(entries)

  /**
   * Always a valid index, where the reference's may go to -1 and be clamped afterwards
   * (`history.ts:181-185`). The overshoot is what tells it to fetch; here [back] says so by
   * answering false, so there is no invalid state to repair.
   */
  private var cursor = filtered.lastIndex

  /** The entry to put in the cell. Never null: the placeholder is always the last entry. */
  val current: String get() = filtered[cursor]

  /**
   * Steps one entry older.
   *
   * False when there is none - the cursor is already on the oldest entry it knows, and asking the
   * kernel for a longer tail is the caller's job ([grownTo]). The reference reaches the same place
   * by going to -1, fetching, and clamping.
   */
  fun back(): Boolean {
    if (cursor == 0) return false
    cursor--
    return true
  }

  /** Steps one entry newer, stopping on the placeholder (`history.ts:201`). */
  fun forward() {
    if (cursor < filtered.lastIndex) cursor++
  }

  /**
   * Takes a longer tail of the same history and lands one entry older than the oldest this cursor
   * had - `fetchBatch` (`history.ts:247-273`), which is only meaningful straight after [back] has
   * refused.
   *
   * **The arithmetic is not just a length difference**, and [shiftAgainst] is why: the kernel may
   * have run more cells between the two asks, so the entries this cursor already walked are no
   * longer at the end of the list. Counting only the extra length would land back on one of them.
   *
   * False when the longer tail held nothing older after filtering, which is the caller's cue to
   * stop asking - the reference's own `_history.length > oldHistory.length` guard (`:269`). The
   * cursor still rests on something real, because a control that walks off its list is worse than
   * one that stops.
   */
  fun grownTo(entries: List<String>): Boolean {
    val had = filtered
    filtered = filteredFor(entries)
    val landed = filtered.size - (had.size + 1) - shiftAgainst(had)
    cursor = landed.coerceIn(0, filtered.lastIndex)
    return landed >= 0
  }

  /**
   * How many entries arrived *newer* than the ones already walked - the reference's `cursorOffset`
   * (`history.ts:253-264`), transcribed including its walk from the newest end.
   */
  private fun shiftAgainst(had: List<String>): Int {
    var offset = 0
    val walked = had.asReversed()
    val now = filtered.asReversed()
    for (i in walked.indices) {
      var at = i + offset
      while (at < now.size && walked[i] != now[at]) {
        offset++
        at++
      }
    }
    return offset
  }

  /**
   * `setFilter` (`history.ts:355-368`).
   *
   * `last` starts empty and moves only when something is kept, which is the reference's own
   * shape and the reason a repeat survives as long as something else came between.
   */
  private fun filteredFor(entries: List<String>): List<String> {
    val kept = mutableListOf<String>()
    var last = ""
    for (entry in entries) {
      if (entry == last || entry == placeholder) continue
      last = entry
      kept += entry
    }
    // The placeholder last, always: walking forward off the end is how what was being typed comes
    // back, and without it the cell would be left holding a history entry with no way out.
    return kept + placeholder
  }
}

/**
 * Where `updateEditor` leaves the caret after pasting an entry: the end of its **first** line
 * (`packages/notebook/src/history.ts:225-230`).
 *
 * Not the end of the text, which is what a naive paste would do. A multi-line entry is usually
 * being recalled to have its *head* edited - a function's signature, a loop's range - and a caret
 * parked after the last line of a block is nowhere near it.
 *
 * ```
 * endOfFirstLine("def f():\n    pass")   // 8
 * ```
 */
internal fun endOfFirstLine(text: String): Int = text.indexOf('\n').takeIf { it >= 0 } ?: text.length
