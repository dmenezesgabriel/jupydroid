package com.jupydroid.app.ui.editor

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.FocusRequester

/**
 * Back leaves a modal state before it leaves the notebook.
 *
 * Three handlers rather than branches of one, because the three states are exclusive: entering
 * edit mode drops the selection (`widget.ts:1925`), searching is asked for from the document's
 * bar which is not up while a run is marked, and the find box's caret is not a cell being
 * edited - so only one is ever enabled at a time.
 */
@Composable
internal fun EditorModalBacks(
  state: EditorUiState,
  listState: LazyListState,
  headFrame: FocusRequester,
  focusManager: FocusManager,
  viewModel: EditorViewModel,
) {
  // Back in edit mode returns to command mode instead of leaving the notebook - the touch
  // analogue of `Escape`, which is what both references bind it to. It is the same target the
  // `enter-command-mode` chord lands on: the head cell's frame takes focus, which blurs the
  // field, ends the caret and reports the mode change through the one focus listener - and where
  // a device maps KEYCODE_ESCAPE onto Back, this is also the path an Escape takes, so the two
  // land on the same frame rather than Esc's landing on nobody.
  BackHandler(enabled = state.mode == CellMode.EDIT) {
    if (listState.composesCell(state.selectedCellId)) headFrame.requestFocus() else focusManager.clearFocus()
  }
  // Back out of the marking mode before it backs out of the notebook, which is what a contextual
  // bar's close does everywhere else on Android - and the reason it is a second handler rather
  // than a branch in the one above is that the two states are exclusive: entering edit mode drops
  // the selection (`widget.ts:1925`), so only one of these is ever enabled.
  BackHandler(enabled = state.selecting) { viewModel.selection.deselectAll() }
  // And out of the find bar before it backs out of the notebook, which is the same contextual-bar
  // rule the one above states. Its own handler rather than a branch, because the three states are
  // exclusive: searching is asked for from the document's bar, which is not up while a run is
  // marked, and the find box taking the caret is not a cell being edited.
  BackHandler(enabled = state.search != null) { viewModel.search.end() }
}
