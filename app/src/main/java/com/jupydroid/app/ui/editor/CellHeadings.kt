package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.markdown.MarkdownDocument
import com.jupydroid.app.richtext.plainText

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
 * One entry in the table of contents: the cell it maps to, the heading's level and its text.
 *
 * `INotebookHeading` carries the same three, plus a `cellRef` to the widget in place of an id
 * (`packages/notebook/src/toc.ts:60-80`). Flat rather than nested - the reference's own tree is
 * built from a flat list at render time (`packages/toc/src/toctree.tsx:57-102`), the same rule
 * [headingSectionOf] already states for this app.
 */
data class TocHeading(val cellId: String, val level: Int, val text: String)

/**
 * `NotebookToCModel.getHeadings()` (`packages/notebook/src/toc.ts:245-322`), narrowed to what this
 * app already has: a cell collapses to at most one heading ([Cell.headingLevel]'s "lowest number
 * wins"), so an entry is 1:1 with a markdown cell that has one. A code or raw cell, and a markdown
 * cell with no heading, contribute nothing - the reference's own loop skips a cell that yields no
 * headings too.
 *
 * Numbering (`numberHeaders`/`numberingH1`) and output headings (`includeOutput`) are later
 * sub-steps; this is the flat list alone.
 */
val EditorUiState.tableOfContentsHeadings: List<TocHeading>
  get() = cells.mapNotNull { cell ->
    val heading = if (cell.kind == CellKind.MARKDOWN) MarkdownDocument.heading(cell.source) else null
    heading?.let { TocHeading(cell.id, it.level, it.inlines.plainText()) }
  }

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
 * How many cells sit under [cellId] that nobody can see, and **0 for every cell that is not a
 * collapsed heading**.
 *
 * The reference stores this as `MarkdownCell.numberChildNodes` and recomputes it inside
 * `setHeadingCollapse` (`actions.tsx:2427-2429`); here it is the section, which is where that
 * number came from. It is what `addCells` adds to a paste index (`:1590-1612`) so a pasted cell
 * lands *after* a hidden section rather than inside one.
 */
internal fun EditorUiState.hiddenSectionSize(cellId: String): Int =
  if (cells.any { it.id == cellId && it.headingCollapsed }) headingSectionOf(cellId).size else 0

/**
 * `notebook:insert-heading-{above,below}` - a new heading beside the section the head is in.
 *
 * Three rules, all from `insertSameLevelHeading{Above,Below}` (`actions.tsx:1183-1231`), and the
 * second is the one a first guess gets wrong:
 *
 * - **The level is the section's, not the cell's.** `determineHeadingLevel` answers the head's own
 *   level if it is a heading and its parent heading's if it is not (`:3576-3597`), falling back to
 *   1 when there is no heading at or above it at all.
 * - **[SectionReach.BELOW] means the end of the section**, at `findLowerEqualLevelHeadingBelow`
 *   (`:3520-3550`) - the start of the next sibling - so the new heading closes the section it was
 *   asked from rather than cutting it in half. Only [SectionReach.ABOVE] is beside the cell.
 * - **It ends in edit mode**, `notebook.mode = 'edit'` (`:3645`), which here is the caret being
 *   asked for: the new cell holds nothing but its marker, so there is nothing to read and
 *   everything to type.
 */
fun EditorUiState.withHeadingInserted(reach: SectionReach, newId: String): EditorUiState {
  val head = cells.indexOfFirst { it.id == selectedCellId }
  if (head < 0) return this
  val level = sectionLevelAt(head)
  val at = if (reach == SectionReach.ABOVE) head else endOfSectionAt(head, level)
  val heading = Cell(newId, CellKind.MARKDOWN, "#".repeat(level) + " ")
  return copy(
    cells = cells.toMutableList().apply { add(at, heading) },
    selectedCellId = newId,
    anchorCellId = null,
    // Open and holding the caret, which is the reference's `mode = 'edit'`: an empty heading is
    // the one inserted cell there is nothing to look at in.
    markdownBeingEdited = markdownBeingEdited + newId,
    markdownTakingCaret = newId,
    mode = CellMode.EDIT,
    dirty = true,
  )
}

/**
 * `determineHeadingLevel`: the cell's own level, else its parent heading's, else 1
 * (`actions.tsx:3576-3597`, and the `-1` fallbacks at `:1194` and `:1216`).
 */
private fun EditorUiState.sectionLevelAt(at: Int): Int =
  cells[at].headingLevel
    ?: cells.take(at).lastOrNull { it.headingLevel != null }?.headingLevel
    ?: 1

/** `findLowerEqualLevelHeadingBelow`, or the end of the notebook when there is no sibling. */
private fun EditorUiState.endOfSectionAt(at: Int, level: Int): Int {
  val below = cells.drop(at + 1).indexOfFirst { (it.headingLevel ?: NOT_A_BOUNDARY) <= level }
  return if (below < 0) cells.size else at + 1 + below
}

/**
 * `notebook:collapse-all-headings` - every heading in the notebook, and nothing else.
 *
 * `collapseAllHeadings` filters on `getHeadingInfo(cell).isHeading` (`actions.tsx:2221-2232`), so
 * the cells between the headings are hidden by *being in a section* rather than by being touched.
 * Its last line before handling state is `notebook.activeCellIndex = 0` (`:2229`), and the first
 * cell is the one no collapse can reach: nothing is above it to own it. The run goes with the head
 * for [withMarkedRunOutOfHiding]'s reason - an anchor deeper in the notebook would be a mark on a
 * cell nothing draws.
 */
fun EditorUiState.withAllHeadingsCollapsed(): EditorUiState {
  if (cells.none { it.headingLevel != null && !it.headingCollapsed }) return this
  return copy(
    cells = cells.map { if (it.headingLevel != null) it.copy(headingCollapsed = true) else it },
    selectedCellId = cells.first().id,
    anchorCellId = null,
    dirty = true,
  )
}

/**
 * `notebook:expand-all-headings`, which is the same walk and **does not touch the head** -
 * `expandAllHeadings` has no `activeCellIndex` line at all (`actions.tsx:2238-2246`), and needs
 * none: nothing it does can hide a cell.
 *
 * Headings only, as there. A flag left on a cell whose source has since stopped being a heading is
 * inert - [hiddenCellIds] reads the section, which is empty - and clearing it here would delete a
 * key from a file this app did not put it in.
 */
fun EditorUiState.withAllHeadingsExpanded(): EditorUiState {
  val collapsed = cells.filter { it.headingCollapsed && it.headingLevel != null }.map { it.id }.toSet()
  if (collapsed.isEmpty()) return this
  return copy(
    cells = cells.map { if (it.id in collapsed) it.copy(headingCollapsed = false) else it },
    dirty = true,
  )
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
