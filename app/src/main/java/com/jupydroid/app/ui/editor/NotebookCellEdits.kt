package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.MatchedLine
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/**
 * What the notebook does to its own cells: the thirteen the cell menu offers, and taking one back.
 *
 * Split out of `EditorViewModel` on detekt's `TooManyFunctions`, which is how [NotebookKernel] and
 * [DocumentAutosave] were found too - the answer to that rule is the object that wants to
 * exist, never a raised threshold.
 *
 * **The seam is the reference's own.** A cell operation and the text inside a cell are two
 * histories there: `notebook:undo-cell-action` unwinds the document's stack, `notebook:undo` runs
 * `cell.editor?.undo()` and never touches it (`packages/notebook-extension/src/index.ts:280-286`,
 * `:4476-4547`). So typing stays on `EditorViewModel.editCell` - Compose's `TextFieldState` is the
 * history for that half - and everything with an entry in [EditorUiState.undoable] is here.
 *
 * Each operation is a pure transform from [CellEdits] followed by the same debounced save an edit
 * gets, so re-ordering a notebook survives leaving the screen exactly as typing does - and the
 * same re-search, because the reference restarts its query on `cells.changed`
 * (`packages/notebook/src/searchprovider.ts:559-590`) and a find bar that kept hits for deleted
 * cells would count a notebook that is no longer there.
 *
 * ```
 * val edits = NotebookCellEdits(uiState, save)
 * edits.applyTo("c1", CellAction.Delete)
 * edits.undo()   // the cell is back, with its outputs
 * ```
 */
