package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.port.KernelEvent

data class EditorUiState(
  val title: String = "",
  val cells: List<Cell> = emptyList(),
  val loaded: Boolean = false,
  val error: String? = null,
  /**
   * Held here rather than on [Cell] while a run is in flight: the cell list is what gets
   * written to disk, and half a run's output has no business being saved.
   */
  val outputs: Map<String, List<CellOutput>> = emptyMap(),
  val executionCounts: Map<String, Int> = emptyMap(),
  val runningCellId: String? = null,
  /** Edited since the last write. Cleared by the save, not by the edit. */
  val dirty: Boolean = false,
  /** JupyterLab's `activeCellIndex`, by id: the one cell every cell-scoped action means. */
  val selectedCellId: String? = null,
  val mode: CellMode = CellMode.COMMAND,
)

/**
 * The state transitions an editor makes, as pure functions.
 *
 * Kept apart from the ViewModel so what the UI shows after a given kernel event is decided by
 * something a test can call directly, with no coroutines or repository in the way.
 */
fun EditorUiState.after(event: KernelEvent): EditorUiState = when (event) {
  is KernelEvent.Output ->
    copy(outputs = outputs + (event.cellId to outputs[event.cellId].orEmpty() + event.output))

  is KernelEvent.ExecutionCount ->
    copy(executionCounts = executionCounts + (event.cellId to event.count))

  KernelEvent.Idle -> copy(runningCellId = null)
}

/** Clears the previous run's output: a re-run replaces its output, never appends. */
fun EditorUiState.startingRun(cellId: String): EditorUiState =
  copy(runningCellId = cellId, outputs = outputs - cellId)

fun EditorUiState.withSource(cellId: String, source: String): EditorUiState =
  copy(cells = cells.map { if (it.id == cellId) it.copy(source = source) else it }, dirty = true)

/**
 * Seeded from what was saved, so reopening a notebook shows the results it already had.
 *
 * The inverse of [withResultsOnCells], and kept beside it: how a notebook enters this state and
 * how it leaves are one idea, and reading them apart is how they drift.
 *
 * **A notebook being looked at never has zero cells.** [defaultCellId] names the empty code cell
 * a cell-less notebook is given, so there is always something to type into. That is JupyterLab's
 * rule and it lives here for the same reason it lives in its view layer
 * (`packages/notebook/src/widget.ts:606`): one place covers a notebook just created, one
 * imported with no cells, and the last cell being deleted. It is not an edit - nothing was
 * typed - so the notebook is not dirty and is not written back, which would only bump its
 * timestamp and reorder Recents for having been opened.
 */
fun Notebook.toEditorUiState(defaultCellId: String) = EditorUiState(
  title = title,
  cells = cells.ifEmpty { listOf(Cell(defaultCellId, CellKind.CODE, "")) },
  loaded = true,
  outputs = cells.filter { it.outputs.isNotEmpty() }.associate { it.id to it.outputs },
  executionCounts = cells.mapNotNull { cell -> cell.executionCount?.let { cell.id to it } }.toMap(),
)

/** Moves this run's results onto the cells, which is what gets persisted. */
fun EditorUiState.withResultsOnCells(): List<Cell> =
  cells.map { it.copy(outputs = outputs[it.id].orEmpty(), executionCount = executionCounts[it.id]) }

/**
 * A failure becomes an error output in the cell rather than a thrown exception: an
 * unreachable kernel must say so where the user is looking, not leave a spinner for ever.
 */
fun kernelFailure(cellId: String, failure: Throwable) = KernelEvent.Output(
  cellId,
  CellOutput.Error(
    name = failure::class.simpleName.orEmpty(),
    value = failure.message.orEmpty(),
    traceback = emptyList(),
  ),
)
