package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellKind

/**
 * Bringing one cell out of whatever is hiding it - in **two** strengths, because the reference has
 * two and they are not the same reveal.
 *
 * - **[withCellRevealed] is what every active-cell change does.** `activeCellChanged` is wired to
 *   `NotebookActions.expandParent` (`packages/notebook-extension/src/index.ts:2871-2873`, over
 *   `actions.tsx:2288-2306`), which walks up to the collapsed parent heading and uncollapses it.
 *   Nothing else. A jump from the table of contents gets this one.
 * - **[withMatchRevealed] is what landing on a search match does**, and it adds the two the search
 *   provider adds: `if (activeCell.inputHidden) activeCell.inputHidden = false`
 *   (`packages/notebook/src/searchprovider.ts:630-634`), and `cell.rendered = false` for a markdown
 *   cell (`packages/cells/src/searchprovider.ts:380-410`, `_unrenderedByHighlight`). Both exist to
 *   put the match where it can be seen, which only a search needs.
 *
 * **The split is the reference's own and it is emphatic about it.** A table-of-contents jump forces
 * command mode first, with the reason in the code: *"Switch to command mode to avoid entering
 * Markdown cell in edit mode if the document was in edit mode"* (`packages/notebook/src/toc.ts:681-684`).
 * One helper for both put a heading's `## Part A` on screen as an editor where the reader had asked
 * for a heading - green against every assertion, and the screenshot is what said otherwise
 * (`evidence/p2i-4b-toc-reveals/`).
 *
 * The parent-expanding half also corrects a reading recorded in P2h: the table of contents' jump
 * treated a hidden heading as unreachable, on the grounds that the reference does not solve it
 * either. That citation says it does.
 *
 * Its own file rather than a corner of [CellHeadings] or [CellFolds], because it is the one
 * operation that spans them: each of those owns *a* way of hiding a cell, and this owns the
 * question they are all answers to.
 */

/**
 * The collapsed sections above [cellId], expanded - and **this** when there were none.
 *
 * The unchanged return is load-bearing rather than an optimisation, exactly as it is in
 * [withMarkdownRendered]: `headingCollapsed` is persisted, so jumping around an ordinary notebook
 * would otherwise mark it dirty and rewrite the file for having been read.
 *
 * A stale id is a no-op, as everywhere in this package.
 *
 * ```
 * state.withCellRevealed("c7")   // every section that was folded over it, opened
 * ```
 */
fun EditorUiState.withCellRevealed(cellId: String): EditorUiState {
  // Every collapsed heading whose section holds this cell, which is `expandParent`'s fixed point:
  // it walks up to the nearest parent header and recurses through one that is itself hidden, so
  // what it ends up expanding is exactly the collapsed ancestors of this cell - and nothing else,
  // which is what leaves a collapsed heading elsewhere in the document alone.
  val hiding = cells.filter { it.headingCollapsed && cellId in headingSectionOf(it.id) }.map { it.id }.toSet()
  return if (hiding.isEmpty()) {
    this
  } else {
    copy(
      cells = cells.map { if (it.id in hiding) it.copy(headingCollapsed = false) else it },
      dirty = true,
    )
  }
}

/**
 * [withCellRevealed], plus the two things landing on a *match* also undoes: the cell's own
 * collapsed source, and its rendered prose.
 *
 * Only a search wants these. Both are about showing the reader the characters that matched, and a
 * match in this app is only ever drawn in a cell's source - see `docs/DECISIONS.md` for why
 * rendered prose is counted and not lit.
 *
 * ```
 * state.withMatchRevealed("c7")   // its section open, its source open, its prose unrendered
 * ```
 */
fun EditorUiState.withMatchRevealed(cellId: String): EditorUiState {
  val cell = cells.firstOrNull { it.id == cellId } ?: return this
  val unrender = cell.kind == CellKind.MARKDOWN && cellId !in markdownBeingEdited
  val expanded = withCellRevealed(cellId)
  return if (!cell.sourceHidden && !unrender) {
    expanded
  } else {
    expanded.copy(
      cells = expanded.cells.map { if (it.id == cellId) it.copy(sourceHidden = false) else it },
      // Opened, and **not** asked to take the caret: a command that unrenders is not a tap asking
      // to type in it, which is the distinction P2e-3 cost a soft keyboard over an untouched
      // notebook to learn (`EditorUiState.markdownTakingCaret`).
      markdownBeingEdited = if (unrender) markdownBeingEdited + cellId else markdownBeingEdited,
      // Which markdown cells are open is not the document's; `sourceHidden` is.
      dirty = expanded.dirty || cell.sourceHidden,
    )
  }
}
