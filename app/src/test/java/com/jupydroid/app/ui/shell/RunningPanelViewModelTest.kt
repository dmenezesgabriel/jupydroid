@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.shell

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.space.KernelLifecycleException
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.KernelSessions
import com.jupydroid.app.domain.port.SessionType
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
   * The row's `type` is the value `running:kernel-open-session`'s tap reads to pick
   * `console:open` over `docmanager:open` - it must be what the mint declared, not what the
   * path's suffix implies.
   */
  @Test
  fun aSessionDeclaredAConsoleListsAsOne() = runTest {
    kernelGateway.connect(BASE_SPACE, "console-1-abc", sessionType = SessionType.CONSOLE)
    val model = showing().also { it.refresh() }
    advanceUntilIdle()

    assertEquals("console", model.uiState.value.sessions.single().type)
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

  /**
   * The class contract's own promise: a refused `GET api/sessions` keeps the last good list, the
   * way the reference's section keeps its rows when `refreshRunning` rejects - the launch's
   * failure must neither escape nor empty the panel.
   */
  @Test
  fun aRefusedRelistKeepsTheLastGoodSessions() = runTest {
    val refusing = RefusingKernelSessions(kernelSessions)
    kernelGateway.connect(BASE_SPACE, "notes.ipynb")
    val model = RunningPanelViewModel(refusing, terminalGateway).also { it.refresh() }
    advanceUntilIdle()
    refusing.refusingList = true

    model.refresh()
    advanceUntilIdle()

    assertEquals(listOf("notes.ipynb"), model.uiState.value.sessions.map { it.name })
  }

  /**
   * A refused `DELETE api/sessions/<id>` dies quietly: `ended`, not the call's return, is what
   * removes a row - and no end was emitted for a delete the server never saw.
   */
  @Test
  fun aRefusedShutdownLeavesTheSessionListed() = runTest {
    val refusing = RefusingKernelSessions(kernelSessions)
    kernelGateway.connect(BASE_SPACE, "notes.ipynb")
    val model = RunningPanelViewModel(refusing, terminalGateway).also { it.refresh() }
    advanceUntilIdle()
    refusing.refusingShutdown += model.uiState.value.sessions.single().serverSessionId

    model.shutdownSession(model.uiState.value.sessions.single())
    advanceUntilIdle()

    assertEquals(listOf("notes.ipynb"), model.uiState.value.sessions.map { it.name })
  }

  /**
   * `shutdown-all` with one DELETE refused: the refusal does not skip the rows behind it - each
   * shutdown is its own ask, the way `forEach { it.shutdown() }` over the managers is.
   */
  @Test
  fun aRefusedShutdownDoesNotSkipTheRestOfShutdownAll() = runTest {
    val refusing = RefusingKernelSessions(kernelSessions)
    val a = kernelGateway.connect(BASE_SPACE, "a.ipynb")
    kernelGateway.connect(BASE_SPACE, "b.ipynb")
    val model = RunningPanelViewModel(refusing, terminalGateway).also { it.refresh() }
    advanceUntilIdle()
    refusing.refusingShutdown += a.serverSessionId

    model.shutdownAllSessions()
    advanceUntilIdle()

    assertEquals(listOf("a.ipynb"), model.uiState.value.sessions.map { it.name })
  }

  /**
   * `terminal:close` whose DELETE is refused still relists - the terminal is simply still there,
   * which is the list's own truth rather than the call's.
   */
  @Test
  fun aRefusedTerminalCloseLeavesTheTerminalListed() = runTest {
    val refusing = RefusingTerminalGateway(terminalGateway)
    refusing.open(cwd = null)
    val model = RunningPanelViewModel(kernelSessions, refusing).also { it.refresh() }
    advanceUntilIdle()
    refusing.refusing = true

    model.closeTerminal(TerminalSession("1"))
    advanceUntilIdle()

    assertEquals(listOf(TerminalSession("1")), model.uiState.value.terminals)
  }
}

/** `GET`/`DELETE api/sessions` refusing on demand - what a dead server answers the panel with. */
private class RefusingKernelSessions(private val fake: FakeKernelSessions) : KernelSessions by fake {
  var refusingList = false
  val refusingShutdown = mutableSetOf<String>()

  override suspend fun running(): List<KernelSessionModel> {
    if (refusingList) throw KernelLifecycleException("GET api/sessions refused")
    return fake.running()
  }

  override suspend fun shutdown(serverSessionId: String) {
    if (serverSessionId in refusingShutdown) {
      throw KernelLifecycleException("DELETE api/sessions/$serverSessionId refused")
    }
    fake.shutdown(serverSessionId)
  }
}

/** `GET`/`DELETE api/terminals` refusing on demand - the terminal half of the same dead server. */
private class RefusingTerminalGateway(private val fake: FakeTerminalGateway) : TerminalGateway by fake {
  var refusing = false

  override suspend fun running(): List<TerminalSession> {
    if (refusing) throw KernelLifecycleException("GET api/terminals refused")
    return fake.running()
  }

  override suspend fun close(session: TerminalSession) {
    if (refusing) throw KernelLifecycleException("DELETE api/terminals/${session.name} refused")
    fake.close(session)
  }
}
