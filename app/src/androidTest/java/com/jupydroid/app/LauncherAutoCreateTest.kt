package com.jupydroid.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.launcher.LAUNCHER_CARD_TAG
import com.jupydroid.app.ui.launcher.LauncherCommand
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * `maybeCreate` - `labShell.layoutModified.connect(() => if (labShell.isEmpty('main')) create())`
 * (`launcher-extension/src/index.ts:147-157`) - and the first-run flag beside it. The reference
 * fills an empty main area on every layout change; at two panes that is this app's rule too, and
 * at one pane the push happens on the first run only.
 *
 * These are presence assertions on the navigation itself, which is what a JVM test cannot answer:
 * whether the entry is on screen after a pop, not whether the stack changed.
 */
@RunWith(AndroidJUnit4::class)
class LauncherAutoCreateTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  /** An empty main area draws a launcher, unprompted - `maybeCreate`'s own condition. */
  @Test
  fun theLauncherFillsAnEmptyMainAreaAtTwoPanes() {
    startApp(twoPane = true, launcherSeen = true)

    waitForTag("launcher-screen")
    // The kernel card - "Python 3" is on two cards (Notebook and Console), so a count, not a node.
    compose.onAllNodesWithText("Python 3").assertCountEquals(2)
  }

  /**
   * Closing the last document is a `layoutModified` too - the launcher comes back the same way it
   * first arrived, rather than leaving the detail area blank.
   */
  @Test
  fun closingTheLastDocumentRefillsTheMainAreaAtTwoPanes() {
    startApp(twoPane = true, launcherSeen = true)
    waitForTag("launcher-screen")
    compose.onNodeWithTag("$LAUNCHER_CARD_TAG-${LauncherCommand.NEW_TEXT_FILE.name}").performClick()
    // The tab is what announces the open document - its title's home (`Tab: <name>` is
    // a description, not a text node, so `waitForText` cannot see it).
    waitForDescription("Tab: untitled.txt")

    // The tab's ✕ is the close now - no control survives inside the pane at two panes.
    compose.onNodeWithContentDescription("Close tab: untitled.txt").performClick()

    waitForTag("launcher-screen")
    compose.onAllNodesWithText("Python 3").assertCountEquals(2)
  }

  /**
   * `main.title.closable = widgets('main').length > 1` (index.ts:126): alone in the main area, the
   * launcher gets no close affordance. And Back leaves the workspace for the Spaces home rather than
   * popping the launcher alone - that pop would re-trigger `maybeCreate` and put it straight back.
   */
  @Test
  fun aLoneLauncherCannotBeClosedAndBackLeavesTheWorkspaceAtTwoPanes() {
    startApp(twoPane = true, launcherSeen = true)
    waitForTag("launcher-screen")

    // The lone launcher's tab is `closable = widgets('main').length > 1` - no ✕ on it either.
    compose.onAllNodesWithContentDescription("Close tab: Launcher").assertCountEquals(0)
    Espresso.pressBack()

    compose.onNodeWithContentDescription(OPEN_BASE_SPACE).assertIsDisplayed()
    compose.onNodeWithTag("launcher-screen").assertDoesNotExist()
  }

  /** The one-pane half of the decision: first run only, and the push is what marks it seen. */
  @Test
  fun theFirstRunPushesTheLauncherAtOnePaneAndMarksItSeen() {
    val settings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = false))
    startApp(twoPane = false, settings = settings)

    waitForTag("launcher-screen")
    runTest { assertEquals(true, settings.observeSettings().first().launcherSeen) }
  }

  /** A returning user lands on the browser - the flag, not the device, is what is asked. */
  @Test
  fun aReturningUserLandsOnFilesAtOnePane() {
    startApp(twoPane = false, launcherSeen = true)

    waitForText("report.txt")
    compose.onNodeWithTag("launcher-screen").assertDoesNotExist()
  }

  private fun startApp(
    twoPane: Boolean,
    launcherSeen: Boolean = true,
    settings: FakeLauncherSettingsRepository = FakeLauncherSettingsRepository(
      LauncherSettings(launcherSeen = launcherSeen),
    ),
  ) {
    val repository = DiskNotebookRepository(tempFolder.root)
    File(tempFolder.root, "report.txt").writeText("a file for the listing")
    compose.setContent {
      JupyterDroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            enteredSpacePorts = { _, _ ->
              EnteredSpacePorts(
                notebookRepository = repository,
                workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
              )
            },
            launcherSettings = settings,
          ),
          importTitle = null,
          onImportConsumed = {},
          twoPane = twoPane,
        )
      }
    }
    compose.enterBaseSpace()
  }

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private fun waitForTag(tag: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
  }

  private fun waitForDescription(description: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
