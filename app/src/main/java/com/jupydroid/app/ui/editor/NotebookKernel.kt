package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.SteppingCommand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * The notebook's handle on its kernel: run a cell, restart it, end it, ask it what it knows.
 *
 * Split out of `EditorViewModel` because those are one responsibility and the document - load,
 * edit, rename, reshape, save - is a different one. detekt said so first (`TooManyFunctions` at
 * 11 of 11), and the answer to that is to find the object that wants to exist rather than raise
 * the threshold; it is the same call that produced [DocumentAutosave].
 *
 * It said so again when [shutdown] arrived, and the seam the second time was *running cells* -
 * a queue, a stop in flight and a clock, none of which the rest of this needs. That is
 * [NotebookCellRuns], next door; what stays here is the session and what is done **to** the
 * kernel. The three run methods are kept on this class rather than exposing the collaborator,
 * because "the notebook's kernel" is the one handle every screen and test already holds.
 *
 * It owns the [NotebookKernelSession], which is why completion lives here despite not being
 * execution: the session is the thing all of it needs, and splitting on "is it a run" alone would
 * have put the session in one object and a user of it in another.
 *
 * ```
 * val kernel = NotebookKernel(uiState, kernelGateway, viewModelScope, runs, checkpoint, debugger = debugger)
 * kernel.runCell(cell)
 * kernel.close(applicationScope)   // on the way out
 * ```
 */
