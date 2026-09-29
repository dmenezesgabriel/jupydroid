package com.jupydroid.app.ui.editor

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jupydroid.app.domain.model.MatchedLine

/**
 * A workspace search result this notebook was opened from, landed on once the cells are here.
 *
 * The notebook half of `FileEditorScreen`'s own reveal, and its own composable rather than another
 * effect in [EditorScreen] because detekt counted the branches - the answer to that rule is the
 * object that wants to exist, which is this: "the landing", with the guard it needs to happen once.
 *
 * **The offsets are resolved against the cells this screen loaded**, not against the ones the walk
 * reported, which is the reference's own order: `searchView.open` opens the notebook and *then*
 * calls `updateMatchesForEditorWidget()` to find the match again (`searchView.ts:2283-2295`). See
 * [landedOn]; a workspace shared with a kernel can change under a result.
 *
 * **Keyed on whether the notebook is loaded, and emphatically not on its cells.** Landing *changes*
 * the cells - it expands the heading that was hiding the match - so an effect keyed on them
 * restarts itself: Compose cancels a `LaunchedEffect`'s coroutine the moment a key changes, which
 * killed `animateScrollToItem` mid-flight and left the request unhandled, so the retry saw
 * `landedOn` already set and did nothing. Measured on the tablet, and **only the screenshot said
 * so** - the flow's `assertVisible` on the match was green against a notebook sitting on cell one.
 * `loaded` flips once and never again, which is exactly the
 * "the cells are here now" this needs.
 *
 * Landed **once per request** whatever recomposes afterwards - the defect `FileEditorScreen`
 * measured at `expected:<1> but was:<2>`, where the effect re-ran and dragged the reader back to a
 * match they had scrolled away from. A match the notebook no longer holds lands nowhere and is
 * still reported handled, for the same reason.
 */
@Composable
internal fun LandingOnAMatch(
  reveal: MatchedLine?,
  state: EditorUiState,
  edits: NotebookCellEdits,
  listState: LazyListState,
  onHandled: () -> Unit,
) {
  var landedOn by remember { mutableStateOf<MatchedLine?>(null) }
  LaunchedEffect(reveal, state.loaded) {
    val landing = reveal?.takeIf { it != landedOn && state.loaded } ?: return@LaunchedEffect
    landedOn = landing
    edits.landOnMatch(landing)?.let { cellId ->
      // Against the revealed document rather than the one on screen, exactly as `jumpToHeading`
      // does: the transform has already run and this composition has not seen it, so the index has
      // to be asked of what the notebook is about to be.
      val index = state.withCellRevealed(cellId).cellsOnScreen.indexOfFirst { it.id == cellId }
      if (index >= 0) listState.animateScrollToItem(index)
    }
    onHandled()
  }
}
