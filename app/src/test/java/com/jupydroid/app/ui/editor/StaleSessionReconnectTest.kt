package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.domain.model.CellOutput
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Running a cell after P1c stopped the server underneath it.
 *
 * The app is the one that stopped the server, so the app is the one that brings it back: the run
 * opens a fresh session and carries on, and the user sees an answer rather than a death they did
 * not cause. **This departs from the reference**, where a dead kernel is reported to the user
 * (`packages/apputils/src/sessioncontext.tsx:1167` displays the kernel's error) - there, nothing
 * stops a server on the client's behalf, so a death is always news.
 *
 * On the tablet the un-handled version is a websocket upgrade answered 404 by a server that never
 * heard of that kernel (`evidence/p1c-app-death-backstop/stale-session-404.txt`).
 */
class StaleSessionReconnectTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun aRunWhoseServerWasStopped_reconnectsAndProducesItsOutput() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = 1)
    val editor = loadedEditor(gateway = gateway)

    editor.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals("the run had to open a second session", 2, gateway.connectCount)
    val outputs = editor.uiState.value.outputs[codeCell.id].orEmpty()
    assertTrue("expected the cell's output, got $outputs", outputs.none { it is CellOutput.Error })
    assertTrue("expected something to have been produced", outputs.isNotEmpty())
  }

  /**
   * One retry, not a loop. A gateway that refuses every session is a real failure and the cell
   * says so - retrying for ever would hang the run the way P0 used to.
   */
  @Test
  fun aSessionThatStaysStale_isReportedInTheCellAndTriedOnceMore() = runTest {
    val gateway = StaleSessionKernelGateway(refusals = Int.MAX_VALUE)
    val editor = loadedEditor(gateway = gateway)

    editor.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals("exactly one retry", 2, gateway.connectCount)
    val outputs = editor.uiState.value.outputs[codeCell.id].orEmpty()
    assertTrue("expected the failure in the cell, got $outputs", outputs.any { it is CellOutput.Error })
    assertEquals(ExecutionStatus.STOPPED_ERROR, editor.uiState.value.statusOf(codeCell.id))
  }
}
