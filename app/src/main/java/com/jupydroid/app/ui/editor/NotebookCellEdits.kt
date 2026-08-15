package com.jupydroid.app.ui.editor

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the notebook does to its own cells: the thirteen the cell menu offers, and taking one back.
 *
 * Split out of `EditorViewModel` on detekt's `TooManyFunctions`, which is how [NotebookKernel] and
 * [NotebookSaveDebounce] were found too - the answer to that rule is the object that wants to
 * exist, never a raised threshold.
 *
 * **The seam is the reference's own.** A cell operation and the text inside a cell are two
 * histories there: `notebook:undo-cell-action` unwinds the document's stack, `notebook:undo` runs
 * `cell.editor?.undo()` and never touches it (`packages/notebook-extension/src/index.ts:280-286`,
 * `:4476-4547`). So typing stays on `EditorViewModel.editCell` - Compose's `TextFieldState` is the
 * history for that half - and everything with an entry in [EditorUiState.undoable] is here.
 *
 * Each operation is a pure transform from [CellEdits] followed by the same debounced save an edit
 * gets, so re-ordering a notebook survives leaving the screen exactly as typing does.
 *
 * ```
 * val edits = NotebookCellEdits(uiState, save)
 * edits.applyTo("c1", CellAction.Delete)
 * edits.undo()   // the cell is back, with its outputs
 * ```
 */
class NotebookCellEdits(
  private val uiState: MutableStateFlow<EditorUiState>,
  /** Writing belongs to the document half; this only says when there is something to write. */
  private val save: NotebookSaveDebounce,
) {
  /**
   * Everything the cell menu offers, through one entry point - so a fourteenth action cannot reach
   * the menu and stop there. Which ids are minted is [CellEdits.applying]'s business.
   */
  fun applyTo(cellId: String, action: CellAction) {
    // Clamped after every transform, not only after Delete: it costs nothing, and a transform
    // added later cannot forget to.
    uiState.update { it.applying(action, cellId).withSelectionClamped() }
    save.schedule()
  }

  /**
   * Takes back the last cell operation - `notebook:undo-cell-action`.
   *
   * Structural, never textual: [CellUndo] carries the rule that keeps the two undos apart, and it
   * is the one thing about this feature that is easy to get quietly wrong.
   */
  fun undo() {
    uiState.update { it.undoneCellOperation() }
    save.schedule()
  }

  /** Replays what [undo] took back - `notebook:redo-cell-action`. */
  fun redo() {
    uiState.update { it.redoneCellOperation() }
    save.schedule()
  }
}
