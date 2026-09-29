package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the execute path says while it works, so that the next time it does not, there is
 * something to read.
 *
 * The execution stall has been seen seven times and the captures taken
 * before this log existed diagnosed none of them. The third sighting is the one that explains
 * why: of 33,279 logcat lines, five mentioned the runtime,
 * and all five were the server coming up. The stall is *after* that, and past it the app said
 * nothing at all - so the capture could not tell a request that was never sent from a socket that
 * never connected from a reply that arrived with nobody listening.
 *
 * These are asserted rather than eyeballed because a log nobody checks rots exactly like a test
 * nobody runs, and this one is load-bearing for a defect that is still open.
 */
class RealKernelGatewayObservabilityTest {
  private val log = RecordingSpaceLifecycleLog()

  @Test
  fun connect_reportsTheKernelItBound() = runTest {
    val session = gateway().connect(SPACE, "Analysis.ipynb")

    assertEquals(session.kernelId, log.fieldsOf(KernelLog.SESSION_OPENED)["kernelId"])
    assertEquals(session.id, log.fieldsOf(KernelLog.SESSION_OPENED)["sessionId"])
  }

  @Test
  fun execute_reportsTheRequestItSent() = runTest {
    val gateway = gateway()
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.execute(session, "cell-1", "2 + 2").toList()

    assertEquals("cell-1", log.fieldsOf(KernelLog.EXECUTE_REQUEST)["cellId"])
    val msgId = log.fieldsOf(KernelLog.EXECUTE_REQUEST)["msgId"]
    assertTrue("a request id is what ties the reply back", !msgId.isNullOrEmpty())
  }

  @Test
  fun execute_reportsThatItCompleted() = runTest {
    val gateway = gateway()
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.execute(session, "cell-1", "2 + 2").toList()

    assertEquals("cell-1", log.fieldsOf(KernelLog.EXECUTE_COMPLETE)["cellId"])
  }

  /**
   * The question the third sighting could not answer: did anything come back at all?
   *
   * Reported per frame with the subscriber count, because "the kernel replied and nobody was
   * listening" and "the kernel never replied" look identical from the outside and are opposite
   * bugs. `MutableSharedFlow` with `replay = 0` drops what it emits to nobody, silently.
   */
  @Test
  fun everyFrameOffTheSocketIsReportedWithWhetherAnyoneWasListening() = runTest {
    val gateway = gateway()
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.execute(session, "cell-1", "2 + 2").toList()

    assertTrue("no frame was reported at all", log.countOf(KernelLog.FRAME_RECEIVED) > 0)
    // Not zero, rather than exactly one: the `kernel_info` handshake is collecting alongside the
    // cell, so two is ordinary. Zero is the signal - a frame nobody was listening for, which
    // `MutableSharedFlow(replay = 0)` discards in silence.
    val subscribers = log.fieldsOf(KernelLog.FRAME_RECEIVED)["subscribers"]?.toInt()
    assertTrue("a frame arrived with nobody listening", (subscribers ?: 0) > 0)
    // Which message, not merely how many bytes: the fourth sighting of the stall showed three
    // frames and then silence, and the log could not say which three. The first frame on any
    // socket is now the `kernel_info_reply` that releases the cell - see KernelInfoHandshakeTest.
    assertEquals("kernel_info_reply", log.fieldsOf(KernelLog.FRAME_RECEIVED)["msgType"])
  }

  /**
   * The `kernel_info` handshake says what it did, because for six sightings it said nothing.
   *
   * Every stall up to then had to be read from an *absence* - no `kernel_info_reply` among the
   * frames - which cannot tell "the greeting was never sent" from "it was sent and ignored". Those
   * are different defects with different fixes, and the seventh sighting is what a trail that says
   * which one buys: `greet.timedout` next to iopub frames parented to the server's own nudges,
   * which is a shell channel that is not delivering rather than a greeting nobody sent.
   */
  @Test
  fun theGreetingSaysWhetherItWasAnswered() = runTest {
    val gateway = gateway()
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.execute(session, "cell-1", "2 + 2").toList()

    assertEquals(session.id, log.fieldsOf(KernelLog.GREET_SENT)["sessionId"])
    assertEquals(session.id, log.fieldsOf(KernelLog.GREET_ANSWERED)["sessionId"])
    assertEquals(0, log.countOf(KernelLog.GREET_TIMED_OUT))
  }

  /** And a kernel that never answers is reported as that, rather than as nothing at all. */
  @Test
  fun aGreetingThatIsNeverAnsweredIsReportedAsTimedOut() = runTest {
    val gateway = RealKernelGateway(KernelSockets(neverGreets(), FakeKernelLifecycle(), log), log)
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    runCatching { gateway.execute(session, "cell-1", "2 + 2").toList() }

    assertEquals(session.id, log.fieldsOf(KernelLog.GREET_TIMED_OUT)["sessionId"])
    assertEquals(0, log.countOf(KernelLog.GREET_ANSWERED))
  }

  /**
   * The recovery restart says so, because it is the app throwing away a kernel the user did not
   * ask it to - and since a reopened notebook reattaches to the kernel it already had, that is
   * their variables. Without this line the trail shows a timeout and then a working kernel, which
   * reads as the stall having fixed itself.
   */
  @Test
  fun theRecoveryRestartIsReported() = runTest {
    val gateway = RealKernelGateway(KernelSockets(neverGreets(), FakeKernelLifecycle(), log), log)
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    runCatching { gateway.execute(session, "cell-1", "2 + 2").toList() }

    assertEquals(session.id, log.fieldsOf(KernelLog.GREET_RESTARTING)["sessionId"])
    assertEquals(1, log.countOf(KernelLog.GREET_RESTARTING))
  }

  private fun neverGreets() = ScriptedKernelSocketOpener(
    reply = { KernelFrames.successfulExecution(it) },
    answerKernelInfo = { false },
  )

  /** A socket that ends under a running cell is the other way this stalls, and it is loud. */
  @Test
  fun aSocketThatEndsIsReportedWithItsReason() = runTest {
    val opener = ScriptedKernelSocketOpener(
      reply = { listOf() },
      endAfter = JupyterSocketEvent.Ended(reason = "closed: 1006 gone", failed = false),
    )
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle(), log), log)
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    runCatching { gateway.execute(session, "cell-1", "2 + 2").toList() }

    assertEquals("closed: 1006 gone", log.fieldsOf(KernelLog.SOCKET_ENDED)["reason"])
  }

  private fun gateway() = RealKernelGateway(
    KernelSockets(
      ScriptedKernelSocketOpener(reply = {
        KernelFrames.successfulExecution(it)
      }),
      FakeKernelLifecycle(),
      log,
    ),
    log,
  )

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
