package com.jupydroid.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskRecentDocumentsRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.settings.SETTINGS_SCREEN_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * Settings is reachable from the explorer's own bar, and Back returns there.
 * `TerminalNavigationTest`'s reach-then-Back shape is the precedent this mirrors.
 */
@RunWith(AndroidJUnit4::class)
class SettingsNavigationTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  @get:Rule val state = TemporaryFolder()

  @Test
  fun tappingSettingsOpensItAndBackReturnsToFiles() {
    startApp()

    compose.onNodeWithContentDescription("Settings").performClick()
    waitForTag(SETTINGS_SCREEN_TAG)

    compose.onNodeWithContentDescription("Back to files").performClick()
    waitForTag(SETTINGS_SCREEN_TAG, present = false)
    compose.onNodeWithContentDescription("Settings").assertExists()
  }

  private fun startApp() {
    compose.setContent {
      JupyterDroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
            notebookRepository = DiskNotebookRepository(temp.root),
            workspaceRepository = DiskWorkspaceRepository(temp.root),
            recentDocuments = DiskRecentDocumentsRepository(state.root, temp.root),
          ),
          importTitle = null,
          onImportConsumed = {},
          // Single pane: the explorer and Settings share the window otherwise, and "opened" would
          // already be true before the tap.
          twoPane = false,
        )
      }
    }
  }

  private fun waitForTag(tag: String, present: Boolean = true) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() == present
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
