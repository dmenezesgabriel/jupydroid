package com.jupydroid.app.ui.editor

/**
 * `notebook:move-cursor-heading-above-or-collapse` - `ArrowLeft` in command mode.
 *
 * `selectHeadingAboveOrCollapseHeading` (`packages/notebook/src/actions.tsx:1244-1270`) asks one
 * question first: is the head an *uncollapsed* heading? Then the key folds it and goes nowhere.
 * Anything else - a plain cell, and a collapsed heading too, which is `ArrowRight`'s to open -
 * jumps to the nearest heading above at equal-or-higher level, the base level being the section
 * the head sits in (`determineHeadingLevel` → [sectionLevelAt]).
 *
 * Two details come with it:
 *
 * - **`deselectAll()` is unconditional** (`:1269`) - even when no qualifying heading exists above
 *   and the head cannot move, the run is dropped. And because the frame is re-asked either way,
 *   [EditorUiState.headMovesByKey] counts the key even when the cursor stayed.
 * - **A jump can land on a hidden heading** - one nested inside a collapsed ancestor's section.
 *   `activeCellChanged` → `expandParent` opens it there (`index.ts:2871-2873`); here [withCellRevealed]
 *   is part of the transform for the same reason.
 */
fun EditorUiState.withHeadingAboveOrCollapsed(): EditorUiState {
  val at = cells.indexOfFirst { it.id == selectedCellId }
  if (at < 0) return this
  val head = cells[at]
  val moved = if (head.headingLevel != null && !head.headingCollapsed) {
    withHeadingCollapsed(head.id, collapsed = true)
  } else {
    val base = sectionLevelAt(at)
    val target = cells.take(at).indexOfLast { (it.headingLevel ?: NOT_A_BOUNDARY) <= base }
    if (target < 0) this else withCellRevealed(cells[target].id).copy(selectedCellId = cells[target].id)
  }
  return moved.copy(anchorCellId = null, headMovesByKey = headMovesByKey + 1)
}

/**
 * `notebook:move-cursor-heading-below-or-expand` - `ArrowRight` in command mode, the mirror of
 * [withHeadingAboveOrCollapsed].
 *
 * `selectHeadingBelowOrExpandHeading` (`actions.tsx:1281-1300`): a collapsed heading under the
 * head opens; anything else jumps to `findHeadingBelow` - the next heading at **any** level,
 * unlike the way up, which qualifies on the section's level. The same unconditional
 * `deselectAll()` and reveal-on-landing rules as above apply.
 */
fun EditorUiState.withHeadingBelowOrExpanded(): EditorUiState {
  val at = cells.indexOfFirst { it.id == selectedCellId }
  if (at < 0) return this
  val head = cells[at]
  val moved = if (head.headingLevel != null && head.headingCollapsed) {
    withHeadingCollapsed(head.id, collapsed = false)
  } else {
    val below = cells.drop(at + 1).indexOfFirst { it.headingLevel != null }
    val target = if (below < 0) -1 else at + 1 + below
    if (target < 0) this else withCellRevealed(cells[target].id).copy(selectedCellId = cells[target].id)
  }
  return moved.copy(anchorCellId = null, headMovesByKey = headMovesByKey + 1)
}
