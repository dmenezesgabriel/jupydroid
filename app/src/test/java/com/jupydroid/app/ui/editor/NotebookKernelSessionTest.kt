package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Space
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
}
