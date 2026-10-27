package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFocusManager
import com.jupydroid.app.OpenNotebookCells
import com.jupydroid.app.domain.model.CellKind
import kotlinx.coroutines.flow.StateFlow

/**
 * Publishes this notebook's cells and its insert to [notebookCells], for a contributed panel's
 * Insert and an agent's `add-cell` - [OpenDebugStack][com.jupydroid.app.OpenDebugStack]'s seam again -
 * saying whether a cell is active, and clearing it when the notebook goes.
 *
 * The insert drops the caret before it inserts, as VS Code's chat insert focuses no cell editor
 * (vscode@3f3380c `src/vs/workbench/contrib/notebook/browser/controller/cellOperations.ts`
 * `insertCell`). Dropping it is how this app leaves edit mode at all - the focus listener reports the
 * mode, as `EditorScreen`'s Back handler relies on - so the cell left behind cannot take the next
 * keystroke while the new one is active (`InsertBelowActiveCellCaretTest`).
 *
 * ```
 * NotebookCellsPublication(notebookCells, viewModel.uiState, state.selectedCellId, viewModel.edits)
 * ```
 */
@Composable
internal fun NotebookCellsPublication(
  notebookCells: OpenNotebookCells,
  uiState: StateFlow<EditorUiState>,
  selectedCellId: String?,
  edits: NotebookCellEdits,
) {
  val focusManager = LocalFocusManager.current
  LaunchedEffect(notebookCells, uiState, selectedCellId) {
    val insert = { index: Int, kind: CellKind, source: String ->
      focusManager.clearFocus()
      edits.insertCellAt(index, kind, source)
    }
    notebookCells.publish(selectedCellId != null, { uiState.value }, insert)
  }
  DisposableEffect(notebookCells) { onDispose { notebookCells.clear() } }
}
