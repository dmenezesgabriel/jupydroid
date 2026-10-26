package com.jupydroid.app.ui.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFocusManager
import com.jupydroid.app.OpenNotebookActiveCell

/**
 * Publishes this notebook's insert below its active cell to [activeCell], for a contributed panel's
 * Insert - [OpenDebugStack][com.jupydroid.app.OpenDebugStack]'s seam again - and null while no cell is
 * active, clearing it when the notebook goes.
 *
 * The insert drops the caret before it inserts, as VS Code's chat insert focuses no cell editor
 * (vscode@3f3380c `src/vs/workbench/contrib/notebook/browser/controller/cellOperations.ts`
 * `insertCell`). Dropping it is how this app leaves edit mode at all - the focus listener reports the
 * mode, as `EditorScreen`'s Back handler relies on - so the cell left behind cannot take the next
 * keystroke while the new one is active (`InsertBelowActiveCellCaretTest`).
 *
 * ```
 * InsertBelowActiveCellPublication(activeCell, state.selectedCellId, viewModel.edits)
 * ```
 */
@Composable
internal fun InsertBelowActiveCellPublication(
  activeCell: OpenNotebookActiveCell,
  selectedCellId: String?,
  edits: NotebookCellEdits,
) {
  val focusManager = LocalFocusManager.current
  LaunchedEffect(activeCell, selectedCellId) {
    val insert = { code: String ->
      focusManager.clearFocus()
      edits.insertCodeBelow(code)
    }
    activeCell.publish(insert.takeIf { selectedCellId != null })
  }
  DisposableEffect(activeCell) { onDispose { activeCell.clear() } }
}
