package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What the notebook says its kernel is doing - the rule behind [KernelStatus], on its own.
 *
 * Pure, so every branch is reachable without a coroutine or a gateway; the transitions that
 * *produce* these states are [NotebookKernelStatusTest], next door. The same split
 * `CellExecutions.kt` and [NotebookKernelRestartTest] already have.
 */
class KernelStatusTest {
  /**
   * **A notebook nobody has run has no kernel**, which is this app's lazy start rather than the
   * reference's: JupyterLab opens a session with the document, so its resting state is `idle`.
   * Ours does not boot a Python runtime for a document being read.
   */
  @Test
  fun aNotebookWithNoSessionHasNoKernel() {
    assertEquals(KernelStatus.NO_KERNEL, EditorUiState().kernelStatus)
  }

  /**
   * The fourteen seconds. A run is in flight - the cell says RUNNING from the tap - but the socket
   * is not open yet, because opening it is what installs the Space and starts the server
   * (`evidence/p1c-app-death-backstop/`).
   */
  @Test
  fun aRunWithNoSocketYetIsStarting() {
    val state = EditorUiState().startingRun(codeCell.id, at = SOME_TIME)

    assertEquals(KernelStatus.STARTING, state.kernelStatus)
  }

  @Test
  fun aRunOnAnOpenSocketIsBusy() {
    val state = EditorUiState(kernelConnected = true).startingRun(codeCell.id, at = SOME_TIME)

    assertEquals(KernelStatus.BUSY, state.kernelStatus)
  }

  /**
   * **Queued counts as busy too**, which is `ExecutionIndicator`'s own reading: it reports busy
   * from `scheduledCell.size` rather than from whichever cell the kernel has in hand
   * (`packages/notebook/src/executionindicator.tsx:153`). A Run All whose first cell has just
   * finished is not idle for the gap before the second starts.
   */
  @Test
  fun aQueuedCellIsBusyEvenWithNothingRunningYet() {
    val state = EditorUiState(kernelConnected = true).queueingRun(listOf(codeCell.id))

    assertEquals(KernelStatus.BUSY, state.kernelStatus)
  }

  @Test
  fun anOpenSocketWithNothingToDoIsIdle() {
    val state = EditorUiState(kernelConnected = true)
      .startingRun(codeCell.id, at = SOME_TIME)
      .finishedRun(codeCell.id, at = SOME_TIME, failed = false)

    assertEquals(KernelStatus.IDLE, state.kernelStatus)
  }

  /**
   * **Restarting outranks the run it is abandoning.** `restart-run-all` replaces the interpreter
   * with cells still marked RUNNING (`NotebookKernel.restart` clears them only once the gateway has
   * answered), so a rule that read the executions first would call a restarting kernel busy.
   */
  @Test
  fun aRestartOutranksWhateverWasRunning() {
    val state = EditorUiState(kernelConnected = true, kernelRestarting = true)
      .startingRun(codeCell.id, at = SOME_TIME)

    assertEquals(KernelStatus.RESTARTING, state.kernelStatus)
  }

  private companion object {
    val SOME_TIME: java.time.Instant = java.time.Instant.parse("2026-08-25T10:00:00Z")
  }
}