class NotebookCellEdits(
  // `internal` rather than `private` so [cellsRenamed] and [renameFailed] can stay file-level
  // extensions - the class sits at detekt's function cap, and a rename answer is still a cell
  // edit, which keeps this file its home.
  internal val uiState: MutableStateFlow<EditorUiState>,
  /** Writing belongs to the document half; this only says when there is something to write. */
  internal val save: DocumentAutosave,
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
    uiState.update { it.applyingFrom(cellId, action, editor).withSelectionClamped().withSearchRecomputed() }
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
   * Undoable like the selection-scoped one, and by the same adaptation.
   */
  fun clearAllOutputs() {
    uiState.update { it.withAllOutputsCleared().recording(before = it).withSearchRecomputed() }
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
   * Lands on a match the **workspace** search found, and answers with the cell it landed on.
   *
   * [revealCell]'s stronger sibling: a search result is landed on with [EditorUiState.landedOn],
   * which undoes the collapsed source and the rendered prose as well as the section - the split
   * [CellReveal] draws, and the reference's own. Not undoable, for [revealCell]'s reason: being
   * sent somewhere is not an operation anybody asked to take back.
   *
   * Null when the notebook no longer holds the match, which leaves the document untouched - the
   * caller has nothing to scroll to and says so.
   *
   * ```
   * edits.landOnMatch(match)?.let { scrollTo(it) }
   * ```
   */
  fun landOnMatch(match: MatchedLine): String? {
    val before = uiState.value
    val after = before.landedOn(match)
    if (after === before) return null
    uiState.value = after
    // The same guard [revealCell] uses: only a *persisted* change is worth a write, and reading
    // through a workspace's matches must not rewrite every notebook it visits.
    if (after.cells !== before.cells) save.schedule()
    return after.selectedCellId
  }

  /**
   * Lands a definition jump on [target]'s cell: its collapsed ancestors opened and the cell
   * made the active one, so the scroll and the caret are all that is left - the screen reads
   * them off [EditorUiState.caretLanding] and the cell's own field applies them.
   *
   * Not undoable, for [revealCell]'s reason: being sent somewhere is not an operation anybody
   * asked to take back. The selection is collapsed the way a press outside a marked run
   * collapses it (`_evtMouseDown`'s rule, `widget.ts:3160-3167`) - a jump is to the one cell.
   *
   * ```
   * edits.landOnDefinition(target)
   * ```
   */
  fun landOnDefinition(target: DefinitionTarget) {
    val before = uiState.value
    if (before.cells.none { it.id == target.cellId }) return
    val after = before.withCellRevealed(target.cellId)
      .withSelectionCollapsed()
      .withCellSelected(target.cellId)
      .copy(caretLanding = target)
    uiState.value = after
    if (after.cells !== before.cells) save.schedule()
  }

  /**
   * The landing's field has the caret - [EditorUiState.caretLanding] is a one-shot, consumed
   * by whoever applies it so a second look at the state cannot yank the caret again.
   */
  fun caretLanded() {
    uiState.update { it.copy(caretLanding = null) }
  }

  /**
   * The ask came back with nothing to land on - an empty reply or locations that all point
   * outside the virtual document. [EditorUiState.noDefinitionFound] is a counter and not a
   * flag, so a second miss is a change the screen's snackbar sees again.
   */
  fun definitionMissed() {
    uiState.update { it.copy(noDefinitionFound = it.noDefinitionFound + 1) }
  }

  /**
   * Lands the formatter's answer on [cellId] - "Format cell" (VS Code's
   * `notebook.formatCell`), applied as **one undoable operation**, which is what a Ctrl-Z
   * after `editor.action.format` is there. [recording] is the undoable half and
   * [EditorUiState.sourceRevisions] the visible one: the diff marks the cell in `rewrote`,
   * and the cell's field adopts the new text through [adoptRewrittenSource] exactly as a
   * split's own rewrite lands.
   *
   * [expectedSource] is the source the ask synced and the reply was computed over: a cell
   * that has moved since - typing that reported mid-ask, another operation - is a different
   * document than the formatter rewrote, so its answer is dropped rather than clobbering the
   * newer text. An answer identical to the cell's own is the formatter's no-op: no entry and
   * no write, since `withSource` would still have marked the notebook dirty for it.
   *
   * The menu-on-a-cell rule is `applyingFrom`'s own: asked on a cell outside a marked run,
   * the run collapses onto that cell first.
   */
  fun cellFormatted(cellId: String, source: String, expectedSource: String) {
    val before = uiState.value
    val cell = before.cells.firstOrNull { it.id == cellId } ?: return
    if (cell.source != expectedSource || cell.source == source) return
    val on = if (cellId in before.selectedCellIds) before else before.withSelectionCollapsed().withCellSelected(cellId)
    uiState.value = on.withSource(cellId, source).recording(before = before).withSearchRecomputed()
    save.schedule()
  }

  /**
   * Takes back the last cell operation - `notebook:undo-cell-action`.
   *
   * Structural, never textual: [CellUndo] carries the rule that keeps the two undos apart, and it
   * is the one thing about this feature that is easy to get quietly wrong.
   */
  fun undo() {
    uiState.update { it.undoneCellOperation().withSearchRecomputed() }
    save.schedule()
  }

  /** Replays what [undo] took back - `notebook:redo-cell-action`. */
  fun redo() {
    uiState.update { it.redoneCellOperation().withSearchRecomputed() }
    save.schedule()
  }
}

/**
 * Lands the rename's whole-document answer - `editor.action.rename` applied as
 * **one undoable operation**, the reference's `bulkEditService.apply` transaction: a Ctrl-Z
 * after it restores every cell at once, which is what the shared undo entry is for.
 * [recording] is the undoable half and [EditorUiState.sourceRevisions] the visible one -
 * each rewritten cell's field adopts its new source exactly as a format's rewrite lands.
 *
 * [askedSources] is [NotebookCellEdits.cellFormatted]'s `expectedSource` gate widened to the
 * whole document: the reply carries every code cell's new text, so a cell that moved mid-ask
 * - typing reported in flight, another operation - is a different document than the server
 * renamed, and even its *unchanged* parts would clobber newer text. Any difference drops the
 * whole answer rather than half-applying it. An answer identical to every cell's own source
 * is the no-op: no entry and no write.
 *
 * The menu-on-a-cell rule is `applyingFrom`'s own: asked on a cell outside a marked run,
 * the run collapses onto that cell first.
 */
fun NotebookCellEdits.cellsRenamed(cellId: String, sources: Map<String, String>, askedSources: Map<String, String>) {
  val before = uiState.value
  val moved = sources.keys.any { id -> before.cells.firstOrNull { it.id == id }?.source != askedSources[id] }
  val identical = before.cells.none { cell -> cell.id in sources && cell.source != sources[cell.id] }
  if (moved || identical) return
  val on = if (cellId in before.selectedCellIds) before else before.withSelectionCollapsed().withCellSelected(cellId)
  uiState.value = on.withSources(sources).recording(before = before).withSearchRecomputed()
  save.schedule()
}

/**
 * [code] from outside the notebook - a chat's code block - in a new code cell below the active
 * one, which becomes active, as one undoable operation: JupyterLab's `insertBelow` (jupyterlab@7f0e690
 * `packages/notebook/src/actions.tsx:560-584`) followed by jupyter-chat's `setSource` on the new cell
 * (jupyter-chat@2c224bc `packages/jupyter-chat/src/active-cell-manager.ts`). Nothing with no active cell.
 *
 * Left in command mode, as VS Code's chat insert focuses no cell editor (vscode@3f3380c
 * `src/vs/workbench/contrib/notebook/browser/controller/cellOperations.ts` `insertCell`): the caret is
 * in the chat, and a cell left editing would take the typing while the new one is active.
 */
fun NotebookCellEdits.insertCodeBelow(code: String) {
  val before = uiState.value
  val head = before.selectedCellId ?: return
  val newId = newCellId()
  val inserted = before.withCellInserted(head, CellKind.CODE, newId, code)
  uiState.value = inserted.withSelectionCollapsed().withCellSelected(newId).withMode(CellMode.COMMAND)
    .recording(before = before).withSearchRecomputed()
  save.schedule()
}

/**
 * The rename ask came back refused - the server's own message or the slice guard's, counted
 * so the snackbar sees each one: [NotebookCellEdits.definitionMissed]'s own counter shape,
 * carrying the words a bare count cannot.
 */
fun NotebookCellEdits.renameFailed(message: String) {
  uiState.update { it.copy(renameOutcome = RenameOutcome((it.renameOutcome?.tick ?: 0) + 1, message)) }
}
