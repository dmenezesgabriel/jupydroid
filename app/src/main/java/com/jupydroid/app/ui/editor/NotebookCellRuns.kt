package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelPickRequiredException
import com.jupydroid.app.domain.port.StaleKernelSessionException
import com.jupydroid.app.domain.port.SteppingCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock
import java.time.Instant

/**
 * Running cells against the notebook's kernel: one of them, or all of them in order.
 *
 * Split out of [NotebookKernel] the third time detekt counted its functions, and the split is the
 * one the class had grown into: *running cells* keeps a queue, a stop-in-flight and a clock, while
 * what is left next door - restart, shutdown, disconnect, completion - is what you ask the kernel
 * *about itself*. Same call as [NotebookKernelSession] and [DocumentAutosave] before it.
 *
 * ```
 * NotebookCellRuns(uiState, gateway, session, scope, runs, persist = ::persist, clock, debugger).runCell(cell)
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
  /**
   * What a finished run does to the document - write it back, or mark it owed under
   * `autosave: false`; this only says a run has something to commit.
   */
  private val checkpoint: suspend () -> Unit,
  /** Injected so a test can assert on a run's duration without waiting for one. */
  private val clock: Clock,
  /** Arms a cell's breakpoints before it runs, and reports where the kernel stops. */
  private val debugger: NotebookDebugger,
) {
  /** Set while an interrupt is in flight, so the stop is reported as cancelled and not as error. */
  private var interrupting: String? = null

  /**
   * Runs one cell - the cell header's own Run button.
   *
   * A run of one through [runCells] rather than a path of its own, so a markdown cell renders here
   * exactly as it does inside a Run All and there is one place that decides what running each kind
   * of cell means. A code cell still streams its output into the state as it arrives, so a
   * long-running one shows what it has printed rather than nothing until it finishes.
   */
  fun runCell(cell: Cell) {
    scope.launch { runCells(listOf(cell)) }
  }

  /**
   * Runs a range of the notebook in order, and lets go of the selection as it starts - which is
   * what the four `run*` commands do and what [withRunStarted] carries.
   *
   * **The cells are claimed before the selection moves, not inside the coroutine.**
   * [RunRange.MARKED] is derived from the selection, so deselecting first would leave nothing to
   * run - and reading the range later would also mean running whatever the notebook looked like by
   * then rather than what the user pointed at.
   *
   * ```
   * runs.run(RunRange.ABOVE)   // everything before the head, and not the head
   * ```
   */
  fun run(range: RunRange) {
    val cells = uiState.value.cellsToRun(range)
    uiState.update { it.withRunStarted(range) }
    scope.launch { runCells(cells) }
  }

  /**
   * Runs the given cells in order, stopping at the first that raises - which is what Jupyter
   * does with a queued run. Carrying on would run later cells against state the failed one
   * never produced, and their output would be wrong rather than merely absent.
   *
   * Suspending rather than launching, which is what a restart-and-run-all needs after the process
   * has been replaced.
   *
   * **Markdown is answered here rather than by the caller.** `Private.runCells` is handed every
   * widget and decides per cell what running one means (`packages/notebook/src/cellexecutor.ts`),
   * so a range stays a fact about the document and this is the one place that knows what each kind
   * of cell does when it is run.
   */
  suspend fun runCells(cells: List<Cell>) {
    renderMarkdownIn(cells)
    val queue = cells.filter { it.kind == CellKind.CODE }
    // Claimed up front so every waiting cell says so, rather than looking un-run until its turn
    // comes. Anything still queued when one raises becomes SKIPPED.
    uiState.update { it.queueingRun(queue.map(Cell::id)) }
    for (cell in queue) {
      if (!execute(cell)) break
    }
    uiState.update { it.skippingQueued() }
  }

  /**
   * Running a markdown cell renders it - the whole of what `cellexecutor.ts` does for that kind.
   *
   * Up front rather than in turn, because there is nothing to wait for: the reference launches
   * every cell of a run together and only the code ones ever suspend.
   *
   * Saved only when the document actually changed, which is the fold coming off a hidden source -
   * `withMarkdownRendered` returns the same state otherwise, so a Run All over untouched markdown
   * does not rewrite the file for having been looked at.
   */
  private suspend fun renderMarkdownIn(cells: List<Cell>) {
    val rendering = cells.filter { it.kind == CellKind.MARKDOWN }.map { it.id }
    if (rendering.isEmpty()) return
    val before = uiState.value.cells
    uiState.update { it.withMarkdownRendered(rendering) }
    if (uiState.value.cells != before) checkpoint()
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
    scope.launch {
      session.opened?.let { open ->
        try {
          kernelGateway.interrupt(open)
        } catch (_: StaleKernelSessionException) {
          // The kernel the interrupt named died with its server - nothing to interrupt, but the
          // dead session must not stay claimed: the landing `session.disposed` already owns.
          session.endIfMatches(open.serverSessionId)
        }
      }
    }
  }

  /** Sends a step/continue command for whatever the debugger is currently stopped at. */
  fun step(command: SteppingCommand) {
    scope.launch { debugger.step(command) }
  }

  /**
   * Returns whether the cell finished without raising, which is what [runAllCells] gates on.
   *
   * The whole of it counts as a run in flight, **opening the kernel included**: on a cold app that
   * is the Space installing, the server starting and the kernel starting, and the idle backstop
   * must not read "nothing is running" through the slowest part of a run.
   */
  private suspend fun execute(cell: Cell): Boolean = activeCellRuns.whileRunning { run(cell) }

  private suspend fun run(cell: Cell): Boolean {
    uiState.update { it.startingRun(cell.id, at = now()) }
    try {
      eventsFor(cell).collect { event -> uiState.update { state -> state.after(event) } }
    } catch (_: KernelPickRequiredException) {
      uiState.update { it.withoutExecution(cell.id).copy(kernelPickRequired = it.kernelPickRequired + 1) }
      return false
    }

    val raised = uiState.value.outputs[cell.id].orEmpty().any { it is CellOutput.Error }
    val cancelled = interrupting == cell.id
    interrupting = null
    uiState.update {
      if (cancelled) it.cancellingRun(cell.id, at = now()) else it.finishedRun(cell.id, at = now(), failed = raised)
    }
    checkpoint()
    // A cancelled cell stops the queue too: the user asked for the run to stop, not for the
    // next cell to start.
    return !raised && !cancelled
  }

  /**
   * The cell's events, on a session [NotebookKernelSession] re-opens if its server has gone.
   *
   * Breakpoints are armed **before** the cell executes - DAP requires a debug session attached
   * ahead of `execute_request`, which is why [NotebookDebugger.startSession] runs first, inside
   * the same [NotebookKernelSession.reopeningIfStale] block that resolves the session [KernelGateway.execute]
   * itself uses. The debug-event watcher started beside it follows that same session for exactly
   * as long as the execute flow is being collected - [Flow.onCompletion] is what cancels it the
   * instant that stops being true, whether the cell finished or the run was interrupted.
   *
   * **The watcher is launched only when [NotebookDebugger.startSession] actually attached.** A
   * cell with no breakpoints returns `false` there and has no debug session for
   * [NotebookDebugger.watchForStop] to watch - `BaseSpaceKernelDebugger.events` throws
   * `StaleKernelSessionException` for a session that was never attached, uncaught inside the
   * coroutine [scope] launches, which crashed the app on every ordinary run before this guard
   * (found on a tablet, in a Maestro run).
   */
  private fun eventsFor(cell: Cell): Flow<KernelEvent> = flow {
    emitAll(
      session.reopeningIfStale { open ->
        val attached = debugger.startSession(open, cell)
        val watcher = if (attached) scope.launch { debugger.watchForStop(open, cell) } else null
        kernelGateway.execute(open, cell.id, cell.source).onCompletion { watcher?.cancel() }
      },
    )
    // A kernel that cannot be picked is not a cell failure - it is the picker's cue, so it
    // passes the failure lane by rather than landing as an error output the cell never earned.
  }.catch { failure ->
    if (failure is KernelPickRequiredException) throw failure else emit(kernelFailure(cell.id, failure))
  }

  private fun now(): Instant = clock.instant()
}
