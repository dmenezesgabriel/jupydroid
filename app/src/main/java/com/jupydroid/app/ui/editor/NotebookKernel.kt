package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelGateway
import java.time.Clock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * The notebook's handle on its kernel: run a cell, restart it, end it, ask it what it knows.
 *
 * Split out of `EditorViewModel` because those are one responsibility and the document - load,
 * edit, rename, reshape, save - is a different one. detekt said so first (`TooManyFunctions` at
 * 11 of 11), and the answer to that is to find the object that wants to exist rather than raise
 * the threshold; it is the same call that produced [NotebookSaveDebounce].
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
 * val kernel = NotebookKernel(uiState, kernelGateway, viewModelScope, runs, persist = ::persist)
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
  /** Writing belongs to the document half; this only says when there is something to write. */
  private val persist: suspend () -> Unit,
  /** Injected so a test can assert on a run's duration without waiting for one. */
  private val clock: Clock = Clock.systemUTC(),
) {
  /** Which kernel this notebook is on, and how it gets another when that one's server stops. */
  private val session = NotebookKernelSession(kernelGateway, BASE_SPACE) { uiState.value.notebookId }

  private val runs =
    NotebookCellRuns(uiState, kernelGateway, session, scope, activeCellRuns, persist, clock)

  /** @see NotebookCellRuns.runCell */
  fun runCell(cell: Cell) = runs.runCell(cell)

  /** @see NotebookCellRuns.run */
  fun runAll() = runs.run(RunRange.ALL)

  /** @see NotebookCellRuns.stopRunningCell */
  fun stopRunningCell() = runs.stopRunningCell()

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
    val open = session.opened ?: return
    scope.launch {
      kernelGateway.restart(open)
      // Whatever was in flight died with the process; anything finished is history and stays.
      uiState.update { it.abandoningRun() }
      if (andRunAll) runs.runCells(uiState.value.cells)
    }
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
