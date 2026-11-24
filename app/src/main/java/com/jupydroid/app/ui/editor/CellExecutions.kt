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

/**
 * A run the kernel still owes an answer to - queued for it, or being executed.
 *
 * The question a closing screen asks before it lets go of its kernel: a run in flight is the
 * one thing `SessionContext.dispose()` never ends either - the client connection drops and the
 * kernel keeps working server-side (`sessioncontext.tsx:679-709`), which is only survivable
 * because the run's collection, not the screen, owns it from there.
 */
val EditorUiState.runInFlight: Boolean
  get() = executions.values.any { it.status == ExecutionStatus.RUNNING || it.status == ExecutionStatus.QUEUED }

fun EditorUiState.executionOf(cellId: String): CellExecution? = executions[cellId]

/** Never null, so callers do not have to spell out what "no record" means every time. */
fun EditorUiState.statusOf(cellId: String): ExecutionStatus = executions[cellId]?.status ?: ExecutionStatus.NOT_STARTED

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
).withSearchedOutputsRecomputed()

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

/**
 * Forgets whatever was in flight, which is what a restart leaves behind.
 *
 * A restart replaces the interpreter, so a RUNNING or QUEUED cell did not finish - it never
 * happened, and the events that would have ended it died with the process. Left alone such a cell
 * spins for ever.
 *
 * What already finished stays: JupyterLab keeps outputs and counts across a plain restart and
 * offers "restart and clear" separately, so wiping them here would make the two the same thing.
 */
fun EditorUiState.abandoningRun(): EditorUiState = copy(
  executions = executions.filterValues {
    it.status != ExecutionStatus.RUNNING && it.status != ExecutionStatus.QUEUED
  },
  // The interpreter that asked is gone, and nothing is left to read an answer.
  inputRequests = emptyMap(),
)

/** Drops a cell's record entirely, for when the cell itself goes. */
fun EditorUiState.withoutExecution(cellId: String): EditorUiState = copy(executions = executions - cellId)

private fun EditorUiState.stopping(cellId: String, at: Instant, status: ExecutionStatus): EditorUiState {
  val started = executions[cellId]?.startedAt
  // A prompt belongs to the run that asked, so the run ending - Stop included - closes it.
  return copy(
    executions = executions + (cellId to CellExecution(status, startedAt = started, endedAt = at)),
    inputRequests = inputRequests - cellId,
  )
}
