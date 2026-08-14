package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `kernel_info_request` first, then the cell - which is what a Jupyter client is supposed to do.
 *
 * Read out of the reference rather than reasoned about
 * (`reference/research_repos/jupyterlab/packages/services/src/kernel/default.ts`,
 * `_updateConnectionStatus`), where the comment is about our exact symptom:
 *
 * > Send a kernel info request to make sure we send at least one message to get kernel status
 * > back. Always request kernel info first, to get kernel status back **and ensure iopub is fully
 * > established**.
 * >
 * > if sent while zmq subscriptions are not established, kernelInfo may not resolve, so use a
 * > timeout to ensure we don't hang forever.
 *
 * So a websocket that has completed its HTTP handshake is *not* a kernel ready to be asked
 * anything: `jupyter_server` is still subscribing the session to the kernel's ZMQ channels, and an
 * `execute_request` arriving in that window is dropped without a word. That is the stall, seen
 * five times - the kernel sends its startup statuses and never `execute_input`.
 *
 * JupyterLab queues every user message until connected, sends `kernel_info_request` on connect,
 * and releases the queue when the reply arrives or after a 3 s failsafe.
 *
 * The first two parts are asserted here. **The third is where this app departs**, and its tests
 * live in [KernelGreetingRecoveryTest]: releasing the queue into a channel that answered nothing
 * is what the stall *is*, so an unanswered greeting restarts the kernel instead, and fails the
 * cell if the new one is no better.
 */
class KernelInfoHandshakeTest {
  @Test
  fun theFirstThingSentIsAKernelInfoRequest() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) })
    val gateway = RealKernelGateway(opener, FakeKernelLifecycle())
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.execute(session, "cell-1", "2 + 2").toList()

    assertEquals(listOf("kernel_info_request", "execute_request"), opener.sentMsgTypes)
  }

  @Test
  fun theCellStillRuns() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) })
    val gateway = RealKernelGateway(opener, FakeKernelLifecycle())
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    val events = gateway.execute(session, "cell-1", "2 + 2").toList()

    assertTrue("the cell did not finish: $events", events.contains(KernelEvent.Idle))
  }

  /**
   * The handshake is per socket, not per cell: a second cell must not re-ask.
   *
   * JupyterLab does it once per connection, in `_updateConnectionStatus`, not once per request.
   */
  @Test
  fun aSecondCellDoesNotAskAgain() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) })
    val gateway = RealKernelGateway(opener, FakeKernelLifecycle())
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.execute(session, "cell-1", "2 + 2").toList()
    gateway.execute(session, "cell-2", "3 + 3").toList()

    assertEquals(1, opener.sentMsgTypes.count { it == "kernel_info_request" })
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