class NotebookKernel(
  private val uiState: MutableStateFlow<EditorUiState>,
  private val kernelGateway: KernelGateway,
  private val scope: CoroutineScope,
  /**
   * A run is counted here for as long as it lasts, opening the kernel included - see
   * [ActiveCellRuns] for why the kernel starting has to be part of it.
   */
  private val activeCellRuns: ActiveCellRuns,
  /**
   * What a finished run does to the document - write it, or mark it owed - decided by the
   * `autosave` setting on the document half; this only says a run has something to commit.
   */
  private val checkpoint: suspend () -> Unit,
  /** Injected so a test can assert on a run's duration without waiting for one. */
  private val clock: Clock = Clock.systemUTC(),
  /** Arms a cell's breakpoints before it runs, and reports where the kernel stops; see [NotebookCellRuns]. */
  private val debugger: NotebookDebugger,
) {
  /** Which kernel this notebook is on, and how it gets another when that one's server stops. */
  private val session =
    NotebookKernelSession(
      kernelGateway,
      BASE_SPACE,
      onConnectedChanged = { connected -> uiState.update { it.copy(kernelConnected = connected) } },
    ) { uiState.value.notebookId }

  private val runs =
    NotebookCellRuns(uiState, kernelGateway, session, scope, activeCellRuns, checkpoint, clock, debugger)

  /**
   * `notebook:access-previous-history-entry` / `access-next-history-entry` - what the kernel has
   * run, walked from the cell being edited. See [NotebookHistory].
   *
   * **Exposed where [runs] is hidden**, and the difference is what each is: the three run methods
   * are the same idea as the session this class owns, so they read as its own. A walk through the
   * kernel's history has a cursor, a batch size and a rule about what ends it, none of which the
   * rest of the kernel wants - and three more methods here is a class about two things.
   */
  val history = NotebookHistory(session, kernelGateway, scope)

  /**
   * A Console's own session, pre-attached to whatever kernel this notebook already has - P6b-9.
   *
   * `console:inject`'s own contract (`console-extension/src/index.ts:1294-1338`): find the session
   * already tracked for this path and hand it over, never call `connect` a second time. A fresh
   * [NotebookKernelSession] rather than [session] itself, because a Console lives in its own
   * `ViewModelStore` entry - [attachedTo] is what lets its own copy skip the connect P6b-1 built it
   * to skip, without the two screens sharing one mutable object across two independent lifecycles.
   * `null` when this notebook has never opened a kernel of its own yet - the returned session then
   * behaves exactly like a fresh one, and opens on whichever of the two runs something first.
   */
  fun attachConsole(): NotebookKernelSession =
    NotebookKernelSession(kernelGateway, BASE_SPACE, attachedTo = session.opened) { uiState.value.notebookId }

  /** @see NotebookCellRuns.runCell */
  fun runCell(cell: Cell) = runs.runCell(cell)

  /**
   * Runs a range of the notebook - `run-all-cells`, `run-all-above`, `run-all-below` and the
   * marked run, which differ only in which cells they name.
   *
   * @see NotebookCellRuns.run
   */
  fun run(range: RunRange) = runs.run(range)

  /** @see NotebookCellRuns.stopRunningCell */
  fun stopRunningCell() = runs.stopRunningCell()

  /** @see NotebookCellRuns.step */
  fun step(command: SteppingCommand) = runs.step(command)

  /**
   * Restarts the kernel: a fresh interpreter, and every variable gone.
   *
   * **The caller is expected to have asked first.** This is irreversible and silent - nothing on
   * screen changes except that the notebook's state is no longer real - which is why JupyterLab
   * puts it behind a warn dialog reading *"All variables will be lost"*
   * (`packages/apputils/src/sessioncontext.tsx:1505-1520`).
   *
   * Does nothing **to the kernel** when none has been opened: there is no process to replace, and
   * connecting one only to restart it would boot a runtime the user did not ask for.
   *
   * [then] is what the three compound commands do afterwards - `restart-run-all`,
   * `restart-clear-output` and `restart-and-run-to-selected` are each an awaited restart followed
   * by one more thing (`packages/notebook-extension/src/index.ts:3245-3345`), which is why this
   * takes a continuation rather than growing a flag per command.
   *
   * It runs **after** the kernel has been replaced, and for a run that is the difference between a
   * result and a lie: cells executed first would answer from the interpreter the restart is about
   * to destroy. And it runs even when there was no kernel, exactly as [shutdown]'s does - with
   * nothing to replace there is nothing to wait for, and the second half of a command is not
   * conditional on the first having had work to do.
   */
  fun restart(then: suspend () -> Unit = {}) {
    scope.launch {
      session.opened?.let { open ->
        // Announced for the length of the call rather than after it: replacing an interpreter is
        // the one kernel operation with nothing else on screen to say it is happening, and on a
        // device it is not instant. Only when there *is* a process to replace, which is the same
        // guard the restart itself carries.
        uiState.update { state -> state.copy(kernelRestarting = true) }
        kernelGateway.restart(open)
        // Whatever was in flight died with the process; anything finished is history and stays.
        uiState.update { state -> state.abandoningRun().copy(kernelRestarting = false) }
      }
      then()
    }
  }

  /**
   * `notebook:restart-run-all` - a restart, then every cell.
   *
   * Through [NotebookCellRuns.runCells] rather than [run] for the reason [restartAndRunTo] gives:
   * the reference composes this one out of the public `NotebookActions.runCells` (`index.ts:3315`),
   * which leaves the selection alone, and not out of `runAll`, which deselects.
   */
  fun restartAndRunAll() {
    val cells = uiState.value.cells
    restart(then = { runs.runCells(cells) })
  }

  /**
   * `notebook:restart-and-run-to-selected` - a restart, then the head and everything above it.
   *
   * Its range is [RunRange.UP_TO_SELECTED] and its runner is [NotebookCellRuns.runCells] rather
   * than [run], which is not an implementation detail: the reference hands its slice to the
   * **public** `NotebookActions.runCells` (`index.ts:3283`), and that one touches no selection - so
   * unlike the four `run*` commands this leaves the head where it is.
   *
   * The cells are claimed before the restart rather than inside it, so the command runs what was on
   * screen when it was asked for.
   */
  fun restartAndRunTo() {
    val cells = uiState.value.cellsToRun(RunRange.UP_TO_SELECTED)
    restart(then = { runs.runCells(cells) })
  }

  /**
   * Ends the session: the kernel process stops and every variable goes with it.
   *
   * **`notebook:shutdown-kernel`, and the deliberate opposite of [close].** Leaving a notebook
   * closes the socket and leaves the kernel running, so reopening reattaches to it; this is the
   * user saying that is not what they want. The reference guards it with nothing at all - it is
   * the *close* variant that asks first (`notebook:close-and-shutdown`,
   * `packages/notebook-extension/src/index.ts:3189-3213`) - so the dialog lives with that command
   * and not here.
   *
   * [then] is what `close-and-shutdown` does afterwards, and it runs **after** the session has
   * ended rather than beside it: the reference awaits its shutdown before disposing the widget.
   * It runs even when there was no kernel, because the command is Close *and* shut down and the
   * closing half is not conditional.
   *
   * Does nothing to the kernel when none was opened - connecting one in order to end it would
   * boot a runtime the user never asked for, exactly as [restart] refuses to.
   */
  fun shutdown(then: () -> Unit = {}) {
    val open = session.forget()
    scope.launch {
      open?.let { kernelGateway.shutdown(it) }
      // Whatever was in flight died with the process, and nothing that has finished is a lie.
      uiState.update { it.abandoningRun() }
      then()
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
    val open = session.opened ?: return CompletionResult.NONE
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
    val open = session.forget() ?: return
    on.launch { kernelGateway.disconnect(open) }
  }

  private companion object {
    /** Step 17 introduces real Spaces; until then every notebook runs in the base one. */
    val BASE_SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
