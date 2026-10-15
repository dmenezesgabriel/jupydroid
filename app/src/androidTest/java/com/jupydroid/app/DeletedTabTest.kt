package com.jupydroid.app

import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import java.io.File

/**
 * A document deleted from its own tab closes that tab and no other. Two closes answer one delete -
 * the strip's own, and the screen's on hearing its file go - so the second must find nothing left
 * to close rather than the tab beneath.
 */
@RunWith(AndroidJUnit4::class)
class DeletedTabTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun deletingTheTopTabLeavesTheOneBeneath() {
    openTwoFiles()

    deleteFromItsTab("second.py")

    compose.waitUntil(TIMEOUT_MILLIS) { tabs("second.py").isEmpty() }
    compose.waitForIdle()
    compose.waitForTab("first.py")
    assertFalse(File(tempFolder.root, "second.py").exists())
  }

  @Test
  fun deletingATabBeneathLeavesTheTop() {
    openTwoFiles()

    deleteFromItsTab("first.py")

    compose.waitUntil(TIMEOUT_MILLIS) { tabs("first.py").isEmpty() }
    compose.waitForIdle()
    compose.waitForTab("second.py")
    assertFalse(File(tempFolder.root, "first.py").exists())
  }

  private fun tabs(title: String) = compose.onAllNodesWithContentDescription("Tab: $title").fetchSemanticsNodes()

  private fun deleteFromItsTab(title: String) {
    compose.onNodeWithContentDescription("Tab: $title").performTouchInput { longClick() }
    compose.onNodeWithText("Delete…").performClick()
    compose.onNode(hasText("Delete") and hasClickAction()).performClick()
  }

  private fun openTwoFiles() {
    File(tempFolder.root, "first.py").writeText("first = 1\n")
    File(tempFolder.root, "second.py").writeText("second = 2\n")
    compose.setContent {
      JupyterDroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
            enteredSpacePorts = { _, _ ->
              EnteredSpacePorts(
                notebookRepository = DiskNotebookRepository(tempFolder.root),
                workspaceRepository = DiskWorkspaceRepository(tempFolder.root),
              )
            },
          ),
          sharedNotebook = null,
          onImportConsumed = {},
          twoPane = true,
        )
      }
    }
    compose.enterBaseSpace()
    compose.waitForRow("first.py").performClick()
    compose.waitForTab("first.py")
    compose.waitForRow("second.py").performClick()
    compose.waitForTab("second.py")
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
