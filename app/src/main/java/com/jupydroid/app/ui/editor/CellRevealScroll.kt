package com.jupydroid.app.ui.editor

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * The two scrolls that bring a cell in front of the reader - a search's current hit, and the cell
 * about to be typed into - split out of [EditorScreen] on its file-length limit, the way
 * [LandingOnDefinition] and [LandingOnAMatch] were: each is an effect keyed on what it lands on.
 *
 * ```
 * CellRevealScroll(state, listState)
 * ```
 */
@Composable
internal fun CellRevealScroll(state: EditorUiState, listState: LazyListState) {
  // `activateNewMatch`'s `scrollToItem` (`packages/notebook/src/searchprovider.ts:625-630`),
  // keyed on the hit rather than on the cell: two matches in one cell are two steps, and a
  // notebook that only moved when the *cell* changed would look stuck on the second of them.
  // The reveal has already happened in the state, so the index is read after it and the cell is
  // on screen to scroll to.
  LaunchedEffect(state.search?.current, state.search?.hits) {
    val landed = state.search?.currentHitCellId ?: return@LaunchedEffect
    val index = state.cellsOnScreen.indexOfFirst { it.id == landed }
    // **Only when the cell is not already fully on screen**, which is `activateNewMatch`'s two
    // steps read together: `if (!activeCell.inViewport) scrollToItem(…)` and then
    // `editor.revealPosition(…)` (`:625-641`). The first alone is not the rule and measuring is
    // what said so - `inViewport` is true of a cell showing five pixels above the soft keyboard,
    // so a match the arrows had just been asked for stayed cut in half at the bottom edge
    // (the run before this). The second step is what makes the
    // *match* readable rather than merely present, and with a cell-sized list item the equivalent
    // is asking for the whole cell.
    //
    // Still conditional, because the other half of the reference's guard matters too: stepping
    // between two matches already in front of the reader must not yank the notebook.
    if (index >= 0 && !listState.isFullyVisible(index)) listState.animateScrollToItem(index)
  }
  // **A cell about to be typed into has to be on screen**, which is the reference's `_ensureFocus`
  // (`packages/notebook/src/widget.ts:2885-2905`) - see [cellBeingEditedIndex] for the rule and
  // for what its absence cost. Keyed on the cell and the mode rather than on the document, so
  // typing into the cell does not re-scroll on every keystroke.
  //
  // Guarded by [isFullyVisible] exactly as the search's scroll is, and for the reference's own
  // reason: `if (activeCell.inViewport) focus() else scrollToItem(...)`. A cell already in front
  // of the reader must not be yanked.
  LaunchedEffect(state.mode, state.selectedCellId, state.cellsOnScreen.size) {
    val index = cellBeingEditedIndex(state.mode, state.selectedCellId, state.cellsOnScreen)
      ?: return@LaunchedEffect
    if (!listState.isFullyVisible(index)) listState.animateScrollToItem(index)
  }
  // **The caret-asked cell has to be on screen before its field exists to take the caret** -
  // `runAndAdvance`/`runAndInsert` await `inViewportChanged` before `mode = 'edit'` for exactly
  // this (`actions.tsx:839-848`, `:913-918`). The scroll brings it up, the field composes, and
  // `EditorUiState.cellTakingCaret` does the rest - the same `isFullyVisible` guard, since a
  // cell already in front of the reader must not be yanked either.
  LaunchedEffect(state.cellTakingCaret) {
    val asked = state.cellTakingCaret ?: return@LaunchedEffect
    val index = state.cellsOnScreen.indexOfFirst { it.id == asked }
    if (index >= 0 && !listState.isFullyVisible(index)) listState.animateScrollToItem(index)
  }
}

/**
 * Whether item [index] is on screen **in its entirety** - see the scroll above for why partly is
 * not enough.
 *
 * An item taller than the viewport is never fully visible, and scrolling it to the top is the right
 * answer for that too; there is no loop, because the effect that asks is keyed on the match rather
 * than on the scroll.
 */
internal fun LazyListState.isFullyVisible(index: Int): Boolean {
  val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return false
  return item.offset >= layoutInfo.viewportStartOffset &&
    item.offset + item.size <= layoutInfo.viewportEndOffset
}
