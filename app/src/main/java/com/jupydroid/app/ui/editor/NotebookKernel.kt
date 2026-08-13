package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import java.time.Clock
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The notebook's kernel, and everything the notebook asks it: run a cell, interrupt, complete.
 *
 * Split out of `EditorViewModel` because those are one responsibility and the document - load,
 * edit, rename, reshape, save - is a different one. detekt said so first (`TooManyFunctions` at
 * 11 of 11), and the answer to that is to find the object that wants to exist rather than raise
 * the threshold; it is the same call that produced [NotebookSaveDebounce].
 *
 * It owns the [KernelSession], which is why completion lives here despite not being execution:
 * the session is the thing all three need, and splitting on "is it a run" would have put the
 * session in one object and a user of it in another.
 *
 * ```
 * val kernel = NotebookKernel(uiState, kernelGateway, viewModelScope, persist = ::persist)
 * kernel.runCell(cell)
 * kernel.close(applicationScope)   // on the way out
 * ```
 */
class NotebookKernel(
  private val uiState: MutableStateFlow<EditorUiState>,
  private val kernelGateway: KernelGateway,
  private val scope: CoroutineScope,
  /** Writing belongs to the document half; this only says when there is something to write. */
  private val persist: suspend () -> Unit,
  /** Injected so a test can assert on a run's duration without waiting for one. */
  private val clock: Clock = Clock.systemUTC(),
) {
  private var session: KernelSession? = null

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

  private suspend fun runAllCells() {
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
    scope.launch { session?.let { kernelGateway.interrupt(it) } }
  }

  /**
   * Restarts the kernel: a fresh interpreter, and every variable gone.
   *
   * **The caller is expected to have asked first.** This is irreversible and silent - nothing on
   * screen changes except that the notebook's state is no longer real - which is why JupyterLab
   * puts it behind a warn dialog reading *"All variables will be lost"*
   * (`packages/apputils/src/sessioncontext.tsx:1505-1520`).
   *
   * Does nothing when no kernel has been opened: there is no process to replace, and connecting
   * one only to restart it would boot a runtime the user did not ask for.
   */
  fun restart(andRunAll: Boolean = false) {
    val open = session ?: return
    scope.launch {
      kernelGateway.restart(open)
      // Whatever was in flight died with the process; anything finished is history and stays.
      uiState.update { it.abandoningRun() }
      if (andRunAll) runAllCells()
    }
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

  /**
   * Closes the socket, on a scope that outlives the screen.
   *
   * **Not a shutdown**, and that is deliberate: `SessionContext.dispose()` disposes the client
   * connection and shuts the kernel down only when `shutdownOnDispose` is set, which defaults to
   * false (`packages/apputils/src/sessioncontext.tsx:679-709`).
   */
  fun close(on: CoroutineScope) {
    val open = session ?: return
    session = null
    on.launch { kernelGateway.disconnect(open) }
  }

  /** Returns whether the cell finished without raising, which is what [runAll] gates on. */
  private suspend fun execute(cell: Cell): Boolean {
    uiState.update { it.startingRun(cell.id, at = now()) }
    kernelGateway.execute(openSession(), cell.id, cell.source)
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

  private fun now(): Instant = clock.instant()

  /**
   * The notebook's own path goes to the kernel, because that is what gives the kernel a working
   * directory - so a cell writing `data.csv` writes it beside the notebook, which is the whole
   * point of the workspace being the kernel's `HOME` (X1a).
   *
   * Read from the state rather than captured at construction: since X1b the id *is* the path and
   * a rename moves it. The kernel keeps the directory it started in either way - a process's cwd
   * is fixed once it is running - which is also what happens in the reference, where a rename
   * retitles the session and leaves the running kernel where it was.
   */
  private suspend fun openSession(): KernelSession =
    session ?: kernelGateway.connect(BASE_SPACE, uiState.value.notebookId).also { session = it }

  private companion object {
    /** Step 17 introduces real Spaces; until then every notebook runs in the base one. */
    val BASE_SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
