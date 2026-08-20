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
   * Everything the cell menu offers, through one entry point - so a fifteenth action cannot reach
   * the menu and stop there. Which ids are minted is [CellEdits.applying]'s business.
   *
   * [editor] is what the cell's own text field is holding, which only the composable that owns it
   * can say - see [CellEditorText]. Every action but [CellAction.Split] ignores it.
   */
  fun applyTo(cellId: String, action: CellAction, editor: CellEditorText = CellEditorText.NONE) {
    // Clamped after every transform, not only after Delete: it costs nothing, and a transform
    // added later cannot forget to.
    uiState.update { it.applyingFrom(cellId, action, editor).withSelectionClamped() }
    save.schedule()
  }

  /**
   * `notebook:clear-all-cell-outputs` - every code cell, whatever is selected.
   *
   * Its own method rather than a [CellAction], because a [CellAction] is by definition something a
   * *cell's* menu offers and this one has no cell: `clearAllOutputs` walks the model instead of
   * filtering on `isSelectedOrActive` (`packages/notebook/src/actions.tsx:1851-1870`). Routing it
   * through [applyTo] would have meant inventing a cell for it to be asked from.
   *
   * Undoable like the selection-scoped one, and by the same adaptation - see `docs/DECISIONS.md`.
   */
  fun clearAllOutputs() {
    uiState.update { it.withAllOutputsCleared().recording(before = it) }
    save.schedule()
  }

  /**
   * `notebook:collapse-all-headings` and `notebook:expand-all-headings`, as one method with the
   * direction as its argument.
   *
   * Beside [clearAllOutputs] and for its reason: a [CellAction] is by definition something a
   * *cell's* menu offers, and these two have no cell - they walk the whole notebook the way
   * `collapseAllHeadings` walks `notebook.widgets` (`packages/notebook/src/actions.tsx:2221-2246`).
   * Routing them through [applyTo] would have meant inventing a cell to ask them from.
   *
   * One method rather than two because the pair differ in exactly one thing, which is what
   * [CellHeadings] shows: the direction, and whether the head has to be brought back into view.
   */
  fun setAllHeadingsCollapsed(collapsed: Boolean) {
    uiState.update {
      val folded = if (collapsed) it.withAllHeadingsCollapsed() else it.withAllHeadingsExpanded()
      folded.recording(before = it)
    }
    save.schedule()
  }

  /**
   * Brings [cellId] out of whatever is hiding it, for a surface that is about to jump to it.
   *
   * `expandParent` on `activeCellChanged` (`packages/notebook-extension/src/index.ts:2871-2873`),
   * which is the reference's own answer for *"the case that a cell becomes active that is within a
   * collapsed heading"*. Here rather than beside the find bar because both surfaces that jump need
   * it - the arrows and the table of contents - and it changes the same two collapse flags
   * [setAllHeadingsCollapsed] above it does.
   *
   * **Not undoable, and that is the difference from every other method here.** The collapses this
   * undoes are a side effect of going somewhere, not an operation somebody asked for; a Ctrl-Z
   * that re-hid the cell you had just been sent to would be the reference's `expandParent` running
   * backwards. So it takes no `recording`, only the save the persisted flags need - and only when
   * something actually changed, or jumping around a notebook would rewrite it.
   *
   * ```
   * edits.revealCell("c7")   // its section expanded, ready to be scrolled to
   * ```
   */
  fun revealCell(cellId: String) {
    val before = uiState.value
    val after = before.withCellRevealed(cellId)
    if (after === before) return
    uiState.value = after
    if (after.cells !== before.cells) save.schedule()
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
