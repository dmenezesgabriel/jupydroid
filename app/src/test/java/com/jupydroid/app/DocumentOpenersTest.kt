package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.port.SpaceServers
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [openNewTerminal] - `rememberNewTerminal`'s mint-and-reveal half - where the composable's own
 * reach is `TerminalNavigationTest`'s. The refused half is what matters: the launcher card's own
 * path reports through `LauncherViewModel.error`, and this reach has no error surface, so a
 * refused `open` must land as *no terminal opened* rather than escaping the launch it runs in -
 * the same `runCatching` the VIEW intent gives `createNotebook` in `Navigation.kt`.
 *
 * And [importNotebook], the VIEW intent's act: which Space it lands in, and that a refusal opens nothing.
 */
class DocumentOpenersTest {
  @Test
  fun anOpenedTerminalIsPushedOnTheStack() = runTest {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id))

    backStack.openNewTerminal(FakeTerminalGateway(), "dir")

    assertEquals(listOf<NavKey>(Explorer(BASE_SPACE.id), Terminal("1")), backStack)
  }

  @Test
  fun aRefusedTerminalOpenLeavesTheStackAlone() = runTest {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id))

    backStack.openNewTerminal(RefusingTerminalGateway(), "dir")

    assertEquals(listOf<NavKey>(Explorer(BASE_SPACE.id)), backStack)
  }

  /** From the home an import lands in base, which is run first: Open is enabled only on a running Space. */
  @Test
  fun anImportFromTheHomeRunsBaseAndEntersIt() = runTest {
    val backStack = mutableListOf<NavKey>(SpacesHome)
    val servers = FakeSpaceServers()

    val opened = backStack.importNotebook("Imported", FakeNotebookRepository(), servers)

    assertNotNull(opened)
    assertEquals(setOf(BASE_SPACE.id), servers.running.value)
    assertEquals(listOf(SpacesHome, Explorer(BASE_SPACE.id)), backStack)
  }

  /** Inside a workspace an import stays in the Space already entered, rather than switching to base. */
  @Test
  fun anImportInsideAWorkspaceStaysInTheEnteredSpace() = runTest {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer("space-1"))
    val servers = FakeSpaceServers()

    val opened = backStack.importNotebook("Imported", FakeNotebookRepository(), servers)

    assertNotNull(opened)
    assertEquals(emptySet<String>(), servers.running.value)
    assertEquals(listOf(SpacesHome, Explorer("space-1")), backStack)
  }

  @Test
  fun anImportWhoseRunIsRefusedOpensNothing() = runTest {
    val backStack = mutableListOf<NavKey>(SpacesHome)

    val opened = backStack.importNotebook("Imported", FakeNotebookRepository(), RefusingSpaceServers())

    assertNull(opened)
    assertEquals(listOf<NavKey>(SpacesHome), backStack)
  }

  /** Servers whose only answer to `run` is no. */
  private class RefusingSpaceServers : SpaceServers {
    override val running: StateFlow<Set<String>> = MutableStateFlow(emptySet())
    override suspend fun run(spaceId: String) = throw IllegalStateException("'$spaceId' did not start")
    override suspend fun stop(spaceId: String) = Unit
    override suspend fun stopAll() = Unit
  }

  /** A gateway whose only answer to `open` is no - the mint is the one ask this test needs. */
  private class RefusingTerminalGateway : TerminalGateway {
    override suspend fun open(cwd: String?): TerminalSession = throw IllegalStateException("server refused the mint")
    override suspend fun running(): List<TerminalSession> = emptyList()
    override fun attach(session: TerminalSession): Flow<TerminalEvent> = emptyFlow()
    override suspend fun send(session: TerminalSession, text: String) = Unit
    override suspend fun resize(session: TerminalSession, rows: Int, cols: Int) = Unit
    override suspend fun close(session: TerminalSession) = Unit
  }
}
