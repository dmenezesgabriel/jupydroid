package com.jupydroid.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeLanguageServerPort
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.NotebookBreakpoints
import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.notebookLeafOf
import com.jupydroid.app.domain.model.pathAfterRename
import com.jupydroid.app.domain.port.EditorSettingsRepository
import com.jupydroid.app.domain.port.KernelDebugger
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.LanguageServerPort
import com.jupydroid.app.domain.port.NotebookExporter
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.formattingOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
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
  /**
   * Where a breakpoint outlives this ViewModel - see [NotebookBreakpoints]'s own doc for why
   * [debugger] cannot hold that state itself.
   */
  notebookBreakpoints: NotebookBreakpoints = NotebookBreakpoints(),
  /** DAP over the notebook's kernel session - the handshake [debugger] sends; see [NotebookDebugger]. */
  kernelDebugger: KernelDebugger = FakeKernelDebugger(),
  /**
   * The `autosave`/`autosaveInterval` pair the save machinery reads - defaulted the way
   * [openDocuments] is, so a test driving the editor alone reads the shipped defaults.
   */
  private val editorSettings: EditorSettingsRepository = FakeEditorSettingsRepository(),
  /**
   * The Space's language server, asked for completions before the kernel (P5c) - defaulted the
   * way [kernelDebugger] is, so a test driving the editor alone spawns nothing.
   */
  private val languageServer: LanguageServerPort = FakeLanguageServerPort(),
  /**
   * Where "the session ended elsewhere" is heard - the Running panel's shutdown lands this
   * notebook on No Kernel through it (P8f), the way `session.disposed` does in the reference.
   */
  kernelSessions: KernelSessions = FakeKernelSessions(),
  /**
   * The Space's kernelspec catalogue - what the saved `kernelspec.name` is resolved through
   * before a kernel is asked for, the way `specsManager.specs` feeds `getDefaultKernel`
   * (`sessioncontext.tsx:1927`). Defaulted the way [kernelSessions] is.
   */
  private val kernelSpecs: KernelSpecs = FakeKernelSpecs(),
) : ViewModel() {
  private val _uiState = MutableStateFlow(EditorUiState())
  val uiState = _uiState.asStateFlow()
  private var notebook: Notebook? = null

  /**
   * The settings the save machinery consults, as a `StateFlow` because the answers it needs are
   * synchronous: `schedule()` gates at call time, and the run-end `checkpoint` lambda picks its
   * branch on the current value.
   */
  private val settings = editorSettings.observeSettings()
    .stateIn(viewModelScope, SharingStarted.Eagerly, EditorSettings())

  private val save = DocumentAutosave(viewModelScope, settings, { _uiState.value.dirty }) { persist() }

  /** Set when this notebook's file is deleted, so [persist] stops putting it back. */
  private var notebookIsGone = false

  /**
   * Breakpoints in the gutter, and where the kernel stops once a run arms one. Driven straight
   * from the screen; see [NotebookDebugger]. Built before [kernel] since [NotebookCellRuns] arms
   * a cell's breakpoints before it runs.
   */
  val debugger = NotebookDebugger(_uiState, notebookBreakpoints, kernelDebugger)

  /** Run, interrupt and complete. Driven straight from the screen; see [NotebookKernel]. */
  val kernel =
    NotebookKernel(
      _uiState,
      kernelGateway,
      viewModelScope,
      activeCellRuns,
      // `context.save()` when autosave is on, model-dirty when it is not: a run's outputs are a
      // document change like any edit, so a notebook that ran unsaved owes a write - which is
      // what `Context.model.dirty` says after `execute_request` answers.
      checkpoint = {
        if (settings.value.autosaveEnabled) persist() else _uiState.update { it.copy(dirty = true) }
      },
      debugger = debugger,
      // `kernelShutdown`, read the way `SessionContext` reads `kernelPreference` - at dispose.
      shutdownOnClose = { settings.value.shutdownKernelOnClose },
      // `indentUnit` as the FormattingOptions a range-format must carry - the same live read.
      formattingOptions = { settings.value.indentUnit.formattingOptions() },
      languageServer = languageServer,
      kernelSessions = kernelSessions,
      kernelSpecs = kernelSpecs,
      // `kernelChanged` -> `_updateSpec` (`panel.ts:207,248-258`): a bound kernel is persisted
      // as `metadata.kernelspec` - dirty, then the autosave writes it like any other edit.
      onKernelBound = { bound, spec ->
        notebook = notebook?.withKernelSpec(bound.kernelName, spec)
        _uiState.update { it.copy(dirty = true) }
        save.schedule()
      },
    )

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
  val document = NotebookDocument(viewModelScope, _uiState, save, notebookId = { notebookId }, openedAs = notebookId)

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
      seedKernelPreference()
      debugger.seed(_uiState.value.cells)
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
          is WorkspaceChange.Deleted ->
            if (change.path == notebookId) {
              notebookIsGone = true
              save.discard()
              _uiState.update { it.copy(gone = true) }
            }
        }
      }
    }
  }

  /**
   * `Context._populate`'s second half (`docregistry/src/context.ts:587-603`): the saved
   * `kernelspec.name`/`language_info.name` become the session's `kernelPreference`, resolved
   * through the Space's catalogue before it ever reaches a connect. When it resolves to
   * nothing - a kernelspec uninstalled since the file was written - the reference opens
   * `selectKernel` right here; the owe is what [EditorUiState.kernelPickRequired] counts.
   */
  private suspend fun seedKernelPreference() {
    val preference = notebook?.kernelPreference ?: return
    kernel.kernelPreference = preference
    if (kernelSpecs.specs(BASE_SPACE).preferredKernelName(preference) != null) return
    _uiState.update { it.copy(kernelPickRequired = it.kernelPickRequired + 1) }
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
   * Takes on the id the repository moved the file to.
   *
   * Reached only through `WorkspaceChange.Renamed`: renames ask the workspace layer - the tab
   * strip's `docmanager:rename` equivalent and the browser's row alike - so they arrive here as
   * change events, `Context._onFileChanged` style, and never through a method on this screen.
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

  /**
   * `docmanager:save`, run on the way out - the screen's close gate and the tab strip's both.
   * Delegated to [document] so the two callers share the one flush-and-check rather than keeping
   * a copy each.
   */
  fun saveNow(onSaved: () -> Unit) = document.saveNow(onSaved)

  /**
   * The close dialog's Discard: owed work is dropped - `SaveHandler.dispose()` clears its timer
   * and writes nothing, so the close that follows must not flush it back.
   */
  fun discardChanges() = document.discardChanges()

  public override fun onCleared() {
    openDocuments.closed(document)
    val hadUnsavedWork = save.cancelPending()
    applicationScope.launch {
      if (!save.discarded && (hadUnsavedWork || _uiState.value.dirty)) persist()
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
