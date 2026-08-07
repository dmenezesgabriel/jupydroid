package com.jupydroid.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

class EditorViewModel(
  private val notebookId: String,
  private val notebookRepository: NotebookRepository,
  private val kernelGateway: KernelGateway,
  /**
   * Outlives this ViewModel, because [onCleared] runs *after* `viewModelScope` has been
   * cancelled - `ViewModel.clear()` closes the closeables and only then calls `onCleared`
   * (`ViewModel.jvm.kt:66-68`). Work launched on `viewModelScope` there would never run.
   */
  private val applicationScope: CoroutineScope,
) : ViewModel() {
  private val _uiState = MutableStateFlow(EditorUiState())
  val uiState = _uiState.asStateFlow()
  private var session: KernelSession? = null
  private var notebook: Notebook? = null
  private var pendingSave: Job? = null

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
    scheduleSave()
  }

  private fun scheduleSave() {
    pendingSave?.cancel()
    pendingSave = viewModelScope.launch {
      delay(SAVE_DEBOUNCE_MILLIS)
      persist()
    }
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
   * **Closing the socket is not shutting the kernel down**, and that is deliberate:
   * `SessionContext.dispose()` disposes the client connection and shuts the kernel down only
   * when `shutdownOnDispose` is set, which defaults to false
   * (`packages/apputils/src/sessioncontext.tsx:679-709`).
   *
   * Public rather than protected because `clear()` is `internal` to lifecycle, so this is the
   * only seam a test has on leaving the screen.
   */
  public override fun onCleared() {
    val hadUnsavedWork = pendingSave?.isActive == true
    pendingSave?.cancel()
    val openSession = session
    session = null
    applicationScope.launch {
      if (hadUnsavedWork) persist()
      openSession?.let { kernelGateway.disconnect(it) }
    }
  }

  /**
   * The cell-management half of the editor: everything the long-press menu offers.
   *
   * Each one is a pure transform in [CellEdits] followed by the same debounced save an edit
   * gets, so re-ordering a notebook survives leaving the screen exactly as typing does. Ids
   * are minted here rather than inside the transforms, which keeps those testable.
   */
  fun applyToCell(cellId: String, action: CellAction) {
    _uiState.update { state ->
      when (action) {
        is CellAction.Insert -> state.withCellInserted(cellId, action.kind, newId = UUID.randomUUID().toString())
        is CellAction.Move -> state.withCellMoved(cellId, action.move)
        CellAction.Duplicate -> state.withCellDuplicated(cellId, newId = UUID.randomUUID().toString())
        CellAction.Delete -> state.withCellDeleted(cellId, replacementId = UUID.randomUUID().toString())
        CellAction.ClearOutputs -> state.withOutputsCleared(cellId)
        CellAction.ToggleSourceHidden -> state.withSourceHiddenToggled(cellId)
        CellAction.ToggleOutputsHidden -> state.withOutputsHiddenToggled(cellId)
      }
    }
    scheduleSave()
  }

  /**
   * Runs one cell and streams its output into the state as it arrives, so a long-running
   * cell shows what it has printed rather than nothing until it finishes.
   */
  fun runCell(cell: Cell) {
    viewModelScope.launch { execute(cell) }
  }

  /**
   * Runs every code cell in order, stopping at the first that raises - which is what Jupyter
   * does with a queued run. Carrying on would run later cells against state the failed one
   * never produced, and their output would be wrong rather than merely absent.
   */
  fun runAll() {
    viewModelScope.launch {
      for (cell in _uiState.value.cells.filter { it.kind == CellKind.CODE }) {
        if (!execute(cell)) return@launch
      }
    }
  }

  /**
   * Interrupts the kernel, which is how a running cell is stopped: the cell itself has no
   * handle on the kernel's work. Does nothing when nothing is running, since the kernel may
   * be busy with somebody else's cell.
   */
  fun stopRunningCell() {
    if (_uiState.value.runningCellId == null) return
    viewModelScope.launch { session?.let { kernelGateway.interrupt(it) } }
  }

  /**
   * What the kernel offers for the code under the cursor.
   *
   * Uses the session the notebook already has and never opens one. Typing must not boot a
   * Python runtime the user has not asked for by running a cell - `BaseSpaceKernelGateway`
   * installs a Space and starts `jupyter_server` on first use, which is seconds and ~143 MB.
   *
   * A completion that fails is silence, not a crash: the socket dying is worth reporting when
   * a cell is running, and worth ignoring when the user is only typing.
   */
  @Suppress("SwallowedException") // a kernel that cannot answer has nothing to say, which is not an error
  suspend fun completionsAt(code: String, cursorOffset: Int): CompletionResult {
    val open = session ?: return CompletionResult.NONE
    return try {
      kernelGateway.complete(open, code, cursorOffset)
    } catch (e: IllegalStateException) {
      CompletionResult.NONE
    }
  }

  /** Returns whether the cell finished without raising, which is what [runAll] gates on. */
  private suspend fun execute(cell: Cell): Boolean {
    _uiState.update { it.startingRun(cell.id) }
    kernelGateway.execute(kernelSession(), cell.id, cell.source)
      .catch { failure -> emit(kernelFailure(cell.id, failure)) }
      .collect { event -> _uiState.update { state -> state.after(event) } }
    _uiState.update { it.copy(runningCellId = null) }
    persist()
    return _uiState.value.outputs[cell.id].orEmpty().none { it is CellOutput.Error }
  }

  /**
   * Writes what is on screen - sources, outputs and counts - back to the notebook. Called
   * per cell rather than per Run All, so a long run that is interrupted still keeps what the
   * cells before it produced.
   */
  private suspend fun persist() {
    val state = _uiState.value
    val updated = notebook?.copy(cells = state.withResultsOnCells()) ?: return
    notebook = updated
    _uiState.update { it.copy(cells = updated.cells, dirty = false) }
    notebookRepository.saveNotebook(updated)
  }

  private suspend fun kernelSession(): KernelSession =
    session ?: kernelGateway.connect(BASE_SPACE).also { session = it }

  private companion object {
    /** Step 17 introduces real Spaces; until then every notebook runs in the base one. */
    val BASE_SPACE = Space(id = "base", name = "Base", kernels = emptyList())

    /** Long enough that a burst of typing is one write, short enough to feel immediate. */
    const val SAVE_DEBOUNCE_MILLIS = 500L
  }
}
