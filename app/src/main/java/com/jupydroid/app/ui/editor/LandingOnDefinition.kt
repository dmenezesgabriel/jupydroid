package com.jupydroid.app.ui.editor

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/**
 * The two effects a definition ask can leave behind - the scroll half of the jump and the
 * miss's "No definition found" - split out of [EditorScreen] on detekt's complexity count,
 * [LandingOnAMatch]'s precedent: "the landing" is an object that wants to exist.
 *
 * The scroll is `TextEditorSelectionRevealType.NearTopIfOutsideViewport` in the reference's
 * `_openReference` (`goToCommands.ts`): only when the landing's cell is not already in front
 * of the reader, for the search scroll's own reason - a jump must not yank what is already
 * read. The reveal has already happened in the state ([NotebookCellEdits.landOnDefinition]),
 * so the index read after it names a cell that is on screen to scroll to.
 */
@Composable
internal fun LandingOnDefinition(
  state: EditorUiState,
  edits: NotebookCellEdits,
  listState: LazyListState,
  snackbar: SnackbarHostState,
) {
  LaunchedEffect(state.caretLanding) {
    val landing = state.caretLanding ?: return@LaunchedEffect
    try {
      val index = state.cellsOnScreen.indexOfFirst { it.id == landing.cellId }
      if (index >= 0 && !listState.isFullyVisible(index)) listState.animateScrollToItem(index)
    } finally {
      // Whatever is still pending once the scroll finishes - or a gesture interrupts it - is
      // dropped rather than left armed: a cell that never composed never took its caret, and
      // a stale landing yanking focus minutes later is worse than a jump that went nowhere.
      edits.caretLanded()
    }
  }
  // VS Code's `MessageController.showMessage`: an ask that found nothing - an empty reply,
  // or locations that all point outside the virtual document - says so once, where the user
  // is looking. The counter and not a flag, so a second miss is a change this sees.
  LaunchedEffect(state.noDefinitionFound) {
    if (state.noDefinitionFound > 0) snackbar.showSnackbar(NO_DEFINITION_FOUND)
  }
}

/** What a definition ask that found nothing says - VS Code's own message (`goToCommands.ts`). */
private const val NO_DEFINITION_FOUND = "No definition found"
