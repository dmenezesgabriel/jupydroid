@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.IOException

/**
 * The cell header's Stop square, in the two windows where the ask used to be lost.
 *
 * `startingRun` shows RUNNING and Stop for the whole connect - installing the Space, starting
 * `jupyter_server`, starting the kernel is the slow part of a run - but `whileOpen` has no
 * session to ask until `connect` returns, so a Stop landed in that window used to be dropped:
 * the execute went out anyway and the cell that finished was mislabeled CANCELLED by the
 * interrupting marker the dropped stop left behind.
 *
 * The other window is the interrupt itself being refused: the kernel's `POST .../interrupt`
 * answering 500 or the server being unreachable must not escape `stopRunningCell`'s launch as
 * an uncaught coroutine failure - interrupt is best-effort, and the run's own channel is what
 * reports what the kernel actually did.
 */
class CellStopTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /**
   * The connect window is the whole point: Stop while `connect` is still out must be kept, and
   * once the session resolves the cell's execute is never sent - the cell ends stopped with no
   * output, the way a stop that reached the kernel leaves it.
   */
  @Test
  fun aStopDuringConnect_neverReachesTheKernelAndReportsCancelled() = runTest {
    val gateway = SlowToConnectKernelGateway()
    val editor = loadedEditor(gateway = gateway)

    editor.kernel.runCell(codeCell)
    advanceUntilIdle()
    assertEquals("the run is inside its connect window", "c1", editor.uiState.value.runningCellId)

    editor.kernel.stopRunningCell()
    advanceUntilIdle()

    gateway.letItConnect()
    advanceUntilIdle()

    assertTrue("the cell's code never reached the kernel", gateway.executedSource.isEmpty())
    assertTrue(
      "a cell stopped before its execute has no output",
      editor.uiState.value.outputs["c1"].orEmpty().isEmpty(),
    )
    assertEquals(ExecutionStatus.CANCELLED, editor.uiState.value.statusOf("c1"))
    assertEquals(null, editor.uiState.value.runningCellId)
  }

  /**
   * The interrupt POST refused - the kernel already gone answers 500, an unreachable server
   * refuses the socket - must fold inside the launch rather than crash the process: the run it
   * was meant to stop resolves through its own channel, which is the execute flow's events.
   */
  @Test
  fun aRefusedInterrupt_foldsAndTheRunResolvesOnItsOwnChannel() = runTest {
    val gateway = RecordingKernelGateway(
      runsUntilInterrupted = true,
      interruptFailure = IOException("POST api/kernels/kernel-1/interrupt failed: HTTP 500"),
    )
    val editor = loadedEditor(gateway = gateway)

    editor.kernel.runCell(codeCell)
    advanceUntilIdle()
    assertEquals("the run is held mid-execution", "c1", editor.uiState.value.runningCellId)

    editor.kernel.stopRunningCell()
    advanceUntilIdle()

    assertEquals("the interrupt was asked for and refused", 1, gateway.interruptCount)

    gateway.endHeldRuns()
    advanceUntilIdle()

    assertEquals(ExecutionStatus.CANCELLED, editor.uiState.value.statusOf("c1"))
    assertEquals(null, editor.uiState.value.runningCellId)
  }
}
