package com.jupydroid.app

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * A file recreated at the name an open tab was renamed away from opens a tab of its own.
 *
 * `openOrReveal` matches a widget by the path its context lives at now
 * (jupyterlab@7f0e6906 `packages/docmanager/src/manager.ts`, `_contextsForPath`), so the renamed
 * tab no longer answers to its old name. The composition-level half of `RecreatedPathOpeningTest`:
 * the row's tap, the strip, and each editor showing its own file.
 */
@RunWith(AndroidJUnit4::class)
class RecreatedPathTabTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun aFileRecreatedAtARenamedTabsOldNameOpensBesideIt() {
    File(tempFolder.root, "first.py").writeText("old = 1\n")
    startApp()
    compose.waitForRow("first.py").performClick()
    compose.waitForTab("first.py")
    compose.renameTab(from = "first.py", to = "renamed.py")
    compose.waitForTab("renamed.py")

    File(tempFolder.root, "first.py").writeText("new = 2\n")
    compose.waitForRow("first.py").performClick()

    compose.waitForTab("first.py")
    compose.onNodeWithContentDescription("Tab: renamed.py").assertExists()
    compose.waitForText("new = 2")
    compose.onNodeWithContentDescription("Tab: renamed.py").performClick()
    compose.waitForText("old = 1")
  }

  private fun ComposeTestRule.waitForText(text: String) = waitUntil(TIMEOUT_MILLIS) {
    onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty()
  }

  private fun startApp() {
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(windowFor(twoPane = true))) {
        JupydroidTheme {
          MainNavigation(
            appContainer = AppContainer(
              launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
              enteredSpacePorts = { _, _ ->
                EnteredSpacePorts(
                  notebookRepository = DiskNotebookRepository(tempFolder.root),
                  workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
                  workspaceFiles = DiskWorkspaceFiles(tempFolder.root),
                )
              },
            ),
            sharedNotebook = null,
            onImportConsumed = {},
            // Two panes, so the explorer's row stays hittable beside the open document.
            twoPane = true,
          )
        }
      }
    }
    compose.enterBaseSpace()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
