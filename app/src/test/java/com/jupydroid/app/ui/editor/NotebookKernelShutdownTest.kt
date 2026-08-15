package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * `notebook:shutdown-kernel`, and what separates it from every other way a notebook lets go.
 *
 * Closing a document calls `disconnect` and leaves the kernel running, which is JupyterLab's
 * default (`kernelShutdown`, `packages/notebook-extension/schema/tracker.json:741-745`, `false`).
 * This is the user asking for the opposite, out loud, from a menu - so the assertions here are all
 * about it being *that* and not the other: the session ends, and nothing later tries to close a
 * socket to a kernel that has gone.
 */
class NotebookKernelShutdownTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun shuttingDownEndsTheSessionRatherThanOnlyClosingTheSocket() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.kernel.shutdown()
    advanceUntilIdle()

    assertEquals(1, gateway.shutDown.size)
    assertTrue("a shutdown is not a disconnect", gateway.disconnected.isEmpty())
  }

  /**
   * No kernel was ever opened, so there is nothing to shut down - and connecting one in order to
   * end it would boot a `jupyter_server` the user did not ask for. The same rule `restart` has.
   */
  @Test
  fun shuttingDownANotebookThatNeverRanOpensNoKernel() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)

    viewModel.kernel.shutdown()
    advanceUntilIdle()

    assertTrue("nothing was running, so nothing to shut down", gateway.shutDown.isEmpty())
  }

  /**
   * The session is forgotten with it, or leaving the screen afterwards would `disconnect` a
   * session the server has already destroyed - a 404 for a socket nobody is holding.
   */
  @Test
  fun aShutDownKernelIsNotDisconnectedAgainOnTheWayOut() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    viewModel.kernel.shutdown()
    advanceUntilIdle()

    viewModel.onCleared()
    advanceUntilIdle()

    assertTrue("the session was already ended, so there is nothing to close", gateway.disconnected.isEmpty())
  }

  /**
   * `notebook:close-and-shutdown` is shutdown *then* close, in that order - the reference awaits
   * the shutdown before disposing the widget (`notebook-extension/src/index.ts:3196-3213`). Here
   * the closing is the caller's `onBack`, so what this asserts is that it is not run early.
   */
  @Test
  fun closingAfterAShutdownWaitsForTheShutdownToFinish() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    var sessionsWhenClosed: Int? = null

    viewModel.kernel.shutdown(then = { sessionsWhenClosed = gateway.shutDown.size })
    advanceUntilIdle()

    assertEquals("the close ran before the shutdown had happened", 1, sessionsWhenClosed)
  }

  /** A notebook with no kernel still closes: the command is Close *and* shut down. */
  @Test
  fun closingStillHappensWhenThereWasNoKernelToShutDown() = runTest {
    val viewModel = loadedEditor(gateway = RecordingKernelGateway(), applicationScope = this)
    var closed = false

    viewModel.kernel.shutdown(then = { closed = true })
    advanceUntilIdle()

    assertTrue("nothing to shut down is not a reason to stay on the screen", closed)
  }
}
