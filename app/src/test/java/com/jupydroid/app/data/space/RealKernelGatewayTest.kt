package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelGatewayContract
import com.jupydroid.app.mimeBundleOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/** The real gateway owes the same contract as the fake, plus the protocol's own rules. */
class RealKernelGatewayTest : KernelGatewayContract() {
  override fun gateway(): KernelGateway = RealKernelGateway(
    KernelSockets(
      ScriptedKernelSocketOpener(reply = {
        everyReplyKind(it)
      }),
      FakeKernelLifecycle(),
    ),
  )

  @Test
  fun execute_streamsTheKernelsOutputInOrder() = runTest {
    val events = runCell { KernelFrames.successfulExecution(it, text = "4") }

    assertEquals(
      listOf(
        KernelEvent.ExecutionCount("cell-1", 1),
        KernelEvent.Output("cell-1", CellOutput.ExecuteResult(mimeBundleOf("text/plain" to "4"), 1)),
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
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.execute(session, "cell-1", "2 + 2").toList()

    assertEquals(session.kernelId, opener.openedKernelId)
    assertEquals(session.id, opener.openedSessionId)
  }

  // --.4b: one socket per session, not per execution ---

  /** A socket per cell means a WebSocket handshake on every run, and a connection count that churns. */
  @Test
  fun execute_reusesOneSocketAcrossEveryCellInTheSession() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) })
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    repeat(3) { gateway.execute(session, "cell-$it", "2 + 2").toList() }

    assertEquals(1, opener.openCount)
    assertTrue("a finished cell must not close the session's socket", !opener.closed)
  }

  @Test
  fun disconnect_closesTheSessionSocket() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) })
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")
    gateway.execute(session, "cell-1", "2 + 2").toList()

    gateway.disconnect(session)

    assertTrue("disconnect must close the socket", opener.closed)
  }

  /**
   * A shutdown is a disconnect **and** a `DELETE`, in that order.
   *
   * Both halves are asserted because either alone is a defect with a long fuse: the socket left
   * open outlives the kernel it points at, and the session left behind is the ~55 MiB the command
   * exists to reclaim.
   */
  @Test
  fun shutdown_closesTheSocketAndDeletesTheServersSession() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) })
    val lifecycle = FakeKernelLifecycle()
    val gateway = RealKernelGateway(KernelSockets(opener, lifecycle))
    val session = gateway.connect(SPACE, "Analysis.ipynb")
    gateway.execute(session, "cell-1", "2 + 2").toList()

    gateway.shutdown(session)

    assertTrue("shutdown must close the socket", opener.closed)
    assertEquals(listOf(session.serverSessionId), lifecycle.shutDownSessions)
  }

  /**
   * A picked kernel is `kernel.name` on the session POST (`handlers.py:67-80`), and the started
   * model's `kernel.name` is what the session then carries - the picker row and the file's
   * `metadata.kernelspec` are both written from it.
   */
  @Test
  fun connect_startsTheSessionWithTheChosenKernel() = runTest {
    val lifecycle = FakeKernelLifecycle()
    val gateway = RealKernelGateway(KernelSockets(ScriptedKernelSocketOpener(reply = { emptyList() }), lifecycle))

    val session = gateway.connect(SPACE, "Analysis.ipynb", KernelChoice.Spec("p8g"))

    assertEquals(listOf(KernelChoice.Spec("p8g")), lifecycle.startedWith)
    assertEquals("p8g", session.kernelName)
  }

  /**
   * A kernel change binds a **different kernel id** (`handlers.py:patch` starts the named one and
   * shuts the old down), while the session - and so the socket map's key - stays. The socket
   * bound to the old kernel is stale, so it is closed and the next use opens one for the new id.
   */
  @Test
  fun changeKernel_reopensTheSocketForTheNewKernel() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) })
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")
    gateway.execute(session, "cell-1", "2 + 2").toList()

    val changed = gateway.changeKernel(session, KernelChoice.Spec("p8g"))
    gateway.execute(changed, "cell-2", "3 + 3").toList()

    assertEquals(2, opener.openCount)
    assertEquals(changed.kernelId, opener.openedKernelId)
    assertTrue("the old kernel's socket must be closed", opener.closed)
  }

  /** The change addresses the session's own id - `PATCH api/sessions/<serverSessionId>`. */
  @Test
  fun changeKernel_patchesTheServersOwnSessionId() = runTest {
    val lifecycle = FakeKernelLifecycle(serverSessionId = "server-session-9")
    val gateway = RealKernelGateway(KernelSockets(ScriptedKernelSocketOpener(reply = { emptyList() }), lifecycle))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.changeKernel(session, KernelChoice.Spec("p8g"))

    assertEquals(listOf("server-session-9" to KernelChoice.Spec("p8g")), lifecycle.kernelChanges)
  }

  /** The session id the server filed, never our own - they address different things. */
  @Test
  fun connect_carriesTheServersSessionIdSoItCanBeShutDown() = runTest {
    val lifecycle = FakeKernelLifecycle(serverSessionId = "server-session-9")
    val gateway = RealKernelGateway(KernelSockets(ScriptedKernelSocketOpener(reply = { emptyList() }), lifecycle))

    val session = gateway.connect(SPACE, "Analysis.ipynb")

    assertEquals("server-session-9", session.serverSessionId)
  }

  /** Two sessions are two kernels; sharing a socket between them would cross their traffic. */
  @Test
  fun execute_givesEachSessionItsOwnSocket() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) })
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))

    gateway.execute(gateway.connect(SPACE, "Analysis.ipynb"), "cell-1", "2 + 2").toList()
    gateway.execute(gateway.connect(SPACE, "Analysis.ipynb"), "cell-1", "2 + 2").toList()

    assertEquals(2, opener.openCount)
  }

  /** The flow is cold: collecting it twice must run the cell twice, not finish instantly. */
  @Test
  fun execute_canBeCollectedTwiceAndRunsTheCellEachTime() = runTest {
    val gateway =
      RealKernelGateway(
        KernelSockets(
          ScriptedKernelSocketOpener(reply = {
            KernelFrames.successfulExecution(it)
          }),
          FakeKernelLifecycle(),
        ),
      )
    val cell = gateway.execute(gateway.connect(SPACE, "Analysis.ipynb"), "cell-1", "2 + 2")

    assertEquals(cell.toList(), cell.toList())
    assertEquals(KernelEvent.Idle, cell.toList().last())
  }

  /** A dropped socket must fail the cell, not quietly complete it as producing nothing. */
  @Test
  fun execute_failsWhenTheSocketEndsMidExecution() = runTest {
    val opener = ScriptedKernelSocketOpener(
      reply = { listOf(KernelFrames.stream(it, "partial")) },
      endAfter = JupyterSocketEvent.Ended("connection reset", failed = true),
    )
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))

    val failure = runCatching { gateway.execute(gateway.connect(SPACE, "Analysis.ipynb"), "cell-1", "2 + 2").toList() }

    assertTrue("expected the flow to fail, got $failure", failure.isFailure)
    assertTrue("reason must survive", failure.exceptionOrNull()?.message?.contains("connection reset") == true)
  }

  /** A socket closing cleanly after a finished execution is not an error. */
  @Test
  fun execute_completesNormallyWhenTheSocketClosesAfterIdle() = runTest {
    val opener = ScriptedKernelSocketOpener(
      reply = { KernelFrames.successfulExecution(it) },
      endAfter = JupyterSocketEvent.Ended("closed: 1000 ", failed = false),
    )
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))

    val events = gateway.execute(gateway.connect(SPACE, "Analysis.ipynb"), "cell-1", "2 + 2").toList()

    assertEquals(KernelEvent.Idle, events.last())
  }

  /**
   * `kernel.reconnect()`'s act without the button: a socket that ended between runs is not the
   * session's socket anymore, so the next ask opens a fresh one rather than `send`ing on a dead
   * one - OkHttp returns false there and no frame ever comes back, which is a run that waits
   * for ever.
   */
  @Test
  fun execute_reopensASocketThatEndedBetweenRuns() = runTest {
    val opener = ScriptedKernelSocketOpener(
      reply = { KernelFrames.successfulExecution(it) },
      endAfter = JupyterSocketEvent.Ended("connection reset", failed = true),
    )
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.execute(session, "cell-1", "2 + 2").toList()
    val events = withTimeoutOrNull(3.seconds) { gateway.execute(session, "cell-2", "2 + 2").toList() }

    assertTrue("the second run hung on the dead socket (${opener.openCount} socket(s) opened)", events != null)
    assertEquals(2, opener.openCount)
    assertEquals(KernelEvent.Idle, events!!.last())
  }

  // --.5a: completion over the same socket ---

  @Test
  fun complete_returnsTheMatchesAndTheRangeFromTheKernelsReply() = runTest {
    val gateway =
      RealKernelGateway(
        KernelSockets(
          ScriptedKernelSocketOpener(reply = {
            listOf(KernelFrames.completeReply(it, listOf("print", "printf"), 4, 7))
          }),
          FakeKernelLifecycle(),
        ),
      )

    val result = gateway.complete(gateway.connect(SPACE, "Analysis.ipynb"), "x = pri", cursorOffset = 7)

    assertEquals(listOf("print", "printf"), result.matches.map { it.insert })
    assertEquals(4, result.replacedStart)
    assertEquals(7, result.replacedEnd)
  }

  /** The shell channel is shared, so a reply to another client must not be taken as ours. */
  @Test
  fun complete_waitsPastAReplyToSomebodyElsesRequest() = runTest {
    val gateway = RealKernelGateway(
      KernelSockets(
        ScriptedKernelSocketOpener(
          reply = { ours ->
            listOf(
              KernelFrames.completeReply("someone-else", listOf("wrong"), 0, 1),
              KernelFrames.completeReply(ours, listOf("print"), 0, 3),
            )
          },
        ),
        FakeKernelLifecycle(),
      ),
    )

    val result = gateway.complete(gateway.connect(SPACE, "Analysis.ipynb"), "pri", cursorOffset = 3)

    assertEquals(listOf("print"), result.matches.map { it.insert })
  }

  /** A completion asked for while a cell is running arrives after the execution's own frames. */
  @Test
  fun complete_ignoresTheExecutionTrafficItArrivesAmong() = runTest {
    val gateway =
      RealKernelGateway(
        KernelSockets(
          ScriptedKernelSocketOpener(reply = {
            everyReplyKind(it)
          }),
          FakeKernelLifecycle(),
        ),
      )

    val result = gateway.complete(gateway.connect(SPACE, "Analysis.ipynb"), "pri", cursorOffset = 3)

    assertEquals(listOf("print"), result.matches.map { it.insert })
  }

  @Test
  fun complete_failsWhenTheSocketEndsBeforeTheReply() = runTest {
    val gateway = RealKernelGateway(
      KernelSockets(
        ScriptedKernelSocketOpener(
          reply = { emptyList() },
          endAfter = JupyterSocketEvent.Ended("connection reset", failed = true),
        ),
        FakeKernelLifecycle(),
      ),
    )

    val failure = runCatching { gateway.complete(gateway.connect(SPACE, "Analysis.ipynb"), "pri", cursorOffset = 3) }

    assertTrue("expected the request to fail, got $failure", failure.isFailure)
  }

  @Test
  fun complete_reusesTheSessionSocketRatherThanOpeningAnother() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { everyReplyKind(it) })
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.execute(session, "cell-1", "2 + 2").toList()
    gateway.complete(session, "pri", cursorOffset = 3)

    assertEquals(1, opener.openCount)
  }

  // --- Execution history over the same socket ---

  @Test
  fun executionHistory_readsTheInputsOutOfTheReply() = runTest {
    val gateway =
      RealKernelGateway(
        KernelSockets(
          ScriptedKernelSocketOpener(reply = {
            listOf(KernelFrames.historyReply(it, listOf("x = 1", "x + 1")))
          }),
          FakeKernelLifecycle(),
        ),
      )

    val history = gateway.executionHistory(gateway.connect(SPACE, "Analysis.ipynb"), entries = 10)

    assertEquals(listOf("x = 1", "x + 1"), history)
  }

  /**
   * The reference's request, transcribed: the **tail** of the raw inputs, without their outputs
   * (`packages/notebook/src/history.ts:402-409`).
   *
   * Asserted on the frame rather than on the answer, because the shipped kernel branches on all
   * three: `raw` and `output` are passed straight to `get_tail`, and any `hist_access_type` other
   * than the three it knows returns an empty list rather than an error
   * (`ipykernel/ipkernel.py:612-645` in the shipped rootfs).
   */
  @Test
  fun executionHistory_asksForTheTailOfTheRawInputsWithoutTheirOutputs() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { listOf(KernelFrames.historyReply(it, listOf("x = 1"))) })
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))

    gateway.executionHistory(gateway.connect(SPACE, "Analysis.ipynb"), entries = 20)

    val request = opener.sent.single { """"msg_type":"history_request"""" in it }
    assertTrue("must ask for the tail, got $request", """"hist_access_type":"tail"""" in request)
    assertTrue("must ask for raw inputs, got $request", """"raw":true""" in request)
    assertTrue("must not ask for outputs, got $request", """"output":false""" in request)
    assertTrue("must carry the batch size, got $request", """"n":20""" in request)
  }

  /** The shell channel is shared, so a reply to another client must not be taken as ours. */
  @Test
  fun executionHistory_waitsPastAReplyToSomebodyElsesRequest() = runTest {
    val gateway = RealKernelGateway(
      KernelSockets(
        ScriptedKernelSocketOpener(
          reply = { ours ->
            listOf(
              KernelFrames.historyReply("someone-else", listOf("not ours")),
              KernelFrames.historyReply(ours, listOf("ours")),
            )
          },
        ),
        FakeKernelLifecycle(),
      ),
    )

    val history = gateway.executionHistory(gateway.connect(SPACE, "Analysis.ipynb"), entries = 10)

    assertEquals(listOf("ours"), history)
  }

  /** A kernel that has run nothing has an empty history, which is an answer rather than a failure. */
  @Test
  fun executionHistory_isEmptyForAKernelThatHasRunNothing() = runTest {
    val gateway =
      RealKernelGateway(
        KernelSockets(
          ScriptedKernelSocketOpener(reply = {
            listOf(KernelFrames.historyReply(it, emptyList()))
          }),
          FakeKernelLifecycle(),
        ),
      )

    assertEquals(emptyList<String>(), gateway.executionHistory(gateway.connect(SPACE, "Analysis.ipynb"), 10))
  }

  @Test
  fun executionHistory_failsWhenTheSocketEndsBeforeTheReply() = runTest {
    val gateway = RealKernelGateway(
      KernelSockets(
        ScriptedKernelSocketOpener(
          reply = { emptyList() },
          endAfter = JupyterSocketEvent.Ended("connection reset", failed = true),
        ),
        FakeKernelLifecycle(),
      ),
    )

    val failure = runCatching { gateway.executionHistory(gateway.connect(SPACE, "Analysis.ipynb"), 10) }

    assertTrue("expected the request to fail, got $failure", failure.isFailure)
  }

  // --.4a: kernel lifecycle over REST ---

  @Test
  fun connect_startsARealKernelAndBindsTheSessionToIt() = runTest {
    val lifecycle = FakeKernelLifecycle(kernelId = "kernel-from-server")
    val gateway = RealKernelGateway(KernelSockets(ScriptedKernelSocketOpener(reply = { emptyList() }), lifecycle))

    val session = gateway.connect(SPACE, "Analysis.ipynb")

    assertEquals("kernel-from-server", session.kernelId)
    assertEquals(1, lifecycle.startCount)
  }

  /** Interrupt goes over REST, because a busy kernel is not reading its shell channel. */
  @Test
  fun interrupt_asksTheServerToInterruptTheSessionsKernel() = runTest {
    val lifecycle = FakeKernelLifecycle(kernelId = "kernel-9")
    val gateway = RealKernelGateway(KernelSockets(ScriptedKernelSocketOpener(reply = { emptyList() }), lifecycle))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.interrupt(session)

    assertEquals(listOf("kernel-9"), lifecycle.interrupted)
  }

  /** A kernel that will not start must surface, not leave a session pointing at nothing. */
  @Test
  fun connect_failsWhenTheServerWillNotStartAKernel() = runTest {
    val gateway =
      RealKernelGateway(
        KernelSockets(
          ScriptedKernelSocketOpener(reply = {
            emptyList()
          }),
          FakeKernelLifecycle(failWith = KernelLifecycleException("HTTP 500")),
        ),
      )

    val failure = runCatching { gateway.connect(SPACE, "Analysis.ipynb") }

    assertTrue("expected connect to fail, got $failure", failure.isFailure)
  }

  private suspend fun runCell(script: (String) -> List<String>): List<KernelEvent> {
    val gateway = RealKernelGateway(KernelSockets(ScriptedKernelSocketOpener(reply = script), FakeKernelLifecycle()))
    return gateway.execute(gateway.connect(SPACE, "Analysis.ipynb"), "cell-1", "2 + 2").toList()
  }

  /**
   * Answers all three request kinds from one script. The gateway filters by `msg_type`, so the
   * execution frames are invisible to a completion, the completion reply is invisible to an
   * execution, and the history reply is invisible to both - the same indifference a real kernel's
   * shared channel demands.
   */
  private fun everyReplyKind(parent: String): List<String> = KernelFrames.successfulExecution(parent) +
    KernelFrames.completeReply(parent, listOf("print"), 0, 3) +
    KernelFrames.historyReply(parent, listOf("2 + 2"))
}
