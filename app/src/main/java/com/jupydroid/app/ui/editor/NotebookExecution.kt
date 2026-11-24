package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.ActiveCellRuns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * A run that outlived the screen that started it: the kernel still collecting, the state flow
 * still being written, and the watch that ends both when the last run finishes with nobody
 * left to show it.
 *
 * This is the piece `SessionContext.dispose()` never had to build - in the reference the client
 * connection simply drops and whatever the kernel was doing is nobody's problem
 * (`packages/apputils/src/sessioncontext.tsx:679-709`). Ours keeps the collection alive on
 * purpose, so it also has to decide when it is done: [watchDetached] ends the kernel's
 * lifetime and reports the drain the moment no execution is RUNNING or QUEUED **and** no run is
 * counted in flight. Both halves are load-bearing - the last cell's `finishedRun` lands in the
 * state before its `checkpoint()` returns inside `ActiveCellRuns.whileRunning`, so executions
 * alone would tear down mid-write, and `lifetime.cancel()` would take the write with it.
 *
 * ```
 * NotebookExecution(key, kernel, uiState, activeCellRuns, scope) { registry.remove(it.key, it) }
 *   .watchDetached()
 * ```
 */
class NotebookExecution(
  val key: NotebookExecutionKey,
  val kernel: NotebookKernel,
  /** The debug bookkeeping the run's cells armed - carried so a reopened screen steps the stop it left. */
  val debugger: NotebookDebugger,
  /**
   * The flow the kernel keeps writing - exposed as the mutable flow itself so a screen that
   * comes back can adopt it as its own state rather than copying run fields across.
   */
  val uiState: MutableStateFlow<EditorUiState>,
  private val activeCellRuns: ActiveCellRuns,
  /**
   * The scope the teardown's disconnect launches on - `applicationScope`, outliving both the
   * screen that is gone and this holder.
   */
  private val scope: CoroutineScope,
  /** Told once, when the last run ends detached - the registry drops its entry here. */
  private val onDrained: (NotebookExecution) -> Unit,
) {
  /**
   * `scope`'s context with its job swapped for a supervisor of this watch's own - the same
   * arrangement [NotebookKernel.kernelScope] documents: the passed scope in a test is a
   * `runTest` one, and a collector parented to it that only ends on a drain would hold the
   * test open forever.
   */
  private val watchScope = CoroutineScope(scope.coroutineContext + SupervisorJob())
  private var watchJob: Job? = null

  /**
   * Watches for the drain and ends the run's machinery when it lands. Launched rather than
   * collected inline because the holder is built on a scope that may already be mid-teardown;
   * [kotlinx.coroutines.flow.first] is what lets the collect end itself - the flow is state,
   * so it never completes on its own and an open `collect` would leak one coroutine per
   * detached notebook.
   */
  fun watchDetached() {
    watchJob =
      watchScope.launch {
        combine(uiState, activeCellRuns.inFlight) { state, runs -> state.runInFlight to runs }
          .distinctUntilChanged()
          .first { (runInFlight, runs) -> !runInFlight && runs == 0 }
        kernel.close(scope)
        onDrained(this@NotebookExecution)
      }
  }

  /** A screen that took the run over owns the kernel now - the drain watch is no longer its to fire. */
  fun stopWatching() {
    watchJob?.cancel()
    watchJob = null
  }
}
