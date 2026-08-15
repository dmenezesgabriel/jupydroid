package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Running cells against the notebook's kernel: one of them, or all of them in order.
 *
 * Split out of [NotebookKernel] the third time detekt counted its functions, and the split is the
 * one the class had grown into: *running cells* keeps a queue, a stop-in-flight and a clock, while
 * what is left next door - restart, shutdown, disconnect, completion - is what you ask the kernel
 * *about itself*. Same call as [NotebookKernelSession] and [NotebookSaveDebounce] before it.
 *
 * ```
 * NotebookCellRuns(uiState, gateway, session, scope, runs, persist = ::persist).runCell(cell)
 * ```
 */
internal class NotebookCellRuns(
  private val uiState: MutableStateFlow<EditorUiState>,
  private val kernelGateway: KernelGateway,
  /** Shared with [NotebookKernel]: which kernel this is, and how it gets another. */
  private val session: NotebookKernelSession,
  private val scope: CoroutineScope,
  /**
   * A run is counted here for as long as it lasts, opening the kernel included - see
   * [ActiveCellRuns] for why the kernel starting has to be part of it.
   */
  private val activeCellRuns: ActiveCellRuns,
  /** Writing belongs to the document half; this only says when there is something to write. */
  private val persist: suspend () -> Unit,
  /** Injected so a test can assert on a run's duration without waiting for one. */
  private val clock: Clock,
) {
  /** Set while an interrupt is in flight, so the stop is reported as cancelled and not as error. */
  private var interrupting: String? = null

  /**
   * Runs one cell and streams its output into the state as it arrives, so a long-running
   * cell shows what it has printed rather than nothing until it finishes.
   */
  fun runCell(cell: Cell) {
    scope.launch { execute(cell) }
  }

  /**
   * Runs every code cell in order, stopping at the first that raises - which is what Jupyter
   * does with a queued run. Carrying on would run later cells against state the failed one
   * never produced, and their output would be wrong rather than merely absent.
   */
  fun runAll() {
    scope.launch { runAllCells() }
  }

  /** Awaitable, which is what a restart-and-run-all needs after the process has been replaced. */
  suspend fun runAllCells() {
    val queue = uiState.value.cells.filter { it.kind == CellKind.CODE }
    // Claimed up front so every waiting cell says so, rather than looking un-run until its turn
    // comes. Anything still queued when one raises becomes SKIPPED.
    uiState.update { it.queueingRun(queue.map(Cell::id)) }
    for (cell in queue) {
      if (!execute(cell)) break
    }
    uiState.update { it.skippingQueued() }
  }

  /**
   * Interrupts the kernel, which is how a running cell is stopped: the cell itself has no
   * handle on the kernel's work. Does nothing when nothing is running, since the kernel may
   * be busy with somebody else's cell.
   */
  fun stopRunningCell() {
    val running = uiState.value.runningCellId ?: return
    // Remembered rather than applied now: the cell is still executing, and the kernel will
    // report a KeyboardInterrupt through the ordinary output path. This is what tells the stop
    // apart from the user's own code raising.
    interrupting = running
    scope.launch { session.opened?.let { kernelGateway.interrupt(it) } }
  }

  /**
   * Returns whether the cell finished without raising, which is what [runAllCells] gates on.
   *
   * The whole of it counts as a run in flight, **opening the kernel included**: on a cold app that
   * is the Space installing, the server starting and the kernel starting, and P1c's backstop must
   * not read "nothing is running" through the slowest part of a run
   * (`evidence/p1c-app-death-backstop/`).
   */
  private suspend fun execute(cell: Cell): Boolean = activeCellRuns.whileRunning { run(cell) }

  private suspend fun run(cell: Cell): Boolean {
    uiState.update { it.startingRun(cell.id, at = now()) }
    eventsFor(cell)
      .catch { failure -> emit(kernelFailure(cell.id, failure)) }
      .collect { event -> uiState.update { state -> state.after(event) } }

    val raised = uiState.value.outputs[cell.id].orEmpty().any { it is CellOutput.Error }
    val cancelled = interrupting == cell.id
    interrupting = null
    uiState.update {
      if (cancelled) it.cancellingRun(cell.id, at = now()) else it.finishedRun(cell.id, at = now(), failed = raised)
    }
    persist()
    // A cancelled cell stops the queue too: the user asked for the run to stop, not for the
    // next cell to start.
    return !raised && !cancelled
  }

  /** The cell's events, on a session [NotebookKernelSession] re-opens if its server has gone. */
  private fun eventsFor(cell: Cell): Flow<KernelEvent> = flow {
    emitAll(session.reopeningIfStale { open -> kernelGateway.execute(open, cell.id, cell.source) })
  }

  private fun now(): Instant = clock.instant()
}
