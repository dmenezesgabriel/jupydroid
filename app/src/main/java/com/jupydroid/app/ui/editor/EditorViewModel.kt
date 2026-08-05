package com.jupydroid.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.NotebookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditorUiState(
  val title: String = "",
  val cells: List<Cell> = emptyList(),
  val loaded: Boolean = false,
  val error: String? = null,
  /**
   * Held here rather than on [Cell] because outputs are not persisted yet - nbformat
   * round-tripping them is Step 7's job, and putting them in the domain model now would
   * change what `DiskNotebookRepository` writes before anything reads it back.
   */
  val outputs: Map<String, List<CellOutput>> = emptyMap(),
  val executionCounts: Map<String, Int> = emptyMap(),
  val runningCellId: String? = null,
)

class EditorViewModel(
  notebookId: String,
  private val notebookRepository: NotebookRepository,
  private val kernelGateway: KernelGateway,
) : ViewModel() {
  private val _uiState = MutableStateFlow(EditorUiState())
  val uiState = _uiState.asStateFlow()
  private var session: KernelSession? = null

  init {
    viewModelScope.launch {
      _uiState.value =
        try {
          val notebook = notebookRepository.readNotebook(notebookId)
          EditorUiState(title = notebook.title, cells = notebook.cells, loaded = true)
        } catch (e: IllegalArgumentException) {
          EditorUiState(error = e.message)
        }
    }
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

  /** Returns whether the cell finished without raising, which is what [runAll] gates on. */
  private suspend fun execute(cell: Cell): Boolean {
    startRun(cell.id)
    kernelGateway.execute(kernelSession(), cell.id, cell.source)
      .catch { failure -> emit(kernelFailure(cell.id, failure)) }
      .collect(::apply)
    _uiState.update { it.copy(runningCellId = null) }
    return _uiState.value.outputs[cell.id].orEmpty().none { it is CellOutput.Error }
  }

  /** Clears the previous run's output first: a re-run replaces its output, never appends. */
  private fun startRun(cellId: String) {
    _uiState.update { it.copy(runningCellId = cellId, outputs = it.outputs - cellId) }
  }

  private fun apply(event: KernelEvent) {
    _uiState.update { state ->
      when (event) {
        is KernelEvent.Output ->
          state.copy(outputs = state.outputs + (event.cellId to state.outputs[event.cellId].orEmpty() + event.output))
        is KernelEvent.ExecutionCount ->
          state.copy(executionCounts = state.executionCounts + (event.cellId to event.count))
        KernelEvent.Idle -> state.copy(runningCellId = null)
      }
    }
  }

  /**
   * A failure becomes an error output in the cell rather than a thrown exception: an
   * unreachable kernel must say so where the user is looking, not leave a spinner forever.
   */
  private fun kernelFailure(cellId: String, failure: Throwable) = KernelEvent.Output(
    cellId,
    CellOutput.Error(
      name = failure::class.simpleName.orEmpty(),
      value = failure.message.orEmpty(),
      traceback = emptyList(),
    ),
  )

  private suspend fun kernelSession(): KernelSession =
    session ?: kernelGateway.connect(BASE_SPACE).also { session = it }

  private companion object {
    /** Step 17 introduces real Spaces; until then every notebook runs in the base one. */
    val BASE_SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
