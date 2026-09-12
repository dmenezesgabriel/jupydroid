package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelEvent
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A cell's request must not be handed over before the socket is connected.
 *
 * The execution stall, four sightings and finally diagnosed
 * (`evidence/kernel-observability/stall-diagnosed.md`): the app enqueued `execute_request` from
 * `onSubscription`, which runs the instant a collector attaches and long before OkHttp has
 * finished the handshake. OkHttp queues the frame and flushes it on open, so it can reach
 * `jupyter_server` before that server's own `open()` has associated the session - and be dropped.
 * The kernel then sends its startup statuses and nothing else, the cell spins forever, and no
 * error is raised anywhere because from the app's side the send "succeeded".
 *
 * `enqueued: true` was never evidence of delivery, which is why the log field is not called
 * `sent`. JupyterLab does not send before the connection is up either.
 */
class KernelRequestWaitsForTheSocketTest {
  @Test
  fun nothingIsSentUntilTheHandshakeFinishes() = runTest {
    val opener = ScriptedKernelSocketOpener(
      reply = { KernelFrames.successfulExecution(it) },
      openImmediately = false,
    )
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    val running = async { gateway.execute(session, "cell-1", "2 + 2").toList() }
    yield()
    assertEquals("the request went out before the socket opened", emptyList<String>(), opener.sentBeforeOpen)

    opener.finishHandshake()

    assertTrue("the cell never finished once connected", running.await().contains(KernelEvent.Idle))
  }

  /**
   * And it must not wait for ever.
   *
   * Waiting turns a lost frame into a hang if the handshake never lands - a refused upgrade, or a
   * 404 for a kernel that has gone. That has to fail the cell, the way a socket ending mid-run
   * already does: a cell that silently produced nothing looks exactly like one that ran and
   * printed nothing.
   */
  @Test
  fun aHandshakeThatFailsFailsTheCellRatherThanHanging() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { listOf() }, openImmediately = false)
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    val running = async { runCatching { gateway.execute(session, "cell-1", "2 + 2").toList() } }
    yield()
    opener.failHandshake("Failed to connect (http 404)")

    val failure = running.await().exceptionOrNull()
    assertTrue("expected the cell to fail, got $failure", failure is KernelChannelException)
  }

  /** A second cell on a socket already open must not wait for an event that has been and gone. */
  @Test
  fun aLaterCellOnAnOpenSocketRunsStraightAway() = runTest {
    val opener = ScriptedKernelSocketOpener(reply = { KernelFrames.successfulExecution(it) })
    val gateway = RealKernelGateway(KernelSockets(opener, FakeKernelLifecycle()))
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.execute(session, "cell-1", "2 + 2").toList()
    val second = gateway.execute(session, "cell-2", "3 + 3").toList()

    assertTrue("the second cell did not complete", second.contains(KernelEvent.Idle))
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
