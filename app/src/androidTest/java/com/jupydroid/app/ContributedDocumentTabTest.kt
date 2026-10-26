package com.jupydroid.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material3.Text
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskWorkspaceFiles
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.di.EnteredSpacePorts
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.extension.api.DocumentContribution
import com.jupydroid.extension.api.Extension
import com.jupydroid.extension.api.SidePanelContribution
import com.jupydroid.extensions.ai.AiExtension
import com.jupydroid.extensions.ai.data.fake.FakeChatFiles
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * A file whose suffix an extension claims opens from the explorer as that extension's tab, drawn
 * by the extension, and a second tap on the row reveals the tab rather than opening another. A chat
 * moved to a tab from the Chat panel lands in that same tab.
 */
@RunWith(AndroidJUnit4::class)
class ContributedDocumentTabTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  @Test
  fun aClaimedFileOpensAsTheExtensionsTabOnce() {
    startAppWithAClaimedFile()

    compose.waitForRow(FILE_NAME).performClick()
    compose.waitForTab(FILE_NAME)
    waitForText(BODY)
    compose.onNodeWithText(BODY).assertIsDisplayed()

    compose.listingRow(FILE_NAME).performClick()
    compose.waitForIdle()
    compose.onAllNodesWithContentDescription("Tab: $FILE_NAME").assertCountEquals(1)
  }

  /** The panel's move goes through the host's opener, which reveals the chat's tab where it is open. */
  @Test
  fun aChatMovedToATabTwiceOpensOneChatTab() {
    temp.newFile(CHAT_FILE_NAME)
    startApp(AiExtension(chatFilesFor = { FakeChatFiles(listOf(CHAT_FILE_NAME)) }))

    // Once: the panel stays shown after a move, and picking the shown panel again collapses it.
    compose.onNodeWithContentDescription("Chat").performClick()
    repeat(2) {
      compose.onNodeWithText("untitled").performClick()
      compose.onNodeWithContentDescription("Move to tab").performClick()
      compose.waitForTab(CHAT_FILE_NAME)
    }

    compose.onAllNodesWithContentDescription("Tab: $CHAT_FILE_NAME").assertCountEquals(1)
  }

  private fun startAppWithAClaimedFile() {
    temp.newFile(FILE_NAME).writeText("{}")
    startApp(DemoDocumentExtension())
  }

  private fun startApp(extension: Extension) {
    compose.setContent {
      // Wide enough for the explorer beside the tab, so the row is still there to tap again.
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(DpSize(1280.dp, 800.dp))) {
        JupyterDroidTheme {
          MainNavigation(
            appContainer = AppContainer(
              launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
              extensions = listOf(extension),
              enteredSpacePorts = { _, _ ->
                EnteredSpacePorts(
                  notebookRepository = DiskNotebookRepository(temp.root),
                  workspaceRepository = DiskWorkspaceRepository(temp.root),
                  workspaceFiles = DiskWorkspaceFiles(temp.root),
                )
              },
            ),
            sharedNotebook = null,
            onImportConsumed = {},
            twoPane = true,
          )
        }
      }
    }
    compose.enterBaseSpace()
  }

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
  }

  private class DemoDocumentExtension : Extension {
    override val sidePanels = emptyList<SidePanelContribution>()
    override val documents = listOf(
      DocumentContribution("demo.document", ".demo", Icons.Filled.AccountTree) { path, _ -> Text("$BODY_PREFIX$path") },
    )
  }

  private companion object {
    const val FILE_NAME = "notes.demo"
    const val CHAT_FILE_NAME = "untitled.chat"
    const val BODY_PREFIX = "Demo document: "
    const val BODY = "$BODY_PREFIX$FILE_NAME"
    const val TIMEOUT_MILLIS = 10_000L
  }
}
