package com.jupydroid.app.ui.shell

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What the Sessions and Tabs panel lists and ends - `RunningSessions`' own state
 * (`packages/running/src/index.tsx`), behind this app's viewmodel seam.
 *
 * The gateway and the sessions port share one [KernelSessionRegistry] for the reason
 * [FakeKernelSessions] documents: a shutdown the panel issues has to be the same `ended` the open
 * document hears, so these tests drive the fake pair exactly as `AppContainer` wires it.
 */
class RunningPanelViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val registry = KernelSessionRegistry()
  private val kernelGateway = FakeKernelGateway(registry)
  private val kernelSessions = FakeKernelSessions(registry)
  private val terminalGateway = FakeTerminalGateway()

  private fun showing() = RunningPanelViewModel(kernelSessions, terminalGateway)

  /** Nothing is asked until the panel asks it - the listing is `refresh()`'s, not `init`'s. */
  @Test
  fun aPanelListsNothingBeforeItsFirstRefresh() = runTest {
    kernelGateway.connect(BASE_SPACE, "notes.ipynb")

    assertTrue(showing().uiState.value.sessions.isEmpty())
  }

  /** `refreshRunning` on both managers - `GET api/sessions` and `GET api/terminals`. */
  @Test
  fun refreshListsTheSessionsAndTerminalsTheServerHolds() = runTest {
    kernelGateway.connect(BASE_SPACE, "notes.ipynb")
    terminalGateway.open(cwd = null)
    val model = showing()

    model.refresh()
    advanceUntilIdle()

    assertEquals(listOf("notes.ipynb"), model.uiState.value.sessions.map { it.name })
    assertEquals(listOf(TerminalSession("1")), model.uiState.value.terminals)
  }

  /**
   * `running:kernel-shut-down` - `DELETE api/sessions/<id>` on the row's own session. The row is
   * gone afterwards because `ended` is what the list reconciles by.
   */
  @Test
  fun shuttingDownASessionRemovesItsRow() = runTest {
    kernelGateway.connect(BASE_SPACE, "notes.ipynb")
    val model = showing().also { it.refresh() }
    advanceUntilIdle()

    model.shutdownSession(model.uiState.value.sessions.single())
    advanceUntilIdle()

    assertTrue(model.uiState.value.sessions.isEmpty())
  }

  /**
   * `runningChanged` rather than a poll wait: a session ended *by its holder* - a notebook's own
   * "Shut Down Kernel", which never touches the panel - still leaves the list, because the panel
   * is subscribed to `ended` the way the reference's sections are to `manager.runningChanged`.
   */
  @Test
  fun aSessionEndedElsewhereLeavesTheListWithoutARefresh() = runTest {
    val session = kernelGateway.connect(BASE_SPACE, "notes.ipynb")
    val model = showing().also { it.refresh() }
    advanceUntilIdle()

    registry.end(session.serverSessionId)
    advanceUntilIdle()

    assertTrue(model.uiState.value.sessions.isEmpty())
  }

  /**
   * `kernelShutdownAll` - one DELETE per session. The confirmation's count is the section's, so a
   * caller passing the whole list is the shape the count depends on.
   */
  @Test
  fun shuttingDownAllSessionsClearsTheSection() = runTest {
    kernelGateway.connect(BASE_SPACE, "a.ipynb")
    kernelGateway.connect(BASE_SPACE, "b.ipynb")
    val model = showing().also { it.refresh() }
    advanceUntilIdle()

    model.shutdownAllSessions()
    advanceUntilIdle()

    assertTrue(model.uiState.value.sessions.isEmpty())
  }

  /** `terminal:close` from the row - `DELETE api/terminals/<name>`. */
  @Test
  fun closingATerminalRemovesItsRow() = runTest {
    val terminal = terminalGateway.open(cwd = null)
    val model = showing().also { it.refresh() }
    advanceUntilIdle()

    model.closeTerminal(terminal)
    advanceUntilIdle()

    assertTrue(model.uiState.value.terminals.isEmpty())
  }

  @Test
  fun shuttingDownAllTerminalsClearsTheSection() = runTest {
    terminalGateway.open(cwd = null)
    terminalGateway.open(cwd = null)
    val model = showing().also { it.refresh() }
    advanceUntilIdle()

    model.shutdownAllTerminals()
    advanceUntilIdle()

    assertTrue(model.uiState.value.terminals.isEmpty())
  }
}
