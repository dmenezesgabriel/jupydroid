package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.StaleKernelSessionException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which kernel a notebook is on, and what happens when the server behind it stops (P1c). */
class NotebookKernelSessionTest {
  private val space = Space(id = "base", name = "Base", kernels = emptyList())

  @Test
  fun open_connectsOnceAndKeepsTheSameSession() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 0)
    val session = NotebookKernelSession(gateway, space) { "Analysis.ipynb" }

    assertEquals(session.open(), session.open())
    assertEquals(1, gateway.connectCount)
  }

  @Test
  fun open_returnsThePreAttachedSessionWithoutConnecting() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 0)
    val attached = KernelSession(id = "s1", spaceId = "base", kernelId = "k1", serverSessionId = "ss1")
    val session = NotebookKernelSession(gateway, space, attachedTo = attached) { "Analysis.ipynb" }

    assertEquals(attached, session.open())
    assertEquals(0, gateway.connectCount)
  }

  @Test
  fun constructingWithAttachedToReportsTheKernelArrived() {
    val reported = mutableListOf<Boolean>()
    val attached = KernelSession(id = "s1", spaceId = "base", kernelId = "k1", serverSessionId = "ss1")

    NotebookKernelSession(
      StaleSessionKernelGateway(refusals = 0),
      space,
      onConnectedChanged = { reported += it },
      attachedTo = attached,
    ) { "Analysis.ipynb" }

    assertEquals(listOf(true), reported)
  }

  @Test
  fun reopeningIfStale_reconnectsAPreAttachedSessionThatProvesStale() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 1)
    val attached = KernelSession(id = "stale-attached", spaceId = "base", kernelId = "k0", serverSessionId = "ss0")
    val session = NotebookKernelSession(gateway, space, attachedTo = attached) { "Analysis.ipynb" }

    val events = session.reopeningIfStale { open -> gateway.execute(open, "c1", "2 + 2") }.toList()

    assertEquals("the pre-attached session was never counted, only its replacement was", 1, gateway.connectCount)
    assertTrue("expected the retry to produce the cell's events", events.isNotEmpty())
  }

  @Test
  fun forget_handsBackThePreAttachedSessionAndLeavesNone() {
    val attached = KernelSession(id = "s1", spaceId = "base", kernelId = "k1", serverSessionId = "ss1")
    val session = NotebookKernelSession(StaleSessionKernelGateway(refusals = 0), space, attachedTo = attached) {
      "Analysis.ipynb"
    }

    assertEquals(attached, session.forget())
    assertNull(session.opened)
  }

  @Test
  fun open_asksForThePathTheNotebookHasNowRatherThanTheOneItHadThen() = runTest {
    var path = "Draft.ipynb"
    val gateway = RecordingConnectKernelGateway()
    val session = NotebookKernelSession(gateway, space) { path }

    session.open()
    session.forget()
    path = "Renamed.ipynb"
    session.open()

    assertEquals(listOf("Draft.ipynb", "Renamed.ipynb"), gateway.paths)
  }

  @Test
  fun reopeningIfStale_opensAFreshSessionAndTriesAgain() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 1)
    val session = NotebookKernelSession(gateway, space) { "Analysis.ipynb" }

    val events = session.reopeningIfStale { open -> gateway.execute(open, "c1", "2 + 2") }.toList()

    assertEquals(2, gateway.connectCount)
    assertTrue("expected the retry to produce the cell's events", events.isNotEmpty())
  }

  @Test
  fun reopeningIfStale_letsASecondRefusalThrough() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 2)
    val session = NotebookKernelSession(gateway, space) { "Analysis.ipynb" }

    val failure = runCatching { session.reopeningIfStale { open -> gateway.execute(open, "c1", "2 + 2") } }

    assertTrue(
      "expected the second refusal to reach the caller, got ${failure.exceptionOrNull()}",
      failure.exceptionOrNull() is StaleKernelSessionException,
    )
    assertEquals("and no third attempt", 2, gateway.connectCount)
  }

  @Test
  fun forget_handsBackTheOpenSessionAndLeavesNone() = runTest {
    val session = NotebookKernelSession(StaleSessionKernelGateway(refusals = 0), space) { "Analysis.ipynb" }
    val open = session.open()

    assertEquals(open, session.forget())
    assertNull(session.opened)
  }

  @Test
  fun forget_hasNothingToHandBackWhenNoKernelWasEverOpened() {
    val session = NotebookKernelSession(StaleSessionKernelGateway(refusals = 0), space) { "Analysis.ipynb" }

    assertNull(session.forget())
  }

  /**
   * **Every arrival and every departure is reported**, which is why the report lives on the setter
   * rather than at each call site - see [NotebookKernelSession.onConnectedChanged]. The sequence a
   * stale reconnect produces is the case a per-caller report would miss: the refusal drops the
   * session inside [NotebookKernelSession.reopeningIfStale], where no caller can see it, and a
   * notebook that went on claiming a kernel there would say *Idle* with no kernel at all.
   */
  @Test
  fun aStaleReconnectReportsTheKernelGoingAndComingBack() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 1)
    val reported = mutableListOf<Boolean>()
    val session =
      NotebookKernelSession(gateway, space, onConnectedChanged = { reported += it }) { "Analysis.ipynb" }

    session.reopeningIfStale { open -> gateway.execute(open, "c1", "2 + 2").toList() }

    assertEquals(listOf(true, false, true), reported)
  }

  @Test
  fun forgettingReportsTheKernelGone() = runTest {
    val reported = mutableListOf<Boolean>()
    val session = NotebookKernelSession(
      StaleSessionKernelGateway(refusals = 0),
      space,
      onConnectedChanged = { reported += it },
    ) { "Analysis.ipynb" }

    session.open()
    session.forget()

    assertEquals(listOf(true, false), reported)
  }
}
