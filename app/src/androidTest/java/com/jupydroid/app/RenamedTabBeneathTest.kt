package com.jupydroid.app

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
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
import com.jupydroid.app.ui.launcher.LAUNCHER_CARD_TAG
import com.jupydroid.app.ui.launcher.LauncherCommand
import com.jupydroid.app.ui.terminal.TERMINAL_INPUT_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * A document's tab follows a rename made while another tab is on top - `title.label` is a
 * subscriber of the context's `pathChanged` (`packages/docregistry/src/default.ts`), so a tab
 * that is not the current widget is retitled as surely as one that is.
 *
 * A terminal on top is the case that was measured: its screen is no document, so nothing it draws
 * recomposes the strip once the file's ViewModel adopts the new path.
 */
@RunWith(AndroidJUnit4::class)
class RenamedTabBeneathTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun aFileRenamedBeneathATerminalTakesItsNewName() {
    File(tempFolder.root, "second.py").writeText("second = 1\n")
    startApp()

    compose.waitForRow("second.py").performClick()
    compose.waitForTab("second.py")
    compose.onNodeWithContentDescription("Tab: Launcher").performClick()
    compose.onNodeWithTag("$LAUNCHER_CARD_TAG-${LauncherCommand.NEW_TERMINAL.name}").performClick()
    compose.waitUntil(TIMEOUT_MILLIS) {
      compose.onAllNodesWithTag(TERMINAL_INPUT_TAG).fetchSemanticsNodes().isNotEmpty()
    }
    compose.renameTab(from = "second.py", to = "renamed.py")

    compose.waitForTab("renamed.py")
    compose.onNodeWithContentDescription("Tab: second.py").assertDoesNotExist()
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
            // Two panes, so the launcher's Terminal card is the reach and the strip is beside the list.
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
