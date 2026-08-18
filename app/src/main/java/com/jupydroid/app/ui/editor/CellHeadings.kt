package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.markdown.MarkdownDocument

/**
 * A heading owns a **section** of the document, and a collapsed one hides it.
 *
 * Its own file rather than a corner of [CellFolds], because the two fold different things: those
 * two hide a half of *one* cell and are a fact about that cell, while this hides the cells a
 * heading is above and is a fact about the document's structure. P2h's table of contents reads the
 * same structure, which is the other reason it is not buried in a cell-shaped file.
 *
 * **What is hidden is derived, never stored.** The reference stores it on each widget and then
 * recomputes it - `_refreshCollapsedHeadingVisibility` is *"Recompute hidden cells from the current
 * collapsed heading structure"* (`packages/notebook/src/widget.ts:916-940`), called from
 * `moveCells` (`:462`) - so the fact that it is a function of `(order, collapsed flags)` is the
 * reference's own claim. Deriving it instead of re-running it makes every insert, delete, move and
 * undo correct without any of them knowing this file exists.
 *
 * Only [Cell.headingCollapsed] is persisted, which is what JupyterLab persists too: a heading writes
 * `jp-MarkdownHeadingCollapsed` into its metadata (`packages/cells/src/widget.ts:2263-2276`) and no
 * cell ever records that it is hidden.
 */

/**
 * The level this cell heads, or null when it heads nothing.
 *
 * Markdown alone can: `getHeadingInfo` short-circuits every other type to `headingLevel: 7`
 * (`packages/notebook/src/actions.tsx:2481-2493`), a number no section boundary can ever be. Null
 * says the same thing without inviting a comparison that would silently be true.
 */
val Cell.headingLevel: Int?
  get() = if (kind == CellKind.MARKDOWN) MarkdownDocument.headingLevel(source) else null

/**
 * The cells [cellId] owns: everything after it up to the next heading of **equal or higher** level.
 *
 * `setHeadingCollapse` walks forward and breaks on
 * `subCellHeadingInfo.isHeading && subCellHeadingInfo.headingLevel <= selectedHeadingInfo.headingLevel`
 * (`packages/notebook/src/actions.tsx:2374-2405`), running to the end of the notebook when it meets
 * none - which is why an H1's section swallows the H2 under it. Empty for a cell that heads nothing
 * and for one that is gone, both of which the reference answers by returning before its loop.
 */
fun EditorUiState.headingSectionOf(cellId: String): List<String> {
  val at = cells.indexOfFirst { it.id == cellId }
  val level = cells.getOrNull(at)?.headingLevel ?: return emptyList()
  return cells.drop(at + 1).takeWhile { (it.headingLevel ?: NOT_A_BOUNDARY) > level }.map { it.id }
}

/**
 * Every cell a collapsed heading is hiding - the union of their sections.
 *
 * The union is what gives the reference's `localCollapsed` branch for free
 * (`actions.tsx:2400-2420`): expanding an H1 over a still-collapsed H2 shows the H2 and leaves the
 * H2's own children hidden, because that H2 is still in the union on its own account.
 *
 * A heading is never in its own section, so a collapsed one stays on screen - which it must, since
 * it carries the control that expands it again.
 */
val EditorUiState.hiddenCellIds: Set<String>
  get() = cells.filter { it.headingCollapsed }.flatMapTo(mutableSetOf()) { headingSectionOf(it.id) }

/**
 * `notebook:toggle-heading-collapse`, as a set rather than a toggle - which is [CellFolds]'s rule
 * and for the same reason: the caller reads the head's state so a menu row that says one thing does
 * one thing.
 *
 * **Refused on a cell that heads nothing**, which is `setHeadingCollapse`'s own guard - *"otherwise
 * collapsing and uncollapsing already hidden stuff can cause some funny looking bugs"*
 * (`actions.tsx:2364-2372`). A stale id is a no-op, as everywhere in this package.
 *
 * Dirties the notebook because the flag is written to disk, unlike which markdown cells are open.
 */
fun EditorUiState.withHeadingCollapsed(cellId: String, collapsed: Boolean): EditorUiState {
  // One predicate rather than three guards: a cell that is gone, one that heads nothing and one
  // already in the asked-for state are the same no-op, and naming them apart said nothing extra.
  if (cells.none { it.id == cellId && it.headingLevel != null && it.headingCollapsed != collapsed }) return this
  return copy(
    cells = cells.map { if (it.id == cellId) it.copy(headingCollapsed = collapsed) else it },
    dirty = true,
  ).withMarkedRunOutOfHiding(cellId)
}

/**
 * Keeps both ends of the marked run on screen, by putting them on the heading that just swallowed
 * them.
 *
 * The reference never has to: `toggleCurrentHeadingCollapse` acts on the *active* cell
 * (`actions.tsx:2440-2455`), so the one cell that matters is the heading itself and stays visible.
 * Here the operand is the marked run, so a heading inside a run can hide the head - and a head
 * nothing draws is a head the ranged runs would still compute from. `expandParent` states the
 * invariant this keeps (*"the case that a cell becomes active that is within a collapsed heading"*,
 * `:2288-2306`); it repairs it by expanding, which is the wrong half here, since expanding is
 * exactly what the user did not ask for.
 */
private fun EditorUiState.withMarkedRunOutOfHiding(heading: String): EditorUiState {
  val hidden = hiddenCellIds
  if (selectedCellId !in hidden && anchorCellId !in hidden) return this
  return copy(selectedCellId = heading, anchorCellId = null)
}

/** `getHeadingInfo`'s answer for a cell that heads nothing - one past the six levels markdown has. */
private const val NOT_A_BOUNDARY = 7
