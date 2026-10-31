package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeSharedDocumentReader
import com.jupydroid.app.data.fake.FakeSpaceServers
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.model.spaceWithId
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.SpaceServers
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import com.jupydroid.app.import.SharedNotebook
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
import java.time.Instant

/**
 * [openNewTerminal] - `rememberNewTerminal`'s mint-and-reveal half - where the composable's own
 * reach is `TerminalNavigationTest`'s. The refused half is what matters: the launcher card's own
 * path reports through `LauncherViewModel.error`, and this reach has no error surface, so a
 * refused `open` must land as *no terminal opened* rather than escaping the launch it runs in -
 * the same `runCatching` the VIEW intent gives its copy in `DocumentOpeners.kt`.
 *
 * And [importNotebook], the VIEW intent's act: which Space it lands in, that the shared file's own
 * bytes are what land, under a name nothing holds, and that a refusal opens nothing.
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

    val opened = backStack.importNotebook(SHARED, repositories::of, servers, sharedReader())

    assertEquals("Shared analysis.ipynb", opened)
    assertEquals(setOf(BASE_SPACE.id), servers.running.value)
    assertEquals(listOf(SpacesHome, Explorer(BASE_SPACE.id)), backStack)
  }

  /** Inside a workspace an import stays in the Space already entered, rather than switching to base. */
  @Test
  fun anImportInsideAWorkspaceStaysInTheEnteredSpace() = runTest {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer("space-1"))
    val servers = FakeSpaceServers()
    val repositories = SpaceRepositories()

    val opened = backStack.importNotebook(SHARED, repositories::of, servers, sharedReader())

    assertNotNull(opened)
    assertEquals(emptySet<String>(), servers.running.value)
    assertEquals(listOf(SpacesHome, Explorer("space-1")), backStack)
    assertTrue(
      "written into base too",
      runCatching { repositories.of(BASE_SPACE.id).workspaceRepository.readBytes(opened!!) }.isFailure,
    )
  }

  /** The shared file's own bytes, not a notebook rebuilt from what this app models of them. */
  @Test
  fun anImportLandsTheSharedFilesBytesAsTheyAre() = runTest {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer("space-1"))
    val repositories = SpaceRepositories()

    val opened = backStack.importNotebook(SHARED, repositories::of, FakeSpaceServers(), sharedReader())

    assertEquals(SHARED_JSON, repositories.of("space-1").workspaceRepository.readText(opened!!))
  }

  /** A taken name is counted past, as `createNotebook` counts, so the user's own file is left alone. */
  @Test
  fun anImportUnderATakenNameIsIncrementedAndLeavesTheTakenFileAlone() = runTest {
    val backStack = mutableListOf<NavKey>(SpacesHome, Explorer("space-1"))
    val repositories = SpaceRepositories(taken = "Shared analysis.ipynb")

    val opened = backStack.importNotebook(SHARED, repositories::of, FakeSpaceServers(), sharedReader())

    val workspace = repositories.of("space-1").workspaceRepository
    assertEquals("Shared analysis1.ipynb", opened)
    assertEquals(SHARED_JSON, workspace.readText(opened!!))
    assertEquals(TAKEN_JSON, workspace.readText("Shared analysis.ipynb"))
  }

  @Test
  fun anImportWhoseFileCannotBeReadOpensNothing() = runTest {
    val backStack = mutableListOf<NavKey>(SpacesHome)
    val servers = FakeSpaceServers()

    val opened = backStack.importNotebook(SHARED, SpaceRepositories()::of, servers, FakeSharedDocumentReader())

    assertNull(opened)
    assertEquals(listOf<NavKey>(SpacesHome), backStack)
    assertEquals("a file that cannot be read starts no Space", emptySet<String>(), servers.running.value)
  }

  @Test
  fun anImportOverTheLimitOpensNothingAndSaysWhichFileAndTheLimit() = runTest {
    val backStack = mutableListOf<NavKey>(SpacesHome)
    val servers = FakeSpaceServers()
    val reader = FakeSharedDocumentReader(mapOf(SHARED.uri to ByteArray(11)), limitBytes = 10)
    val reports = mutableListOf<String>()

    val opened = backStack.importNotebook(SHARED, SpaceRepositories()::of, servers, reader, reports::add)

    assertNull(opened)
    assertEquals(listOf<NavKey>(SpacesHome), backStack)
    assertEquals("a refused file starts no Space", emptySet<String>(), servers.running.value)
    assertEquals(listOf("${SHARED.fileName} is over the 10 B limit for an import"), reports)
  }

  @Test
  fun anImportWhoseRunIsRefusedOpensNothing() = runTest {
    val backStack = mutableListOf<NavKey>(SpacesHome)

    val opened = backStack.importNotebook(SHARED, SpaceRepositories()::of, RefusingSpaceServers(), sharedReader())

    assertNull(opened)
    assertEquals(listOf<NavKey>(SpacesHome), backStack)
  }

  private fun sharedReader() = FakeSharedDocumentReader(mapOf(SHARED.uri to SHARED_JSON.toByteArray()))

  /**
   * One set of fake ports per Space id, the shape `AppContainer.portsOf` gives the real ones. A Space
   * is [spaceWithId]'s; [taken] names a file every Space's workspace root already holds.
   */
  private class SpaceRepositories(private val taken: String? = null) {
    private val bySpace = mutableMapOf<String, EnteredSpacePorts>()

    fun of(spaceId: String): EnteredSpacePorts = bySpace.getOrPut(spaceId) {
      EnteredSpacePorts(FakeNotebookRepository(), workspaceRepository = workspace(), space = spaceWithId(spaceId))
    }

    private fun workspace(): FakeWorkspaceRepository {
      val name = taken ?: return FakeWorkspaceRepository()
      val entry = WorkspaceEntry(name, name, WorkspaceEntryKind.FILE, Instant.EPOCH)
      return FakeWorkspaceRepository(mapOf("" to listOf(entry)), mapOf(name to TAKEN_JSON))
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

private val SHARED = SharedNotebook("content://media/external/file/7", "Shared analysis.ipynb")

private const val SHARED_JSON = """{"cells": [{"cell_type": "markdown", "source": "# Shared"}], "custom": 1}"""

private const val TAKEN_JSON = """{"cells": []}"""
