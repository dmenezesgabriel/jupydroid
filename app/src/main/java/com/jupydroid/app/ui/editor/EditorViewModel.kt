package com.jupydroid.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.notebookLeafOf
import com.jupydroid.app.domain.model.pathAfterRename
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.FileNameTakenException
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The document half of the editor: what the notebook *is*, and every change to it.
 *
 * What the notebook asks its kernel lives next door in [NotebookKernel] and is reached through
 * [kernel]. The split is detekt's doing and detekt was right - the two halves share only the
 * state flow, and running a cell has nothing to say about renaming a file.
 */
class EditorViewModel(
  /**
   * The notebook this editor is on - **mutable**, because the id is the file's name and a rename
   * moves the file.
   *
   * This is JupyterLab's `Context._path`, which `_updatePath` reassigns in place while the open
   * widget carries on (`packages/docregistry/src/context.ts`). Keeping it here rather than in the
   * navigation key is what lets a rename leave the editor's cells, outputs and execution state
   * alone; re-creating the screen instead would throw away everything that is not on disk.
   */
  private var notebookId: String,
  private val notebookRepository: NotebookRepository,
  /**
   * Not for reading or writing this notebook - only to hear where it went.
   *
   * A rename can come from the file browser sitting beside the document on a tablet, and the
   * reference's open document learns of it from the contents layer rather than from that browser
   * (`Context._onFileChanged`, `docregistry/src/context.ts:93`).
   */
  workspaceRepository: WorkspaceRepository,
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

  /** Set when this notebook's file is deleted, so [persist] stops putting it back. */
  private var notebookIsGone = false

  /** Run, interrupt and complete. Driven straight from the screen; see [NotebookKernel]. */
  val kernel = NotebookKernel(_uiState, kernelGateway, viewModelScope, persist = { persist() })

  init {
    viewModelScope.launch {
      _uiState.value =
        try {
          notebookRepository.readNotebook(notebookId)
            .also { notebook = it }
            .toEditorUiState(defaultCellId = newCellId())
        } catch (e: IllegalArgumentException) {
          EditorUiState(error = e.message)
        }
    }
    // The rename that did not come from this screen - from the browser beside it, or from a folder
    // above it. `Context._onFileChanged` -> `_updatePath`, and without it the next debounced save
    // writes the old path back into existence.
    viewModelScope.launch {
      workspaceRepository.changes.collect { change ->
        when (change) {
          is WorkspaceChange.Renamed ->
            pathAfterRename(notebookId, change.from, change.to)?.let { adopt(it) }
          // The file is gone, so nothing more goes to it. `SaveHandler.dispose()` clears its timer
          // and writes nothing; ours has to say so, because `onCleared` *does* write and a
          // farewell flush after a delete puts the notebook back.
          is WorkspaceChange.Deleted -> if (change.path == notebookId) stopWriting()
        }
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
    val before = current.title
    notebook = current.copy(title = title)
    // `title` is a name; the bar shows names. `before` is the notebook's path, so rolling back
    // has to take the leaf out of it again - see [applyRename].
    _uiState.update { it.copy(title = title) }
    viewModelScope.launch { applyRename(title, before) }
  }

  /**
   * Adopts the new id, or puts the old name back when the repository refuses.
   *
   * X1b gave a rename the power to fail - the name is the file, so `a/b` is not a name and a name
   * already taken would overwrite somebody else's notebook. Refusals arrive as exceptions, and an
   * exception in a bare `viewModelScope.launch` is uncaught and fatal: typing a `/` into the
   * rename dialog crashed the app.
   *
   * On success the id is adopted, which is `Context._updatePath` - the document did not change,
   * only where it lives.
   */
  private suspend fun applyRename(title: String, before: String) {
    runCatching { notebookRepository.renameNotebook(notebookId, title) }
      .onSuccess { moved -> adopt(moved) }
      .onFailure { failure ->
        notebook = notebook?.copy(title = before)
        // Only a taken name becomes a question, exactly as only a 409 does in `renameFile`.
        val taken = if (failure is FileNameTakenException) title else null
        _uiState.update { it.copy(title = notebookLeafOf(before), nameTaken = taken) }
      }
  }

  /**
   * Answers "already exists, overwrite?" - yes replaces the file, no keeps the current name.
   *
   * One method taking the answer rather than two named for the outcomes, because that is the shape
   * the reference has: `shouldOverwrite(path): Promise<boolean>`, with `renameFile` acting on the
   * boolean. The name comes from state, so there is no second copy to fall out of step.
   */
  fun answerOverwrite(accepted: Boolean) {
    val taken = _uiState.value.nameTaken ?: return
    _uiState.update { it.copy(nameTaken = null) }
    if (!accepted) return
    viewModelScope.launch {
      runCatching { notebookRepository.overwriteNotebook(notebookId, taken) }.onSuccess { adopt(it) }
    }
  }

  /**
   * The file is gone: nothing more is written to it, and the screen is told so it can close.
   *
   * `SaveHandler.dispose()` clears its timer and writes nothing; ours has to say so, because
   * [onCleared] *does* write and a farewell flush after a delete puts the notebook back.
   */
  private fun stopWriting() {
    notebookIsGone = true
    save.cancelPending()
    _uiState.update { it.copy(gone = true) }
  }

  /**
   * Takes on the id the repository moved the file to.
   *
   * `Context._updatePath`: the document did not change, only where it lives - so the id is
   * reassigned in place and everything the editor holds carries on.
   */
  private fun adopt(moved: String) {
    notebookId = moved
    notebook = notebook?.copy(id = moved, title = moved)
    // The id is the path and the bar shows the name: `PathExt.basename(sender.localPath)`
    // (`packages/docregistry/src/default.ts:618-621`).
    _uiState.update { it.copy(title = notebookLeafOf(moved), notebookId = moved) }
  }

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


  private suspend fun persist() {
    if (notebookIsGone) return
    val state = _uiState.value
    val updated = notebook?.copy(cells = state.withResultsOnCells()) ?: return
    notebook = updated
    _uiState.update { it.copy(cells = updated.cells, dirty = false) }
    notebookRepository.saveNotebook(updated)
  }
}
