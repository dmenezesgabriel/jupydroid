package com.jupydroid.app.ui.editor

import java.time.Instant

/**
 * The transitions a cell's [CellExecution] makes, as pure functions.
 *
 * Beside [CellEdits] and [CellSelection] for the same reason: what the notebook looks like after
 * the kernel says something is decided by code a test can call directly.
 *
 * They map onto the reference's four `ExecutionEvent`s - [queueingRun] is `ExecutionSubmitted`,
 * [startingRun] is `ExecutionStarted`, [finishedRun] and [cancellingRun] are `ExecutionStopped`.
 */

/** The cell currently executing, or null. At most one: the kernel runs cells one at a time. */
val EditorUiState.runningCellId: String?
  get() = executions.entries.firstOrNull { it.value.status == ExecutionStatus.RUNNING }?.key

fun EditorUiState.executionOf(cellId: String): CellExecution? = executions[cellId]

/** Never null, so callers do not have to spell out what "no record" means every time. */
fun EditorUiState.statusOf(cellId: String): ExecutionStatus =
  executions[cellId]?.status ?: ExecutionStatus.NOT_STARTED

/**
 * Claims every cell a Run All is about to work through, before any of them starts.
 *
 * Marking them up front is what makes "waiting its turn" visible; done per cell as each began,
 * a queued cell would be indistinguishable from one nobody had run.
 */
fun EditorUiState.queueingRun(cellIds: List<String>): EditorUiState =
  copy(executions = executions + cellIds.associateWith { CellExecution(ExecutionStatus.QUEUED) })

/** Clears the previous run's output too: a re-run replaces its output, never appends. */
fun EditorUiState.startingRun(cellId: String, at: Instant): EditorUiState = copy(
  executions = executions + (cellId to CellExecution(ExecutionStatus.RUNNING, startedAt = at)),
  outputs = outputs - cellId,
)

fun EditorUiState.finishedRun(cellId: String, at: Instant, failed: Boolean): EditorUiState =
  stopping(cellId, at, if (failed) ExecutionStatus.STOPPED_ERROR else ExecutionStatus.STOPPED_OK)

/** The Stop button. See [ExecutionStatus.CANCELLED] for why this is not a failure. */
fun EditorUiState.cancellingRun(cellId: String, at: Instant): EditorUiState =
  stopping(cellId, at, ExecutionStatus.CANCELLED)

/**
 * Turns whatever is still queued into [ExecutionStatus.SKIPPED], which is what a Run All that
 * stopped at a failure leaves behind.
 */
fun EditorUiState.skippingQueued(): EditorUiState = copy(
  executions = executions.mapValues { (_, execution) ->
    if (execution.status == ExecutionStatus.QUEUED) execution.copy(status = ExecutionStatus.SKIPPED) else execution
  },
)

/** Drops a cell's record entirely, for when the cell itself goes. */
fun EditorUiState.withoutExecution(cellId: String): EditorUiState = copy(executions = executions - cellId)

private fun EditorUiState.stopping(cellId: String, at: Instant, status: ExecutionStatus): EditorUiState {
  val started = executions[cellId]?.startedAt
  return copy(executions = executions + (cellId to CellExecution(status, startedAt = started, endedAt = at)))
}
