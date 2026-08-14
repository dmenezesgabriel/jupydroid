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
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    val events = gateway.execute(session, "cell-1", "2 + 2").toList()

    assertTrue("the failsafe never released the cell: $events", events.contains(KernelEvent.Idle))
  }

  /**
   * A greeting that goes unanswered is asked **once**, and then the failsafe releases the cell.
   *
   * This pins the reference's shape against the obvious-looking change that does not work.
   * Retrying `kernel_info_request` on `jupyter_server`'s own 0.5 s nudge cadence was tried, put on
   * a tablet, and measured: **2 stalls in 80 runs**, and both trails show the retry asking 120
   * times over 60 s while not one `kernel_info_reply` ever came back
   * (`evidence/kernel-observability/seventh-sighting/`). Asking a dead shell channel again is
   * still asking a dead shell channel.
   *
   * So the client stays where the reference is: `_updateConnectionStatus` calls
   * `requestKernelInfo()` once and arms a `DEFAULT_KERNEL_INFO_TIMEOUT` of 3000 ms
   * (`packages/services/src/kernel/default.ts:1553-1595`). JupyterLab's FIXME does muse that a
   * retry "may be preferable"; the measurement says it is not the fix, and a 60 s timeout only
   * buys a cell 60 s in Queued before the request it will lose anyway.
   */
  @Test
  fun anUnansweredGreetingIsAskedOnce() = runTest {
    val opener = ScriptedKernelSocketOpener(
      reply = { KernelFrames.successfulExecution(it) },
      answerKernelInfo = false,
    )
    val gateway = RealKernelGateway(opener, FakeKernelLifecycle())
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.execute(session, "cell-1", "2 + 2").toList()

    assertEquals(1, opener.sentMsgTypes.count { it == "kernel_info_request" })
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
