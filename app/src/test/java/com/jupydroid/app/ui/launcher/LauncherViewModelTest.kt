package com.jupydroid.app.ui.launcher

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.NotebookRepository
import com.jupydroid.app.domain.port.SessionType
import com.jupydroid.app.domain.port.WorkspaceFiles
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
    workspaceFiles: WorkspaceFiles =
      DiskWorkspaceFiles(workspace.root, UnconfinedTestDispatcher(testScheduler)),
    terminalGateway: FakeTerminalGateway = FakeTerminalGateway(),
    kernelGateway: RecordingKernelGateway = RecordingKernelGateway(),
    space: Space = BASE_SPACE,
    // `Private.count`'s process-global counter, per-test here - the numbering assertions want a
    // sequence no other test in this JVM has advanced.
    consoleOrdinal: () -> Int = run {
      var next = 0
      { ++next }
    },
  ): LauncherViewModel = LauncherViewModel(
    notebookRepository,
    workspaceFiles,
    terminalGateway,
    kernelGateway,
    FakeKernelSpecs(),
    space,
    consoleOrdinal,
  )

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

  /** The card records the entered Space's default kernel; Thesis declares only `xpython`, so a constant cannot pass. */
  @Test
  fun launchingANotebookRecordsTheEnteredSpacesDefaultKernel() = runTest {
    val notebooks = DiskNotebookRepository(workspace.root, ioDispatcher = UnconfinedTestDispatcher(testScheduler))
    val thesis = Space("space-1", "Thesis", listOf(KernelSpec("xpython", "Python 3.13 (XPython)", "python")))
    val viewModel = viewModel(notebookRepository = notebooks, space = thesis)

    viewModel.launch(card(LauncherCommand.NEW_NOTEBOOK), "") {}
    advanceUntilIdle()

    assertEquals("xpython", notebooks.readNotebook("Untitled.ipynb").kernelId)
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
   * console a fresh `SessionContext` at `PathExt.join(basePath, 'console-<count>-<uuid>')` and
   * titles it `Console <count>` (`packages/console/src/panel.ts:58-70`), and the kernel is
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
    assertTrue("path ${console.path} wants reports/console-1-<uuid>", console.path.startsWith("reports/console-1-"))
    assertEquals("Console 1", console.title)
    assertEquals(1, kernelGateway.connectCount)
    assertEquals(listOf(console.path), kernelGateway.connectedPaths)
    assertNotNull(console.session.opened)
  }

  /**
   * The minted session declares its kind on the POST: `SessionContext({type: 'console'})`
   * (`packages/console/src/panel.ts:71`), not the `"file"` the extension rule derives for a
   * suffixless `console-<count>-<uuid>` path. `running:kernel-open-session` reads the field to
   * pick `console:open` over `docmanager:open`, so a console minted `file` could never be
   * reached as one - its Running-panel row opened a file editor on a path that is not a file.
   */
  @Test
  fun launchingTheConsoleCardDeclaresItsSessionAConsole() = runTest {
    val kernelGateway = RecordingKernelGateway()
    val viewModel = viewModel(kernelGateway = kernelGateway)

    viewModel.launch(card(LauncherCommand.NEW_CONSOLE), "") {}
    advanceUntilIdle()

    assertEquals(listOf(SessionType.CONSOLE), kernelGateway.connectedSessionTypes)
  }

  /** A console minted inside an entered Space runs on that Space's kernel, not base's. */
  @Test
  fun theConsoleCardMintsOnTheEnteredSpace() = runTest {
    val viewModel = viewModel(space = Space(id = "space-1", name = "Thesis", kernels = BASE_SPACE.kernels))
    var launched: LaunchedDocument? = null

    viewModel.launch(card(LauncherCommand.NEW_CONSOLE), "") { launched = it }
    advanceUntilIdle()

    assertEquals("space-1", (launched as LaunchedDocument.CreatedConsole).session.opened?.spaceId)
  }

  /** `Private.count` (`panel.ts:332`) is a counter, not a random suffix: each mint numbers up. */
  @Test
  fun aSecondConsoleCardMintNumbersUp() = runTest {
    val viewModel = viewModel()
    var launched: LaunchedDocument? = null

    viewModel.launch(card(LauncherCommand.NEW_CONSOLE), "") { launched = it }
    advanceUntilIdle()
    val first = launched as LaunchedDocument.CreatedConsole
    viewModel.launch(card(LauncherCommand.NEW_CONSOLE), "") { launched = it }
    advanceUntilIdle()
    val second = launched as LaunchedDocument.CreatedConsole

    assertTrue("path ${first.path} wants console-1-<uuid>", first.path.startsWith("console-1-"))
    assertTrue("path ${second.path} wants console-2-<uuid>", second.path.startsWith("console-2-"))
    assertEquals("Console 2", second.title)
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
    val countingFiles = CountingWorkspaceFiles()
    val viewModel = viewModel(notebookRepository = blocked, workspaceFiles = countingFiles)
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
    val viewModel = viewModel(workspaceFiles = FakeWorkspaceFiles())

    viewModel.launch(card(LauncherCommand.NEW_TEXT_FILE), "") {}
    advanceUntilIdle()

    assertNotNull(viewModel.error.value)
    assertEquals(false, viewModel.pending.value)
  }

  @Test
  fun dismissingAnErrorClearsIt() = runTest {
    val viewModel = viewModel(workspaceFiles = FakeWorkspaceFiles())

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
  override suspend fun createNotebook(title: String, kernel: KernelSpec) =
    delegate.createNotebook(title, kernel).also { gate.await() }
}

/** Counts creates so "the second tap never asked" is asserted, not assumed. */
private class CountingWorkspaceFiles : WorkspaceFiles by FakeWorkspaceFiles() {
  var createFileCalls = 0
    private set

  override suspend fun createFile(directory: String, extension: String): String {
    createFileCalls++
    return "${directory}untitled.$extension".removePrefix("/")
  }
}
