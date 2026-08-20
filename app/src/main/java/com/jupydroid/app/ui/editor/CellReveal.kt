package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellKind

/**
 * Bringing one cell out of whatever is hiding it - `activateNewMatch` and the two reveals it gets
 * for free from setting `activeCellIndex`.
 *
 * **Three different collapses can be hiding a cell, and the reference undoes all three:**
 *
 * - its own source, in `activateNewMatch` itself - `if (activeCell.inputHidden)
 *   activeCell.inputHidden = false` (`packages/notebook/src/searchprovider.ts:630-634`);
 * - a rendered markdown cell, in `MarkdownCellSearchProvider` - `cell.rendered = false`, named
 *   `_unrenderedByHighlight` (`packages/cells/src/searchprovider.ts:380-410`);
 * - a collapsed parent heading, through `NotebookActions.expandParent`, which the notebook
 *   extension wires to **every** active-cell change
 *   (`packages/notebook-extension/src/index.ts:2871-2873`, over `actions.tsx:2288-2306`).
 *
 * The third is why this is parity rather than an adaptation, and it corrects a reading recorded in
 * P2h: the table of contents' jump treats a hidden heading as unreachable, on the grounds that the
 * reference does not solve it either. That citation says it does.
 *
 * Its own file rather than a corner of [CellHeadings] or [CellFolds], because it is the one
 * operation that spans all three: each of those files owns *a* way of hiding a cell, and this owns
 * the question they are all answers to.
 */

/**
 * Whatever is hiding [cellId], undone - and **this** when nothing was.
 *
 * The unchanged return is load-bearing rather than an optimisation, exactly as it is in
 * [withMarkdownRendered]: two of the three flags are persisted, so stepping through matches in an
 * ordinary notebook would otherwise mark it dirty and rewrite the file for having been read.
 *
 * A stale id is a no-op, as everywhere in this package.
 *
 * ```
 * state.withCellRevealed("c7")   // its section expanded, its source open, its prose unrendered
 * ```
 */
fun EditorUiState.withCellRevealed(cellId: String): EditorUiState {
  // A stale id and a cell nothing is hiding are the same no-op, so they are one condition rather
  // than two guards - the shape [withHeadingCollapsed] already settled on for the same reason.
  val cell = cells.firstOrNull { it.id == cellId } ?: return this
  // Every collapsed heading whose section holds this cell, which is `expandParent`'s fixed point:
  // it walks up to the nearest parent header and recurses through one that is itself hidden, so
  // what it ends up expanding is exactly the collapsed ancestors of this cell - and nothing else,
  // which is what leaves a collapsed heading elsewhere in the document alone.
  val hiding = cells.filter { it.headingCollapsed && cellId in headingSectionOf(it.id) }.map { it.id }.toSet()
  val unrender = cell.kind == CellKind.MARKDOWN && cellId !in markdownBeingEdited
  return if (hiding.isEmpty() && !cell.sourceHidden && !unrender) this else copy(
    cells = cells.map {
      when {
        it.id in hiding -> it.copy(headingCollapsed = false)
        it.id == cellId && it.sourceHidden -> it.copy(sourceHidden = false)
        else -> it
      }
    },
    // Opened, and **not** asked to take the caret: a command that unrenders is not a tap asking to
    // type in it, which is the distinction P2e-3 cost a soft keyboard over an untouched notebook
    // to learn (`EditorUiState.markdownTakingCaret`).
    markdownBeingEdited = if (unrender) markdownBeingEdited + cellId else markdownBeingEdited,
    // Only the two persisted flags are the document's; which markdown cells are open is not, and
    // nbformat has nowhere to write it.
    dirty = dirty || hiding.isNotEmpty() || cell.sourceHidden,
  )
}
