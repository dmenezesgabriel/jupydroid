package com.jupydroid.app

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.launcher.LAUNCHER_CARD_TAG
import com.jupydroid.app.ui.launcher.LauncherCommand
import com.jupydroid.app.ui.shell.NEW_LAUNCHER
import com.jupydroid.app.ui.terminal.TERMINAL_INPUT_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * The launcher's reach and its cards' landings, end to end: the bottom-bar `+`, a card tap, and
 * what is on screen after - `ConsoleNavigationTest`'s own shape, because what is showing after a
 * push or a pop is not a question a JVM test can answer.
 *
 * One pane, pinned (`twoPane = false`), for the same reason [ConsoleNavigationTest] pins it:
 * `currentWindowAdaptiveInfo()` reads the real device rotation, and one-pane is where the bar and
 * the back-arrow wording live.
 */
@RunWith(AndroidJUnit4::class)
class LauncherNavigationTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  private val terminals = FakeTerminalGateway()

  @Test
  fun theBarItemOpensTheLauncherAndBackReturnsToTheBrowser() {
    startApp()

    compose.onNodeWithContentDescription(NEW_LAUNCHER).performClick()
    waitForTag("launcher-screen")
    compose.onNodeWithText("Terminal").assertIsDisplayed()

    compose.onNodeWithContentDescription("Back to files").performClick()
    // Back on the browser, not out of the app - the Files row proves which surface returned.
    waitForText("report.txt")
    listingRow("report.txt").assertIsDisplayed()
  }

  @Test
  fun theNotebookCardCreatesAndOpensANotebook() {
    startApp()

    compose.onNodeWithContentDescription(NEW_LAUNCHER).performClick()
    waitForTag("launcher-screen")
    compose.onNodeWithTag("$LAUNCHER_CARD_TAG-${LauncherCommand.NEW_NOTEBOOK.name}").performClick()

    // The created notebook is open in the editor - named by its tab now, not a panel row - and
    // it exists on disk, which the disk repository made true rather than a fake merely agreeing.
    waitForTab("Untitled.ipynb")
    assertEquals(true, File(tempFolder.root, "Untitled.ipynb").exists())
  }

  /**
   * The cwd end to end: browsing into a folder, then launching a terminal, roots the shell there -
   * `commands.execute(item.command, {cwd: launcher.cwd})` with the path the explorer reported.
   */
  @Test
  fun theTerminalCardOpensAShellInTheBrowsedDirectory() {
    File(tempFolder.root, "reports").mkdirs()
    startApp()

    folderRow("reports").performClick()
    compose.onNodeWithContentDescription(NEW_LAUNCHER).performClick()
    waitForTag("launcher-screen")
    compose.onNodeWithText("reports").assertIsDisplayed()
    compose.onNodeWithTag("$LAUNCHER_CARD_TAG-${LauncherCommand.NEW_TERMINAL.name}").performClick()

    waitForTag(TERMINAL_INPUT_TAG)
    assertEquals(listOf("reports"), terminals.openedIn)
  }

  private fun startApp() {
    val repository = DiskNotebookRepository(tempFolder.root)
    File(tempFolder.root, "report.txt").writeText("a file for the listing")
    compose.setContent {
      JupydroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
            enteredSpacePorts = { _, activeTerminals ->
              EnteredSpacePorts(
                notebookRepository = repository,
                workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
                workspaceFiles = DiskWorkspaceFiles(tempFolder.root),
                terminalGateway = terminals,
                activeTerminals = activeTerminals,
              )
            },
          ),
          sharedNotebook = null,
          onImportConsumed = {},
          twoPane = false,
        )
      }
    }
    compose.enterBaseSpace()
    waitForText("report.txt")
  }

  private fun listingRow(title: String) = compose.onNode(hasText(title) and hasContentDescription("File"))

  private fun folderRow(title: String) = compose.onNode(hasText(title) and hasContentDescription("Folder"))

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private fun waitForTab(title: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithContentDescription("Tab: $title").fetchSemanticsNodes().isNotEmpty()
  }

  private fun waitForTag(tag: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
