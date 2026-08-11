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
 * The execution stall has been seen three times (`docs/PLAN.md`) and diagnosed none of them. The
 * third sighting is the one that explains why: of 33,279 logcat lines, five mentioned the runtime,
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
    val session = gateway().connect(SPACE)

    assertEquals(session.kernelId, log.fieldsOf(KernelLog.SESSION_OPENED)["kernelId"])
    assertEquals(session.id, log.fieldsOf(KernelLog.SESSION_OPENED)["sessionId"])
  }

  @Test
  fun execute_reportsTheRequestItSent() = runTest {
    val gateway = gateway()
    val session = gateway.connect(SPACE)

    gateway.execute(session, "cell-1", "2 + 2").toList()

    assertEquals("cell-1", log.fieldsOf(KernelLog.EXECUTE_REQUEST)["cellId"])
    val msgId = log.fieldsOf(KernelLog.EXECUTE_REQUEST)["msgId"]
    assertTrue("a request id is what ties the reply back", !msgId.isNullOrEmpty())
  }

  @Test
  fun execute_reportsThatItCompleted() = runTest {
    val gateway = gateway()
    val session = gateway.connect(SPACE)

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
    val session = gateway.connect(SPACE)

    gateway.execute(session, "cell-1", "2 + 2").toList()

    assertTrue("no frame was reported at all", log.countOf(KernelLog.FRAME_RECEIVED) > 0)
    assertEquals("1", log.fieldsOf(KernelLog.FRAME_RECEIVED)["subscribers"])
  }

  /** A socket that ends under a running cell is the other way this stalls, and it is loud. */
  @Test
  fun aSocketThatEndsIsReportedWithItsReason() = runTest {
    val opener = ScriptedKernelSocketOpener(
      reply = { listOf() },
      endAfter = KernelSocketEvent.Ended(reason = "closed: 1006 gone", failed = false),
    )
    val gateway = RealKernelGateway(opener, FakeKernelLifecycle(), log)
    val session = gateway.connect(SPACE)

    runCatching { gateway.execute(session, "cell-1", "2 + 2").toList() }

    assertEquals("closed: 1006 gone", log.fieldsOf(KernelLog.SOCKET_ENDED)["reason"])
  }

  private fun gateway() = RealKernelGateway(
    ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) }),
    FakeKernelLifecycle(),
    log,
  )

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
