package com.jupydroid.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * The document half of the editor: what the notebook *is*, and every change to it.
 *
 * What the notebook asks its kernel lives next door in [NotebookKernel] and is reached through
 * [kernel]. The split is detekt's doing and detekt was right - the two halves share only the
 * state flow, and running a cell has nothing to say about renaming a file.
 */
class EditorViewModel(
  private val notebookId: String,
  private val notebookRepository: NotebookRepository,
  kernelGateway: KernelGateway,
  /**
   * Outlives this ViewModel, because [onCleared] runs *after* `viewModelScope` has been
   * cancelled - `ViewModel.clear()` closes the closeables and only then calls `onCleared`
   * (`ViewModel.jvm.kt:66-68`). Work launched on `viewModelScope` there would never run.
   */
  private val applicationScope: CoroutineScope,
) : ViewModel() {
  private val _uiState = MutableStateFlow(EditorUiState())
  val uiState = _uiState.asStateFlow()
  private var notebook: Notebook? = null
  private val save = NotebookSaveDebounce(viewModelScope) { persist() }

  /** Run, interrupt and complete. Driven straight from the screen; see [NotebookKernel]. */
  val kernel = NotebookKernel(_uiState, kernelGateway, viewModelScope, persist = { persist() })

  init {
    viewModelScope.launch {
      _uiState.value =
        try {
          notebookRepository.readNotebook(notebookId)
            .also { notebook = it }
            .toEditorUiState(defaultCellId = UUID.randomUUID().toString())
        } catch (e: IllegalArgumentException) {
          EditorUiState(error = e.message)
        }
    }
  }

  /**
   * Takes an edit from the cell's editor. Saving is debounced because a write per keystroke
   * would rewrite the file on every character; [onCleared] is the escape hatch for when the
   * screen is going away and the debounce will not get its chance.
   */
  fun editCell(cellId: String, source: String) {
    _uiState.update { it.withSource(cellId, source) }
    save.schedule()
  }

  /**
   * Retitles the notebook, from the app bar.
   *
   * The dialog decides *whether* to rename - it reports nothing for a blank or unchanged title -
   * so this writes whatever it is handed.
   *
   * ```
   * viewModel.renameNotebook("Math notes")
   * ```
   */
  fun renameNotebook(title: String) {
    val current = notebook ?: return
    // The in-memory copy has to move too. persist() writes `notebook.copy(cells = ...)`, so a
    // rename that only reached the repository would be overwritten by the next keystroke.
    notebook = current.copy(title = title)
    _uiState.update { it.copy(title = title) }
    viewModelScope.launch { notebookRepository.renameNotebook(notebookId, title) }
  }

  /**
   * Closes the notebook: flushes a save the debounce never got to, then closes the kernel socket.
   *
   * In that order, which is JupyterLab's - `DocumentWidgetManager.onClose` awaits
   * `context.save()` before `widget.dispose()`, and disposing the context is what disposes the
   * session (`packages/docmanager/src/widgetmanager.ts:335-388`).
   *
   * **Only when there is something to write.** `updatedAt` is the file's mtime, so a write is
   * what makes a notebook "updated" - saving on the way out would push a notebook to the top of
   * Recents for having been looked at. The pending job is cancelled before the flush so a
   * debounce that happens to be mid-flight cannot write the same thing twice.
   *
   * Public rather than protected because `clear()` is `internal` to lifecycle, so this is the
   * only seam a test has on leaving the screen.
   */
  public override fun onCleared() {
    val hadUnsavedWork = save.cancelPending()
    applicationScope.launch {
      if (hadUnsavedWork) persist()
      kernel.close(applicationScope)
    }
  }

  /**
   * Focus arrived in or left a cell's editor, which is the only thing that moves this notebook
   * between its two modes. The Compose analogue of JupyterLab's `_evtFocusIn`/`_evtFocusOut`
   * (`packages/notebook/src/widget.ts:3541-3599`), and it follows both closely.
   *
   * **Gained: select, then edit — in that order, and it is load-bearing.** JupyterLab sets
   * `activeCellIndex` before calling `setMode` and says why in as many words: *"Cell index needs
   * to be updated before changing mode, otherwise the previous cell may get un-rendered"*.
   * Editing is done *to* the selected cell, so selecting second would apply it to whichever cell
   * was selected before.
   *
   * **Lost: only if [cellId] is still the selected cell.** If focus went straight to another
   * cell, that cell has already selected itself and this is a stale report from the one it left.
   * The guard is JupyterLab's `relatedTarget` check under a different name - both are asking
   * "did focus go somewhere that still counts as editing?" - and without it a cell scrolling out
   * of a `LazyColumn` and being disposed would drop the notebook out of edit mode while the user
   * is typing in a different one.
   */
  fun cellFocusChanged(cellId: String, focused: Boolean) {
    _uiState.update { state ->
      when {
        focused -> state.withCellSelected(cellId).withMode(CellMode.EDIT)
        state.selectedCellId == cellId -> state.withMode(CellMode.COMMAND)
        else -> state
      }
    }
  }

  /**
   * The cell-management half of the editor: everything the cell menu offers.
   *
   * Each one is a pure transform in [CellEdits] followed by the same debounced save an edit
   * gets, so re-ordering a notebook survives leaving the screen exactly as typing does. Ids
   * are minted here rather than inside the transforms, which keeps those testable.
   */
  fun applyToCell(cellId: String, action: CellAction) {
    // Clamped after every transform, not only after Delete: it costs nothing, and a transform
    // added later cannot forget to.
    _uiState.update { it.applying(action, cellId).withSelectionClamped() }
    save.schedule()
  }

  private fun EditorUiState.applying(action: CellAction, cellId: String): EditorUiState = when (action) {
    is CellAction.Insert -> withCellInserted(cellId, action.kind, newId = newId())
    is CellAction.InsertAbove -> withCellInsertedAbove(cellId, action.kind, newId = newId())
    is CellAction.ChangeKind -> withCellKindChanged(cellId, action.kind)
    is CellAction.Merge -> withCellMerged(cellId, action.into, newId = newId())
    is CellAction.Move -> withCellMoved(cellId, action.move)
    CellAction.Copy -> withCellCopied(cellId)
    CellAction.Cut -> withCellCut(cellId, replacementId = newId())
    CellAction.Paste -> withClipboardPasted(cellId, newIds = clipboard.map { newId() })
    CellAction.Duplicate -> withCellDuplicated(cellId, newId = newId())
    CellAction.Delete -> withCellDeleted(cellId, replacementId = newId())
    CellAction.ClearOutputs -> withOutputsCleared(cellId)
    CellAction.ToggleSourceHidden -> withSourceHiddenToggled(cellId)
    CellAction.ToggleOutputsHidden -> withOutputsHiddenToggled(cellId)
  }

  /** Ids are minted here rather than inside the transforms, which keeps those testable. */
  private fun newId(): String = UUID.randomUUID().toString()

  private suspend fun persist() {
    val state = _uiState.value
    val updated = notebook?.copy(cells = state.withResultsOnCells()) ?: return
    notebook = updated
    _uiState.update { it.copy(cells = updated.cells, dirty = false) }
    notebookRepository.saveNotebook(updated)
  }
}
