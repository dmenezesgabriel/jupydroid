package com.jupydroid.app.ui.launcher

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.ui.editor.RecordingKernelGateway
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * What a launcher card does when tapped - the reference's `commands.execute(item.command,
 * {cwd: launcher.cwd})` (`packages/launcher/src/widget.tsx:173,255`). The real repositories are
 * Disk ones over a [TemporaryFolder], so "created in the browsed directory" is asserted by the
 * file actually being there, not by a fake's own record of it.
 */
@kotlinx.coroutines.ExperimentalCoroutinesApi
class LauncherViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @get:Rule val workspace = TemporaryFolder()

  private fun TestScope.viewModel(
    notebookRepository: NotebookRepository =
      DiskNotebookRepository(workspace.root, ioDispatcher = UnconfinedTestDispatcher(testScheduler)),
    workspaceRepository: WorkspaceRepository =
      DiskWorkspaceRepository(workspace.root, UnconfinedTestDispatcher(testScheduler)),
    terminalGateway: FakeTerminalGateway = FakeTerminalGateway(),
    kernelGateway: RecordingKernelGateway = RecordingKernelGateway(),
  ): LauncherViewModel = LauncherViewModel(notebookRepository, workspaceRepository, terminalGateway, kernelGateway)

  private fun card(command: LauncherCommand): LauncherItem = LAUNCHER_ITEMS.single { it.command == command }

  @Test
  fun launchingTheNotebookCardCreatesANotebookInTheBrowsedDirectory() = runTest {
    workspace.newFolder("reports")
    val viewModel = viewModel()
    var launched: LaunchedDocument? = null

    viewModel.launch(card(LauncherCommand.NEW_NOTEBOOK), "reports") { launched = it }
    advanceUntilIdle()

    assertEquals(LaunchedDocument.CreatedNotebook("reports/Untitled.ipynb"), launched)
    assertTrue(File(workspace.root, "reports/Untitled.ipynb").exists())
  }

  @Test
  fun launchingANotebookAtTheRootJoinsNoDirectory() = runTest {
    val viewModel = viewModel()
    var launched: LaunchedDocument? = null

    viewModel.launch(card(LauncherCommand.NEW_NOTEBOOK), "") { launched = it }
    advanceUntilIdle()

    assertEquals(LaunchedDocument.CreatedNotebook("Untitled.ipynb"), launched)
    assertTrue(File(workspace.root, "Untitled.ipynb").exists())
  }

  @Test
  fun theThreeFileCardsCreateTheirOwnExtensionInTheBrowsedDirectory() = runTest {
    workspace.newFolder("reports")
    val viewModel = viewModel()
    val launched = mutableListOf<LaunchedDocument>()
    val creates = mapOf(
      LauncherCommand.NEW_TEXT_FILE to "untitled.txt",
      LauncherCommand.NEW_MARKDOWN_FILE to "untitled.md",
      LauncherCommand.NEW_PYTHON_FILE to "untitled.py",
    )

    creates.forEach { (command, name) ->
      viewModel.launch(card(command), "reports") { launched += it }
      advanceUntilIdle()
      assertTrue(File(workspace.root, "reports/$name").exists())
    }

    assertEquals(
      creates.values.map { LaunchedDocument.CreatedFile("reports/$it") },
      launched,
    )
  }

  @Test
  fun launchingTheTerminalCardOpensAShellAtTheBrowsedDirectory() = runTest {
    val terminalGateway = FakeTerminalGateway()
    val viewModel = viewModel(terminalGateway = terminalGateway)
    var launched: LaunchedDocument? = null

    viewModel.launch(card(LauncherCommand.NEW_TERMINAL), "reports") { launched = it }
    advanceUntilIdle()

    assertEquals(listOf("reports"), terminalGateway.openedIn)
    assertEquals(LaunchedDocument.OpenedTerminal("1"), launched)
  }

  /** An empty cwd is the workspace root, which `terminalGateway.open` spells as null. */
  @Test
  fun launchingTheTerminalCardAtTheRootOpensAShellWithoutACwd() = runTest {
    val terminalGateway = FakeTerminalGateway()
    val viewModel = viewModel(terminalGateway = terminalGateway)

    viewModel.launch(card(LauncherCommand.NEW_TERMINAL), "") {}
    advanceUntilIdle()

    assertEquals(listOf(null), terminalGateway.openedIn)
  }

  /**
   * The console card mints its own kernel session - the reference's `console:create` gives the
   * console a fresh `SessionContext` at `PathExt.join(cwd, 'console-<uuid>')`, and the kernel is
   * connected at create time (`sessionContext.initialize()`), not on the first submission.
   */
  @Test
  fun launchingTheConsoleCardMintsAndOpensItsOwnSession() = runTest {
    val kernelGateway = RecordingKernelGateway()
    val viewModel = viewModel(kernelGateway = kernelGateway)
    var launched: LaunchedDocument? = null

    viewModel.launch(card(LauncherCommand.NEW_CONSOLE), "reports") { launched = it }
    advanceUntilIdle()

    val console = launched as LaunchedDocument.CreatedConsole
    assertTrue(console.path.startsWith("reports/console-"))
    assertEquals(1, kernelGateway.connectCount)
    assertEquals(listOf(console.path), kernelGateway.connectedPaths)
    assertNotNull(console.session.opened)
  }

  /**
   * The reference's `_pending` flag (`packages/launcher/src/widget.tsx:254-260`): a launch in
   * flight swallows further taps until it settles - the card container never re-fires.
   */
  @Test
  fun aSecondLaunchWhileOneIsPendingDoesNothing() = runTest {
    val gate = CompletableDeferred<Unit>()
    val blocked = GatedNotebookRepository(
      DiskNotebookRepository(workspace.root, ioDispatcher = UnconfinedTestDispatcher(testScheduler)),
      gate,
    )
    val countingFiles = CountingWorkspaceRepository()
    val viewModel = viewModel(notebookRepository = blocked, workspaceRepository = countingFiles)
    val launched = mutableListOf<LaunchedDocument>()

    viewModel.launch(card(LauncherCommand.NEW_NOTEBOOK), "") { launched += it }
    runCurrent()
    assertTrue(viewModel.pending.value)

    viewModel.launch(card(LauncherCommand.NEW_TEXT_FILE), "") { launched += it }
    runCurrent()
    assertEquals(0, countingFiles.createFileCalls)

    gate.complete(Unit)
    advanceUntilIdle()
    assertEquals(listOf(LaunchedDocument.CreatedNotebook("Untitled.ipynb")), launched)
    assertEquals(false, viewModel.pending.value)
  }

  /** A failed create surfaces - the reference's `showErrorMessage` - rather than vanishing. */
  @Test
  fun aFailedLaunchSurfacesTheError() = runTest {
    val viewModel = viewModel(workspaceRepository = FakeWorkspaceRepository())

    viewModel.launch(card(LauncherCommand.NEW_TEXT_FILE), "") {}
    advanceUntilIdle()

    assertNotNull(viewModel.error.value)
    assertEquals(false, viewModel.pending.value)
  }

  @Test
  fun dismissingAnErrorClearsIt() = runTest {
    val viewModel = viewModel(workspaceRepository = FakeWorkspaceRepository())

    viewModel.launch(card(LauncherCommand.NEW_TEXT_FILE), "") {}
    advanceUntilIdle()
    viewModel.dismissError()

    assertNull(viewModel.error.value)
  }
}

/**
 * A [NotebookRepository] whose create suspends on [gate] - the named fake a "still in flight"
 * launch needs, since the disk repository answers faster than a second tap can be asked.
 */
private class GatedNotebookRepository(private val delegate: NotebookRepository, val gate: CompletableDeferred<Unit>) :
  NotebookRepository by delegate {
  override suspend fun createNotebook(title: String, kernelId: String) =
    delegate.createNotebook(title, kernelId).also { gate.await() }
}

/** Counts creates so "the second tap never asked" is asserted, not assumed. */
private class CountingWorkspaceRepository : WorkspaceRepository by FakeWorkspaceRepository() {
  var createFileCalls = 0
    private set

  override suspend fun createFile(directory: String, extension: String): String {
    createFileCalls++
    return "${directory}untitled.$extension".removePrefix("/")
  }
}
