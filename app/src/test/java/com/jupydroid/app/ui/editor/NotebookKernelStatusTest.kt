package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.domain.model.KernelSessionRegistry
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * How a notebook's [KernelStatus] actually moves, driven through [NotebookKernel].
 *
 * The rule itself is [KernelStatusTest]; this is the wiring - that something sets
 * [EditorUiState.kernelConnected] when a session opens and clears it when one ends, and that a
 * restart says so while it is in flight. Split for the reason [NotebookKernelRestartTest] and
 * [NotebookKernelShutdownTest] are: a pure rule and the object that feeds it are two questions.
 */
class NotebookKernelStatusTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun aFreshlyOpenedNotebookHasNoKernel() = runTest {
    val viewModel = loadedEditor(applicationScope = this)

    assertEquals(KernelStatus.NO_KERNEL, viewModel.uiState.value.kernelStatus)
  }

  /**
   * The state a cold tap is in for as long as the Space is installing. Held open by
   * [SlowToConnectKernelGateway], which is the double that exists for exactly this stretch.
   */
  @Test
  fun aRunWaitingOnASlowKernelIsStarting() = runTest {
    val gateway = SlowToConnectKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(KernelStatus.STARTING, viewModel.uiState.value.kernelStatus)

    gateway.letItConnect()
    advanceUntilIdle()
    assertEquals(KernelStatus.IDLE, viewModel.uiState.value.kernelStatus)
  }

  /**
   * `_pendingKernelName` resolved through the catalogue (`sessioncontext.tsx:584-603`): while the
   * connect is out, `kernelDisplayName` is the kernel *being started*, not `noKernelName` - "Kernel
   * Python 3: Starting" is what the indicator announces then, not "Kernel No Kernel: Starting".
   */
  @Test
  fun aStartingKernelIsNamedForWhatIsComingUp() = runTest {
    val gateway = SlowToConnectKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals("Python 3", viewModel.uiState.value.kernelDisplayName)
  }

  @Test
  fun aFinishedRunLeavesTheKernelIdle() = runTest {
    val viewModel = loadedEditor(applicationScope = this)

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(KernelStatus.IDLE, viewModel.uiState.value.kernelStatus)
  }

  /**
   * `notebook:shutdown-kernel` ends the session, so the notebook is back to having no kernel -
   * the deliberate opposite of leaving the document, which keeps one running to reattach to.
   */
  @Test
  fun shuttingTheKernelDownReturnsToNoKernel() = runTest {
    val viewModel = loadedEditor(applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.kernel.shutdown()
    advanceUntilIdle()

    assertEquals(KernelStatus.NO_KERNEL, viewModel.uiState.value.kernelStatus)
  }

  /**
   * A restart replaces the process and keeps the session, so the notebook still has a kernel when
   * it is over - and is idle rather than busy, because whatever was in flight died with the
   * interpreter (`EditorUiState.abandoningRun`).
   */
  @Test
  fun aRestartEndsIdleRatherThanBusy() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.kernel.restart()
    advanceUntilIdle()

    assertEquals(KernelStatus.IDLE, viewModel.uiState.value.kernelStatus)
  }

  /**
   * **The stale session is reported, not hidden.** P1c stops the app's own server when it is left
   * alone, so a session can be refused; `NotebookKernelSession.reopeningIfStale` drops it and asks
   * for another, and the notebook must not go on claiming a kernel in between. Asserted through
   * the end state, since the refusal and the reconnection are one `advanceUntilIdle` apart.
   */
  @Test
  fun aSessionRefusedByAStoppedServerIsReplacedAndStillReadsIdle() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 1)
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(2, gateway.connectCount)
    assertEquals(KernelStatus.IDLE, viewModel.uiState.value.kernelStatus)
  }

  /**
   * P8f: the Running panel's per-row shutdown lands the *open* notebook on No Kernel, exactly as
   * `session.disposed` does in the reference (`sessioncontext.tsx`'s `noKernelName`) - the
   * document stays open, and only its claim on a kernel goes. The fake pair share one registry
   * here the way `SpaceBackedPorts`'s real pair do.
   */
  @Test
  fun aSessionEndedFromTheRunningPanelLandsOnNoKernel() = runTest {
    val registry = KernelSessionRegistry()
    val kernelSessions = FakeKernelSessions(registry)
    val viewModel = loadedEditor(
      gateway = FakeKernelGateway(registry),
      kernelSessions = kernelSessions,
      applicationScope = this,
    )
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    kernelSessions.shutdown(kernelSessions.running().single().serverSessionId)
    advanceUntilIdle()

    assertEquals(KernelStatus.NO_KERNEL, viewModel.uiState.value.kernelStatus)
  }
}
