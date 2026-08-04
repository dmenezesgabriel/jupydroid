package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelGatewayContract
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The real gateway owes the same contract as the fake, plus the protocol's own rules. */
class RealKernelGatewayTest : KernelGatewayContract() {
  override fun gateway(): KernelGateway = RealKernelGateway(
    ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) }),
    FakeKernelLifecycle(),
  )

  @Test
  fun execute_streamsTheKernelsOutputInOrder() = runTest {
    val events = runCell { KernelFrames.successfulExecution(it, text = "4") }

    assertEquals(
      listOf(
        KernelEvent.ExecutionCount("cell-1", 1),
        KernelEvent.Output("cell-1", CellOutput.ExecuteResult(mapOf("text/plain" to "4"), 1)),
        KernelEvent.Idle,
      ),
      events,
    )
  }

  /**
   * The completion rule, which is the whole reason this class is not "finish on idle":
   * idle alone must not end the execution, or output still in flight is dropped.
   */
  @Test
  fun execute_doesNotFinishOnIdleAloneWithoutTheReply() = runTest {
    val events = runCell { parent ->
      listOf(
        KernelFrames.idle(parent),
        KernelFrames.stream(parent, "arrived after idle"),
        KernelFrames.executeReply(parent),
      )
    }

    val streamed = events.filterIsInstance<KernelEvent.Output>().map { it.output }
    assertTrue("output after idle must survive, got $events", streamed.any { it is CellOutput.Stream })
    assertEquals(KernelEvent.Idle, events.last())
  }

  @Test
  fun execute_doesNotFinishOnTheReplyAloneWithoutIdle() = runTest {
    val events = runCell { parent ->
      listOf(
        KernelFrames.executeReply(parent),
        KernelFrames.stream(parent, "still coming"),
        KernelFrames.idle(parent),
      )
    }

    assertEquals(KernelEvent.Idle, events.last())
    assertTrue(events.filterIsInstance<KernelEvent.Output>().isNotEmpty())
  }

  /** Two notebooks share a kernel: another cell's traffic must not appear as ours. */
  @Test
  fun execute_ignoresFramesBelongingToAnotherRequest() = runTest {
    val events = runCell { parent ->
      listOf(KernelFrames.stream("someone-else", "not ours")) + KernelFrames.successfulExecution(parent)
    }

    val streamed = events.filterIsInstance<KernelEvent.Output>().map { it.output }
    assertTrue("another request's output leaked in: $streamed", streamed.none { it is CellOutput.Stream })
  }

  @Test
  fun execute_opensTheSocketForTheSessionsKernel() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) })
    val gateway = RealKernelGateway(opener, FakeKernelLifecycle())
    val session = gateway.connect(SPACE)

    gateway.execute(session, "cell-1", "2 + 2").toList()

    assertEquals(session.kernelId, opener.openedKernelId)
    assertEquals(session.id, opener.openedSessionId)
    assertTrue("the socket must be closed when the flow ends", opener.closed)
  }

  /** A dropped socket must fail the cell, not quietly complete it as producing nothing. */
  @Test
  fun execute_failsWhenTheSocketEndsMidExecution() = runTest {
    val opener = ScriptedKernelSocketOpener(
      reply = { listOf(KernelFrames.stream(it, "partial")) },
      endAfter = KernelSocketEvent.Ended("connection reset", failed = true),
    )
    val gateway = RealKernelGateway(opener, FakeKernelLifecycle())

    val failure = runCatching { gateway.execute(gateway.connect(SPACE), "cell-1", "2 + 2").toList() }

    assertTrue("expected the flow to fail, got $failure", failure.isFailure)
    assertTrue("reason must survive", failure.exceptionOrNull()?.message?.contains("connection reset") == true)
  }

  /** A socket closing cleanly after a finished execution is not an error. */
  @Test
  fun execute_completesNormallyWhenTheSocketClosesAfterIdle() = runTest {
    val opener = ScriptedKernelSocketOpener(
      reply = { KernelFrames.successfulExecution(it) },
      endAfter = KernelSocketEvent.Ended("closed: 1000 ", failed = false),
    )
    val gateway = RealKernelGateway(opener, FakeKernelLifecycle())

    val events = gateway.execute(gateway.connect(SPACE), "cell-1", "2 + 2").toList()

    assertEquals(KernelEvent.Idle, events.last())
  }

  // --- Step 10.4a: kernel lifecycle over REST ---

  @Test
  fun connect_startsARealKernelAndBindsTheSessionToIt() = runTest {
    val lifecycle = FakeKernelLifecycle(kernelId = "kernel-from-server")
    val gateway = RealKernelGateway(ScriptedKernelSocketOpener(reply = { emptyList() }), lifecycle)

    val session = gateway.connect(SPACE)

    assertEquals("kernel-from-server", session.kernelId)
    assertEquals(1, lifecycle.startCount)
  }

  /** Interrupt goes over REST, because a busy kernel is not reading its shell channel. */
  @Test
  fun interrupt_asksTheServerToInterruptTheSessionsKernel() = runTest {
    val lifecycle = FakeKernelLifecycle(kernelId = "kernel-9")
    val gateway = RealKernelGateway(ScriptedKernelSocketOpener(reply = { emptyList() }), lifecycle)
    val session = gateway.connect(SPACE)

    gateway.interrupt(session)

    assertEquals(listOf("kernel-9"), lifecycle.interrupted)
  }

  /** A kernel that will not start must surface, not leave a session pointing at nothing. */
  @Test
  fun connect_failsWhenTheServerWillNotStartAKernel() = runTest {
    val gateway = RealKernelGateway(
      ScriptedKernelSocketOpener(reply = { emptyList() }),
      FakeKernelLifecycle(failWith = KernelLifecycleException("HTTP 500")),
    )

    val failure = runCatching { gateway.connect(SPACE) }

    assertTrue("expected connect to fail, got $failure", failure.isFailure)
  }

  private suspend fun runCell(script: (String) -> List<String>): List<KernelEvent> {
    val gateway = RealKernelGateway(ScriptedKernelSocketOpener(reply = script), FakeKernelLifecycle())
    return gateway.execute(gateway.connect(SPACE), "cell-1", "2 + 2").toList()
  }
}
