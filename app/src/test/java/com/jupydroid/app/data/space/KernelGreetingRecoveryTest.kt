package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * A greeting that goes unanswered means the shell channel is dead, so replace the kernel.
 *
 * Seven sightings of the execution stall, every one of them passing on the next launch with a
 * fresh kernel, and none reproducible on demand (`docs/PLAN.md` P0). What the sightings agree on
 * is the shape: the socket connects, iopub delivers, and the shell channel answers nothing - not
 * the app's `kernel_info_request`, and not 120 asks over 60 s.
 *
 * So this is recovery rather than diagnosis, and it does not wait on the cause being found.
 *
 * **Restarting rather than retrying, and why that is not the obvious answer twice over.**
 * JupyterLab gives up after one timeout and sends the pending messages anyway, with a standing
 * FIXME saying a retry "may be preferable" (`packages/services/src/kernel/default.ts:1589-1591`).
 * The retry was tried here and measured: 2 stalls in 80 tablet runs, both trails showing 120 asks
 * and not one reply (`evidence/kernel-observability/seventh-sighting/`). Asking a dead channel
 * again is still asking a dead channel. Replacing the process behind it is a different act.
 *
 * **The socket is deliberately not reopened.** JupyterLab's `restart()` follows the REST call with
 * `reconnect()` "to address cases where kernel ports have changed during the restart"
 * (`default.ts:547-563`), but `jupyter_server` restarts with `newports=False`
 * (`services/kernels/handlers.py` calls `km.restart_kernel(kernel_id)`; the default is documented
 * in `jupyter_client/manager.py`), and its `on_kernel_restarted` only publishes a status - it does
 * not rebuild the streams. So the endpoints are the same ones and this connection is still the
 * right one.
 */
class KernelGreetingRecoveryTest {
  @Test
  fun aGreetingThatIsNeverAnsweredRestartsTheKernel() = runTest {
    val lifecycle = FakeKernelLifecycle()
    val gateway = RealKernelGateway(KernelSockets(neverGreets(), lifecycle))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    runCatching { gateway.execute(session, "cell-1", "2 + 2").toList() }

    assertEquals(listOf(session.kernelId), lifecycle.restarted)
  }

  /**
   * Bounded, because the failure mode being fixed is a wait with no end. A loop of restarts is
   * the same defect wearing a different hat, and it would destroy a kernel on every turn of it.
   *
   * One greeting per kernel is also the seventh sighting's finding kept intact: the second ask
   * is not a retry, it goes to a different process.
   */
  @Test
  fun theReplacementKernelIsGreetedOnceAndNoMore() = runTest {
    val opener = neverGreets()
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    runCatching { gateway.execute(session, "cell-1", "2 + 2").toList() }

    assertEquals(2, opener.sentMsgTypes.count { it == "kernel_info_request" })
  }

  /**
   * The point of the whole sub-step: the cell the stall used to swallow now runs.
   *
   * The restart is asserted beside it, because "the cell finished" alone was already true before
   * this existed - the failsafe released it into the socket and the scripted kernel answered.
   * What is new is that it ran on a kernel that had been replaced.
   */
  @Test
  fun aKernelThatAnswersAfterTheRestartRunsTheCell() = runTest {
    val lifecycle = FakeKernelLifecycle()
    val opener = ScriptedKernelSocketOpener(
      reply = { KernelFrames.successfulExecution(it) },
      answerKernelInfo = { ask -> ask > 1 },
    )
    val gateway = RealKernelGateway(KernelSockets(opener, lifecycle))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    val events = gateway.execute(session, "cell-1", "2 + 2").toList()

    assertTrue("the cell did not finish: $events", events.contains(KernelEvent.Idle))
    assertEquals(listOf(session.kernelId), lifecycle.restarted)
  }

  /**
   * A restart that did not help must fail the cell, not hand it to the dead channel anyway.
   *
   * This is where the reference is departed from and it is deliberate. JupyterLab's failsafe
   * releases the pending messages after the timeout; here that is precisely what produced the
   * stall - a request enqueued against a channel carrying nothing, waited on for ever with no
   * error raised anywhere. A cell that reports "the kernel did not answer" can be re-run; a cell
   * that spins for ever cannot be told from one that is merely slow.
   */
  @Test
  fun aKernelThatAnswersNeitherGreetingFailsTheCell() = runTest {
    val gateway = RealKernelGateway(KernelSockets(neverGreets(), FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    val failure = runCatching { gateway.execute(session, "cell-1", "2 + 2").toList() }.exceptionOrNull()

    assertTrue("the cell was not failed, it was $failure", failure is KernelChannelException)
    assertTrue("the message must name the session: ${failure?.message}", failure!!.message!!.contains(session.id))
  }

  /**
   * A restart the server refuses is reported, not swallowed into another wait.
   *
   * The recovery is the only place in the execute path that makes a REST call, so it is the only
   * place that can fail with the server's own error - and the whole point of this sub-step is
   * that no branch of it ends in a cell that spins. The session is built rather than connected
   * for, because a [FakeKernelLifecycle] that refuses everything would refuse `start` too.
   */
  @Test
  fun aRestartTheServerRefusesFailsTheCell() = runTest {
    val refused = KernelLifecycleException("POST api/kernels/kernel-1/restart failed: HTTP 500")
    val gateway = RealKernelGateway(KernelSockets(neverGreets(), FakeKernelLifecycle(failWith = refused)))
    val session = KernelSession(
      id = "session-1",
      spaceId = "base",
      kernelId = "kernel-1",
      kernelName = "python3",
      serverSessionId = "server-session-1",
    )

    val failure = runCatching { gateway.execute(session, "cell-1", "2 + 2").toList() }.exceptionOrNull()

    assertEquals(refused, failure)
  }

  /**
   * Asking for completions must not replace the user's kernel.
   *
   * `complete` waits on the same handshake a cell does, so recovery would otherwise fire while
   * somebody is typing - silently discarding the variables of a kernel they had merely opened a
   * notebook onto. Restarting is a cost worth paying to save a cell and not worth paying to save
   * a completion popup, which has an answer for "the kernel cannot say": nothing.
   *
   * The same rule that keeps typing from *starting* a runtime (`NotebookAsks.completionsAt`)
   * one step further on.
   */
  @Test
  fun typingDoesNotRestartTheKernel() = runTest {
    val lifecycle = FakeKernelLifecycle()
    val gateway = RealKernelGateway(KernelSockets(neverGreets(), lifecycle))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    // Cancelled the way the editor cancels it - by typing again - since a completion against a
    // channel carrying nothing has no deadline of its own. Well past two greeting timeouts, or
    // the test would pass by never reaching the point where recovery could fire.
    withTimeoutOrNull(30.seconds) { gateway.complete(session, "impo", 4) }

    assertEquals(emptyList<String>(), lifecycle.restarted)
  }

  /** And the request itself is never handed over, which is the stall in one line. */
  @Test
  fun noRequestIsSentIntoAChannelThatAnsweredNeitherGreeting() = runTest {
    val opener = neverGreets()
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    runCatching { gateway.execute(session, "cell-1", "2 + 2").toList() }

    assertEquals(0, opener.sentMsgTypes.count { it == "execute_request" })
  }

  private fun neverGreets() = ScriptedKernelSocketOpener(
    reply = { KernelFrames.successfulExecution(it) },
    answerKernelInfo = { false },
  )

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
