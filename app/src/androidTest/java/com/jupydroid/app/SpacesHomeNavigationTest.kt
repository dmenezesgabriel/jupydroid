package com.jupydroid.app

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * The Spaces home as the app's start destination: a cold start lands on it, base's Open enters the
 * workspace, and Back from the workspace's resting state comes back to it.
 *
 * Whether the stack holds the right keys is `SpacesHomeBackStackTest`'s. This is what only a
 * composition answers: which screen the scene strategy actually draws for that stack.
 */
@RunWith(AndroidJUnit4::class)
class SpacesHomeNavigationTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun aColdStartLandsOnTheHome() {
    startApp(twoPane = false)

    waitForText(BASE)
    compose.onNodeWithContentDescription(OPEN_BASE_SPACE).assertIsDisplayed()
    compose.onNodeWithText(LISTED_FILE).assertDoesNotExist()
  }

  @Test
  fun openingBaseShowsItsWorkspace() {
    startApp(twoPane = false)

    compose.enterBaseSpace()

    waitForText(LISTED_FILE)
    compose.onNodeWithContentDescription(OPEN_BASE_SPACE).assertDoesNotExist()
  }

  /** At one pane the browser is the workspace's resting state, so Back from it is Back to the home. */
  @Test
  fun backFromTheBrowserReturnsToTheHomeAtOnePane() {
    startApp(twoPane = false)
    compose.enterBaseSpace()
    waitForText(LISTED_FILE)

    Espresso.pressBack()

    waitForText(BASE)
    compose.onNodeWithContentDescription(OPEN_BASE_SPACE).assertIsDisplayed()
  }

  /**
   * `maybeCreate` fills an empty *main area*, and the home has none: the home is not a workspace,
   * so no launcher is drawn over it however wide the window is.
   */
  @Test
  fun noLauncherCoversTheHomeAtTwoPanes() {
    startApp(twoPane = true)

    waitForText(BASE)
    compose.waitForIdle()
    compose.onNodeWithTag(LAUNCHER_SCREEN).assertDoesNotExist()
    compose.onNodeWithContentDescription(OPEN_BASE_SPACE).assertIsDisplayed()
  }

  /**
   * The activity bar is the workspace's, so the home is drawn without it: a rail beside the home
   * would offer Files and a new launcher before any Space had been entered.
   */
  @Test
  fun theHomeHasNoActivityBarAtTwoPanes() {
    startApp(twoPane = true)

    waitForText(BASE)
    compose.onAllNodesWithContentDescription(FILES).assertCountEquals(0)
  }

  @Test
  fun theWorkspaceHasItsActivityBarAtTwoPanes() {
    startApp(twoPane = true)

    compose.enterBaseSpace()

    compose.onNodeWithContentDescription(FILES).assertIsDisplayed()
  }

  /**
   * An imported notebook opens inside base's workspace, with the browser beside it - not on the
   * bare home, where there is no list pane for it to pair with.
   */
  @Test
  fun anImportOpensInsideTheWorkspace() {
    startApp(twoPane = true, importTitle = "Imported")

    waitForText(LISTED_FILE)
    compose.onNodeWithContentDescription(OPEN_BASE_SPACE).assertDoesNotExist()
  }

  private fun startApp(twoPane: Boolean, importTitle: String? = null) {
    File(tempFolder.root, LISTED_FILE).writeText("a file for the listing")
    compose.setContent {
      JupyterDroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            notebookRepository = DiskNotebookRepository(tempFolder.root),
            workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
            launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
          ),
          importTitle = importTitle,
          onImportConsumed = {},
          twoPane = twoPane,
        )
      }
    }
  }

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private companion object {
    const val BASE = "Base"
    const val FILES = "Files"
    const val LISTED_FILE = "report.txt"
    const val LAUNCHER_SCREEN = "launcher-screen"
    const val TIMEOUT_MILLIS = 5_000L
  }
}
