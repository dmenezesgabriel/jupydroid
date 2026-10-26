package com.jupydroid.app

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.jupydroid.extension.api.NotebookActiveCell

/**
 * Where the open notebook publishes an insert below its active cell, and a contributed panel reads
 * it - [OpenDebugStack]'s seam, crossed for the same reason: the panel is in the list pane and the
 * cells are the detail pane's `EditorViewModel`'s.
 *
 * Null while no notebook is open, or one is open with no active cell, which is what makes Insert
 * disabled there as jupyter-chat's `available` does (jupyter-chat@2c224bc
 * `packages/jupyter-chat/src/active-cell-manager.ts`).
 *
 * ```
 * val activeCell = remember { OpenNotebookActiveCell() }   // MainNavigation
 * activeCell.publish(viewModel.edits::insertCodeBelow)      // the editor
 * ```
 */
@Stable
class OpenNotebookActiveCell : NotebookActiveCell {
  private var insert by mutableStateOf<((String) -> Unit)?>(null)

  override val available: Boolean get() = insert != null

  override fun insertBelow(code: String) {
    insert?.invoke(code)
  }

  /** The open notebook's insert, or null when it has no active cell - replacing whatever was there. */
  fun publish(insertBelowActiveCell: ((String) -> Unit)?) {
    insert = insertBelowActiveCell
  }

  /** The notebook has gone; nothing here should outlive the editor that published it. */
  fun clear() {
    insert = null
  }
}
