package com.jupydroid.app.ui.editor

import com.jupydroid.app.OpenDocument
import com.jupydroid.app.domain.model.TextEdit
import com.jupydroid.app.domain.model.withEditsApplied
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * This notebook seen as the document a **workspace** replace edits, rather than as the screen it is.
 *
 * `ITextModelService` resolving a model that already exists: `BulkTextEdits` resolves one for every
 * resource it is about to edit, open or not (`bulkTextEdits.ts:227`), edits the models and only then
 * saves (`replaceService.ts:136`). A replace that wrote the `.ipynb` instead would be overwritten by
 * this editor's own next debounced save.
 *
 * **Beside [EditorViewModel] rather than on it**, which is that file's established seam and detekt's
 * count is what asked for it again - [NotebookKernel], [NotebookCellEdits], [NotebookSearch],
 * [NotebookReplace] and [NotebookExport] each came off the same way. It takes the save because it
 * writes, and the notebook's id as a **function** for [NotebookExport]'s reason: a rename moves the
 * file while this object lives on.
 *
 * ```
 * val document = NotebookDocument(uiState, save) { notebookId }
 * openDocuments.opened(document)
 * ```
 */
class NotebookDocument(
  private val scope: CoroutineScope,
  private val uiState: MutableStateFlow<EditorUiState>,
  private val save: DocumentAutosave,
  private val notebookId: () -> String,
  /** The id the editor opened under - unlike [path], it stays put through a rename. */
  override val openedAs: String,
) : OpenDocument {
  override val path: String get() = notebookId()

  /** Live, because the tab strip's dot has to follow the save rather than a snapshot of it. */
  override val dirty: StateFlow<Boolean> =
    uiState.map { it.dirty }.stateIn(scope, SharingStarted.Eagerly, initialValue = false)

  /**
   * `docmanager:save`, run on the way out: the owed write is made first and the close proceeds
   * only if it left nothing dirty - a failed save keeps the document open rather than following
   * it off screen, which is what the reference's `saveBeforeClose` does on the same failure.
   */
  override fun saveNow(onSaved: () -> Unit) {
    scope.launch {
      save.flush()
      if (!uiState.value.dirty) onSaved()
    }
  }

  /**
   * The close dialog's Discard: owed work is dropped - `SaveHandler.dispose()` clears its timer
   * and writes nothing, so the close that follows must not flush it back.
   */
  override fun discardChanges() {
    save.discard()
  }

  /**
   * One cell's source - a notebook's resources are its **cells**, and the notebook itself is not one.
   *
   * `ReplaceService.createEdits` addresses a notebook match through `match.cell?.uri`
   * (`replaceService.ts:206-214`), and a cell does have a URI of its own there (`CellUri.parse`), so
   * this is the reference's arrangement and not a convenience. The index is the one a
   * `MatchedLine` carries, and P2x-4a's reason for an index rather than an id holds here too.
   *
   * Null answers two different questions the same way, and both are refusals the engine wants: a
   * request for the file itself, which this document does not have, and a cell the notebook no
   * longer holds - including every cell while the notebook is still loading.
   */
  override fun sourceOf(cell: Int?): String? = cell?.let { uiState.value.cells.getOrNull(it)?.source }

  /**
   * Rewrites one cell, and tells its field so.
   *
   * [adoptingRewrites] rather than `recording`: the cell's `TextFieldState` cannot see a changed
   * `Cell.source` on its own, and **no entry goes into this notebook's own undo history** - the
   * reference records one `MultiModelEditStackElement` for the whole replace
   * (`bulkTextEdits.ts:319-330`), so a second entry here would make one command undoable twice in
   * two scopes that could then disagree.
   *
   * The query is re-run for the reason [EditorViewModel.editCell] re-runs it: *"startQuery is also
   * used as 'restartQuery' after each text change"* (`packages/notebook/src/searchprovider.ts:391`).
   */
  override fun applyEdits(cell: Int?, edits: List<TextEdit>) {
    if (cell == null || edits.isEmpty()) return
    uiState.update { state ->
      val target = state.cells.getOrNull(cell) ?: return@update state
      state.withSource(target.id, target.source.withEditsApplied(edits))
        .adoptingRewrites(before = state)
        .withSearchRecomputed()
    }
    save.schedule()
  }
}
