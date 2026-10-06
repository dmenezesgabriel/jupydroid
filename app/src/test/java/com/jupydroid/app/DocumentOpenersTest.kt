package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.spaceWithId
import com.jupydroid.app.domain.port.KernelSessionModel
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
import org.junit.Assert.assertTrue
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

    val repositories = SpaceRepositories()

    val opened = backStack.importNotebook("Imported", repositories::of, servers)

    assertNotNull(opened)
    assertEquals(setOf(BASE_SPACE.id), servers.running.value)
    assertEquals(listOf(SpacesHome, Explorer(BASE_SPACE.id)), backStack)
    assertEquals("Imported", repositories.of(BASE_SPACE.id).notebookRepository.readNotebook(opened!!).title)
  }

  /** Inside a workspace an import stays in the Space already entered, rather than switching to base. */
  @Test
  fun anImportInsideAWorkspaceStaysInTheEnteredSpace() = runTest {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer("space-1"))
    val servers = FakeSpaceServers()

    val repositories = SpaceRepositories()

    val opened = backStack.importNotebook("Imported", repositories::of, servers)

    assertNotNull(opened)
    assertEquals(emptySet<String>(), servers.running.value)
    assertEquals(listOf(SpacesHome, Explorer("space-1")), backStack)
    assertEquals("Imported", repositories.of("space-1").notebookRepository.readNotebook(opened!!).title)
    assertTrue(
      "written into base too",
      runCatching {
        repositories.of(BASE_SPACE.id).notebookRepository.readNotebook(opened)
      }.isFailure,
    )
  }

  /** The import records the entered Space's default kernel; Thesis declares only `xpython`. */
  @Test
  fun anImportRecordsTheEnteredSpacesDefaultKernel() = runTest {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer("space-1"))
    val thesis = Space("space-1", "Thesis", listOf(KernelSpec("xpython", "Python 3.13 (XPython)", "python")))
    val repositories = SpaceRepositories(mapOf(thesis.id to thesis))

    val opened = backStack.importNotebook("Imported", repositories::of, FakeSpaceServers())

    assertEquals("xpython", repositories.of("space-1").notebookRepository.readNotebook(opened!!).kernelId)
  }

  @Test
  fun anImportWhoseRunIsRefusedOpensNothing() = runTest {
    val backStack = mutableListOf<NavKey>(SpacesHome)

    val opened = backStack.importNotebook("Imported", SpaceRepositories()::of, RefusingSpaceServers())

    assertNull(opened)
    assertEquals(listOf<NavKey>(SpacesHome), backStack)
  }

  /**
   * One set of fake ports per Space id, the shape `AppContainer.portsOf` gives the real ones. A Space
   * not named in [spaces] is [spaceWithId]'s, declaring base's kernels.
   */
  private class SpaceRepositories(private val spaces: Map<String, Space> = emptyMap()) {
    private val bySpace = mutableMapOf<String, EnteredSpacePorts>()

    fun of(spaceId: String): EnteredSpacePorts = bySpace.getOrPut(spaceId) {
      EnteredSpacePorts(FakeNotebookRepository(), space = spaces[spaceId] ?: spaceWithId(spaceId))
    }
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

  /** A console row reveals the console tab already open on its path, which moves to the top. */
  @Test
  fun aRunningConsoleRowRevealsItsOpenTab() {
    val console = Console("console-1-x")
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), console, Terminal("1"))
    val opened = mutableListOf<String>()

    backStack.openRunningSession(runningRow("console-1-x", "console"), backStack.toList(), opened::add)

    assertEquals(console, backStack.last())
    assertEquals(emptyList<String>(), opened)
  }

  @Test
  fun aRunningNotebookRowOpensItsDocument() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id))
    val opened = mutableListOf<String>()

    backStack.openRunningSession(runningRow("Analysis.ipynb", "notebook"), backStack.toList(), opened::add)

    assertEquals(listOf("Analysis.ipynb"), opened)
  }

  private fun runningRow(path: String, type: String) = KernelSessionModel(
    serverSessionId = "ss1",
    kernelId = "k1",
    kernelName = "python3",
    path = path,
    name = path,
    type = type,
  )
}
