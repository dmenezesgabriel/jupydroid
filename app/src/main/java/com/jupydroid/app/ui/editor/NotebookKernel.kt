package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
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
) {
  private var session: KernelSession? = null

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
    scope.launch {
      for (cell in uiState.value.cells.filter { it.kind == CellKind.CODE }) {
        if (!execute(cell)) return@launch
      }
    }
  }

  /**
   * Interrupts the kernel, which is how a running cell is stopped: the cell itself has no
   * handle on the kernel's work. Does nothing when nothing is running, since the kernel may
   * be busy with somebody else's cell.
   */
  fun stopRunningCell() {
    if (uiState.value.runningCellId == null) return
    scope.launch { session?.let { kernelGateway.interrupt(it) } }
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
    uiState.update { it.startingRun(cell.id) }
    kernelGateway.execute(openSession(), cell.id, cell.source)
      .catch { failure -> emit(kernelFailure(cell.id, failure)) }
      .collect { event -> uiState.update { state -> state.after(event) } }
    uiState.update { it.copy(runningCellId = null) }
    persist()
    return uiState.value.outputs[cell.id].orEmpty().none { it is CellOutput.Error }
  }

  private suspend fun openSession(): KernelSession =
    session ?: kernelGateway.connect(BASE_SPACE).also { session = it }

  private companion object {
    /** Step 17 introduces real Spaces; until then every notebook runs in the base one. */
    val BASE_SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
