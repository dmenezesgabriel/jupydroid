package com.jupydroid.app.ui.editor

/**
 * Where a query matches in a notebook, and how the find bar counts through them - as pure
 * functions over [EditorUiState].
 *
 * **The reference keeps one search provider per cell and aggregates**: `matchesCount` sums them
 * (`packages/notebook/src/searchprovider.ts:141-146`) and `currentMatchIndex` adds up the counts
 * of every cell before the current one (`:118-136`), which is arithmetic that exists only because
 * the hits are held apart. Held as one list in document order they are already aggregated, so the
 * sums have nothing left to do and the cursor is an index into a list.
 *
 * Named apart from [NotebookSearch] the way [CellEdits] is from [NotebookCellEdits]: this is what
 * a search *is*, and that is what the screen asks of it.
 */

/** One match: the cell it is in, and the span of that cell's source it covers. */
data class SearchHit(val cellId: String, val range: IntRange)

/**
 * Every match in the notebook, in document order.
 *
 * **Every cell, hidden or collapsed.** `startQuery` maps `this.widget.content.widgets` and builds a
 * provider for each with no visibility filter of any kind (`searchprovider.ts:352-386`) - so a
 * match under a collapsed heading is counted here, and reaching it is what reveals it
 * (`activateNewMatch`, and `expandParent` on `activeCellChanged`). Filtering here instead would
 * make the counter lie about a notebook that is merely folded up.
 *
 * All three cell kinds are searched, because all three have an editor the reference's
 * `CellSearchProvider` wraps - a raw cell included (`packages/cells/src/searchprovider.ts:30-58`).
 *
 * ```
 * state.searchHits(regex)   // [SearchHit("c1", 0..2), SearchHit("c3", 7..9)]
 * ```
 */
fun EditorUiState.searchHits(regex: Regex): List<SearchHit> =
  cells.flatMap { cell ->
    regex.findAll(cell.source).map { SearchHit(cell.id, it.range) }
  }

/**
 * Where a freshly typed query lands: the first hit at or after the selected cell, wrapping.
 *
 * `startQuery` ends with `highlightNext(true, { from: 'selection-start', … })` after seeding
 * `_currentProviderIndex` from `activeCellIndex` (`searchprovider.ts:389-401`), so a search opened
 * halfway down a long notebook lands beside what is already on screen rather than jumping to the
 * top. `loop` is true there, which is the wrap.
 */
fun List<SearchHit>.firstHitFrom(state: EditorUiState): Int? {
  if (isEmpty()) return null
  // Positions rather than repeated `indexOf`, so landing costs one walk of the notebook and not
  // one per hit - the same reason [EditorContent] computes its maps once for the whole list.
  val position = state.cells.withIndex().associate { (at, cell) -> cell.id to at }
  // A notebook with no selected cell starts at the top, which is where `activeCellIndex` clamps
  // to on load anyway (`packages/notebook/src/widget.ts:1965-1974`).
  val selectedAt = position[state.selectedCellId] ?: 0
  return indexOfFirst { (position[it.cellId] ?: 0) >= selectedAt }.takeIf { it >= 0 } ?: 0
}

/**
 * The first hit at or after a position in the document, wrapping - where a replacement leaves the
 * cursor.
 *
 * `replaceCurrentMatch` walks the regenerated match list for
 * `allMatches[matchIdx].position >= positionAfterReplacement` and falls through to the next cell
 * when it finds none (`packages/codemirror/src/searchprovider.ts:356-386`, and the notebook's
 * `highlightNext(loop, { from: 'previous-match' })` above it). Both halves are one predicate here,
 * because the hits are already one list in document order.
 *
 * **Offset-aware, and [firstHitFrom] is not.** Landing by cell alone would send a replacement in
 * the *second* of two matches in one cell back to the first, which is backwards.
 */
fun List<SearchHit>.firstHitAtOrAfter(state: EditorUiState, cellId: String, offset: Int): Int? {
  if (isEmpty()) return null
  val position = state.cells.withIndex().associate { (at, cell) -> cell.id to at }
  val from = position[cellId] ?: 0
  return indexOfFirst { hit ->
    val at = position[hit.cellId] ?: 0
    at > from || (at == from && hit.range.first >= offset)
  }.takeIf { it >= 0 } ?: 0
}

/**
 * `highlightNext()` - one forward, wrapping, which is `loop = true`'s
 * `(index + length) % length` in `_stepNext` (`searchprovider.ts:660-706`).
 *
 * From nowhere it takes the first, because `_currentProviderIndex` is seeded before the walk
 * begins (`:646-648`).
 */
fun nextHit(current: Int?, total: Int): Int? = when {
  total == 0 -> null
  current == null -> 0
  else -> (current + 1) % total
}

/** `highlightPrevious()` - the same step the other way, and from nowhere it takes the last. */
fun previousHit(current: Int?, total: Int): Int? = when {
  total == 0 -> null
  current == null -> total - 1
  else -> (current - 1 + total) % total
}

/**
 * What the find bar reads between its arrows.
 *
 * `searchview.tsx:345-351` exactly: `-/-` with nothing found, and otherwise the **one-based**
 * position over the total, with a `-` for a position that is not on any match yet.
 */
fun searchCounterText(current: Int?, total: Int): String = when {
  total == 0 -> "-/-"
  current == null -> "-/$total"
  else -> "${current + 1}/$total"
}
