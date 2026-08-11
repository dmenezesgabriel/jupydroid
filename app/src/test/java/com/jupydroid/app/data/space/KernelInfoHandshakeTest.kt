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
 * and releases the queue when the reply arrives **or after a 3 s failsafe**. All three parts are
 * load-bearing and all three are asserted here.
 */
class KernelInfoHandshakeTest {
  @Test
  fun theFirstThingSentIsAKernelInfoRequest() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) })
    val gateway = RealKernelGateway(opener, FakeKernelLifecycle())
    val session = gateway.connect(SPACE)

    gateway.execute(session, "cell-1", "2 + 2").toList()

    assertEquals(listOf("kernel_info_request", "execute_request"), opener.sentMsgTypes)
  }

  @Test
  fun theCellStillRuns() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) })
    val gateway = RealKernelGateway(opener, FakeKernelLifecycle())
    val session = gateway.connect(SPACE)

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
    val session = gateway.connect(SPACE)

    gateway.execute(session, "cell-1", "2 + 2").toList()
    gateway.execute(session, "cell-2", "3 + 3").toList()

    assertEquals(1, opener.sentMsgTypes.count { it == "kernel_info_request" })
  }

  /**
   * The failsafe the reference flags in a standing note: a kernel that never answers
   * `kernel_info_request` must not hold the cell for ever.
   *
   * Without it, this change would turn a dropped request into a permanent hang - trading a stall
   * that recovers on retry for one that does not.
   */
  @Test
  fun aKernelThatNeverAnswersStillGetsTheCellAfterTheFailsafe() = runTest {
    val opener = ScriptedKernelSocketOpener(
      reply = { KernelFrames.successfulExecution(it) },
      answerKernelInfo = false,
    )
    val gateway = RealKernelGateway(opener, FakeKernelLifecycle())
    val session = gateway.connect(SPACE)

    val events = gateway.execute(session, "cell-1", "2 + 2").toList()

    assertTrue("the failsafe never released the cell: $events", events.contains(KernelEvent.Idle))
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
