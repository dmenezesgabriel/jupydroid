package com.jupydroid.app.ui.editor

/**
 * How far the bar's collapsible region has slid away: `0f` whole, `1f` gone.
 *
 * **A second row on the bar is not free, and this is what it costs on a phone.** The region was
 * the title strip while the document's name lived there - `#jp-title-panel` in the reference's
 * single-document mode - and is the breadcrumb row now that the tab carries the name.
 * Either way the arithmetic is the same: a 48dp row under the bar is 48dp of notebook, and at
 * 320dp that was measured to be enough to put a fourth cell's source under the bottom bar
 * (`split-code-cell`, `batch-delete`, `insert-heading`, `next-previous`).
 *
 * Material's own answer for a two-row bar is that the second row collapses with the content,
 * which is what `MediumTopAppBar` does for a title below the actions.
 *
 * **Driven from the list's own scroll rather than a `nestedScroll` connection.** `EditorScreen`
 * already holds the `LazyListState` that the cells are drawn from, so the fraction is derived from
 * what is on screen instead of from a second channel that could disagree with it.
 *
 * ```
 * collapsibleRegionFraction(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset, stripPx)
 * ```
 *
 * @param firstVisibleItem which cell the list is showing first.
 * @param scrolledPx how far into that cell it has scrolled - **restarting at zero for each**,
 *   which is why [firstVisibleItem] is read at all: past the first cell the offset alone would
 *   bring the row back in the middle of a long notebook.
 * @param stripPx the region's own height. Zero on the frame before it has been measured, which
 *   would otherwise divide by zero.
 */
internal fun collapsibleRegionFraction(firstVisibleItem: Int, scrolledPx: Int, stripPx: Float): Float = when {
  firstVisibleItem > 0 -> 1f
  stripPx <= 0f -> 0f
  else -> (scrolledPx / stripPx).coerceIn(0f, 1f)
}
