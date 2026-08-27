package com.jupydroid.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.notebookLeafOf
import com.jupydroid.app.domain.model.pathAfterRename
import com.jupydroid.app.domain.port.FileNameTakenException
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The document half of the editor: what the notebook *is*, where it lives, and when it is written.
 *
 * Two collaborators live next door and are reached through this, each split off on detekt's
 * `TooManyFunctions` and each time detekt was right: [NotebookKernel] via [kernel] is what the
 * notebook asks its kernel, and [NotebookCellEdits] via [edits] is what it does to its own cells.
 * All three share only the state flow - running a cell has nothing to say about renaming a file,
 * and neither has anything to say about loading one.
 *
 * Typing stays here, in [editCell], and that is not an oversight: text has its own history in
 * Compose's `TextFieldState`, exactly as the reference keeps `notebook:undo` on the editor and
 * `notebook:undo-cell-action` on the document. See [CellUndo].
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
   * The tree the notebook sits in, for two things and neither of them this notebook's own bytes -
   * those go through [notebookRepository].
   *
   * **To hear where the file went.** A rename can come from the file browser sitting beside the
   * document on a tablet, and the reference's open document learns of it from the contents layer
   * rather than from that browser (`Context._onFileChanged`, `docregistry/src/context.ts:93`).
   *
   * **And to put an export beside it** (P2j), which is a file the notebook is not - see
   * [NotebookExport].
   */
  private val workspaceRepository: WorkspaceRepository,
  kernelGateway: KernelGateway,
  /**
   * `notebook:export-to-format`. Not a kernel act: nbconvert reads the *file*, so a notebook with
   * no kernel exports perfectly well - which is why it is a port of its own and not a call on
   * [kernelGateway].
   */
  notebookExporter: NotebookExporter,
  /**
   * Outlives this ViewModel, because [onCleared] runs *after* `viewModelScope` has been
   * cancelled - `ViewModel.clear()` closes the closeables and only then calls `onCleared`
   * (`ViewModel.jvm.kt:66-68`). Work launched on `viewModelScope` there would never run.
   */
  private val applicationScope: CoroutineScope,
  /**
   * Only written to, and only by [kernel]: it is the app's count of runs in flight, and what P1c's
   * backstop reads before it stops the server under a backgrounded computation.
   */
  activeCellRuns: ActiveCellRuns,
  /**
   * Where this notebook says it is open, so a workspace replace edits its cells rather than
   * rewriting the file underneath it (`bulkTextEdits.ts:227`).
   *
   * Defaulted for the same reason [com.jupydroid.app.ui.fileeditor.FileEditorViewModel]'s is: the
   * cases that drive an editor alone are not made to care, and a registry nobody reads costs
   * nothing.
   */
  private val openDocuments: OpenDocuments = OpenDocuments(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(EditorUiState())
  val uiState = _uiState.asStateFlow()
  private var notebook: Notebook? = null
  private val save = NotebookSaveDebounce(viewModelScope) { persist() }

  /** Set when this notebook's file is deleted, so [persist] stops putting it back. */
  private var notebookIsGone = false

  /** Run, interrupt and complete. Driven straight from the screen; see [NotebookKernel]. */
  val kernel =
    NotebookKernel(_uiState, kernelGateway, viewModelScope, activeCellRuns, persist = { persist() })

  /** The cell menu and its undo history. Driven straight from the screen; see [NotebookCellEdits]. */
  val edits = NotebookCellEdits(_uiState, save)

  /** Which cells the menu means. Nothing here is written, so it takes no save; see [NotebookCellSelection]. */
  val selection = NotebookCellSelection(_uiState)

  /** The find bar. Writes only when a match is reached out of hiding; see [NotebookSearch]. */
  val search = NotebookSearch(_uiState, save)

  /** The other half of that bar - the only part of searching that rewrites cells. See [NotebookReplace]. */
  val replace = NotebookReplace(_uiState, save)

  /**
   * This notebook as something a **workspace** replace can edit by path; see [NotebookDocument].
   *
   * Registered from here rather than by itself, because the lifetime is this ViewModel's.
   */
  val document = NotebookDocument(_uiState, save, notebookId = { notebookId })

  /**
   * `notebook:export-to-format`. Writes a file *beside* the notebook rather than to it, so it
   * takes the workspace and not the save; see [NotebookExport].
   */
  val export = NotebookExport(
    uiState = _uiState,
    exporter = notebookExporter,
    workspace = workspaceRepository,
    scope = viewModelScope,
    // Asked for rather than captured: a rename moves the file while this object lives on.
    notebookId = { notebookId },
    persist = { persist() },
  )

  init {
    // Open before it has loaded, so a replace can never slip in between the read and the register
    // and rewrite a file this editor is about to overwrite from stale memory. Until the cells are
    // here [sourceOf] answers null, which the engine reads as the conflict it is.
    openDocuments.opened(document)
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
    // `onTextChange` -> `reset()` (`packages/notebook/src/history.ts:313-319`): typing ends a walk
    // through the kernel's history, so the next tap starts one against the cell as it is now. It
    // ignores the edit the walk itself caused, which is why it is told the source rather than that
    // *an* edit happened.
    kernel.history.sourceReported(source)
    // Re-found after the edit, not before: *"startQuery is also used as 'restartQuery' after each
    // text change"* (`packages/notebook/src/searchprovider.ts:391-395`). A no-op while the find
    // bar is closed, which is almost always.
    _uiState.update { it.withSource(cellId, source).withSearchRecomputed() }
    save.schedule()
  }

  /**
   * A rendered markdown cell was tapped, so it shows its source - `showEditor()`.
   *
   * Here rather than in the cell because a *run* has to be able to close it again, and a flag only
   * the composable could see left an open editor open through a Run All. See [MarkdownEditing].
   */
  fun markdownEditRequested(cellId: String) {
    _uiState.update { it.withMarkdownEditorOpened(cellId) }
  }

  /**
   * `notebook:toggle-all-cell-line-numbers`, from the notebook's View menu.
   *
   * Neither a cell edit nor a kernel act, so it is here rather than on [edits] or [kernel]: it
   * changes how the document is drawn and nothing the document holds. No save is scheduled, which
   * is the parity point - `toggleAllLineNumbers` writes `notebook.editorConfig` and never the
   * model (`packages/notebook/src/actions.tsx:1792-1812`).
   *
   * ```
   * viewModel.toggleLineNumbers()
   * ```
   */
  fun toggleLineNumbers() {
    _uiState.update { it.copy(lineNumbersShown = !it.lineNumbersShown) }
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
    openDocuments.closed(document)
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
    // A history walk belongs to the cell it started in - see [NotebookHistory.reset], which is a
    // narrowing of the reference rather than a copy of it. Reset on *either* edge: the cell being
    // left is done with its walk, and the cell being entered must not inherit one.
    kernel.history.reset()
    _uiState.update { state ->
      when {
        focused -> state.withCellSelected(cellId).withMode(CellMode.EDIT)
        state.selectedCellId == cellId -> state.withMode(CellMode.COMMAND)
        else -> state
      }
    }
  }

  /**
   * Writes the notebook back - and **adopts the result only if the write happened**.
   *
   * Two things used to be true before the save rather than after it, and both were wrong when it
   * failed: `dirty` was cleared, so a notebook that owed a write claimed not to, and the exception
   * went straight into `viewModelScope`, where it is fatal. A cell that removes the notebook's
   * folder is enough to cause it, and nothing announces that - `WorkspaceRepository.changes`
   * carries what this app moved, not what the kernel did.
   *
   * `EditorUiState.dirty` says it is *"cleared by the save, not by the edit"*, which is only true
   * from here. See [EditorUiState.saveFailure] for why the report is not `error`.
   */
  private suspend fun persist() {
    if (notebookIsGone) return
    val updated = notebook?.copy(cells = _uiState.value.withResultsOnCells()) ?: return
    runCatching { notebookRepository.saveNotebook(updated) }
      .onSuccess {
        notebook = updated
        _uiState.update { it.copy(cells = updated.cells, dirty = false, saveFailure = null) }
      }
      .onFailure { failure -> _uiState.update { it.copy(saveFailure = failure.message) } }
  }
}
