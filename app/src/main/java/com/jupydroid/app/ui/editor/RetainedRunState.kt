package com.jupydroid.app.ui.editor

/**
 * The state a reopening screen takes from the run it adopted.
 *
 * Fresh disk state is the document - cells, title and the results they already had - so this only
 * carries what the run itself owns: executions and their outputs, live kernel and debugger state,
 * and the save failure the cleared screen was still owed. Sitting state - selection, undo,
 * clipboard, the picker, breakpoints - belongs to the new sitting and is seeded afresh.
 */
internal fun EditorUiState.withRetainedRunFrom(retained: EditorUiState): EditorUiState = copy(
  outputs = retained.outputs,
  clearsPending = retained.clearsPending,
  inputRequests = retained.inputRequests,
  executionCounts = retained.executionCounts,
  executions = retained.executions,
  dirty = retained.dirty,
  saveFailure = retained.saveFailure,
  saveConflict = retained.saveConflict,
  diagnosticsByCellId = retained.diagnosticsByCellId,
  stoppedAt = retained.stoppedAt,
  debugStack = retained.debugStack,
  stepping = retained.stepping,
  kernelConnected = retained.kernelConnected,
  kernelRestarting = retained.kernelRestarting,
  kernelPickRequired = retained.kernelPickRequired,
  kernelDisplayName = retained.kernelDisplayName,
  kernelChanging = retained.kernelChanging,
)
