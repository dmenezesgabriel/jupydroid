package com.jupydroid.app.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Explorer
import com.jupydroid.app.FileEditor
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.fake.FakeDocumentCheckpoints
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.mainAreaTabs
import com.jupydroid.app.theme.JupydroidTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Revert to Checkpoint from the tab's menu, and the checkpoint the strip's Save makes for it -
 * jupyterlab@7f0e690's `CommandIDs.restoreCheckpoint` and the `save` command's `createCheckpoint()`
 * (`packages/docmanager-extension/src/index.tsx`).
 */
@RunWith(AndroidJUnit4::class)
class TabRevertToCheckpointTest {
  @get:Rule val compose = createComposeRule()

  private val backStack = mutableStateListOf<NavKey>(Explorer(BASE_SPACE.id))
  private val open = OpenDocuments()
  private val document = FakeTabDocument("notes.py", isDirty = true)
  private val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.py" to "saved"))
  private val checkpoints = FakeDocumentCheckpoints(workspace)
  private val paths = mutableStateMapOf<NavKey, String>()

  private fun strip() {
    open.opened(document)
    backStack.add(FileEditor("notes.py"))
    compose.setContent {
      JupydroidTheme {
        TabbedDocumentPane(
          backStack = backStack,
          openDocuments = open,
          openDocumentPaths = paths,
          tabs = backStack.mainAreaTabs(),
          liveTabTitles = emptyMap(),
          workspaceRepository = workspace,
          workspaceFiles = FakeWorkspaceFiles(),
          onCloseTab = {},
          onOpenDocument = {},
          documentCheckpoints = checkpoints,
        ) { Text("the document") }
      }
    }
  }

  private fun revertFromTheMenu() {
    compose.onNodeWithContentDescription("Tab: notes.py").performTouchInput { longClick() }
    compose.onNodeWithText("Revert to Checkpoint…").performClick()
  }

  private fun waitFor(text: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

  @Test
  fun aFileWithNoCheckpointIsToldSo() {
    strip()

    revertFromTheMenu()

    waitFor("No checkpoints")
    compose.onNodeWithText("No checkpoints are available for this File.").assertIsDisplayed()
  }

  /** Asked first, in the warning colour, and only then restored and read back. */
  @Test
  fun aConfirmedRevertRestoresTheCheckpointAndReadsItBack() {
    runBlocking {
      checkpoints.create("notes.py")
      workspace.writeText("notes.py", "edited since")
    }
    strip()

    revertFromTheMenu()
    waitFor("Revert File to checkpoint")
    compose.onNodeWithText("This cannot be undone.").assertIsDisplayed()
    compose.onNodeWithText("Revert").performClick()

    compose.waitUntil(5_000) { document.reloads == 1 }
    assertEquals("saved", runBlocking { workspace.readText("notes.py") })
  }

  /** Cancel leaves the file and the document as they were. */
  @Test
  fun aCancelledRevertTouchesNothing() {
    runBlocking {
      checkpoints.create("notes.py")
      workspace.writeText("notes.py", "edited since")
    }
    strip()

    revertFromTheMenu()
    waitFor("Revert File to checkpoint")
    compose.onNodeWithText("Cancel").performClick()

    assertEquals(0, document.reloads)
    assertEquals("edited since", runBlocking { workspace.readText("notes.py") })
  }

  /** The strip's Save is the user's own, so it moves the checkpoint. */
  @Test
  fun theStripsSaveMakesACheckpoint() {
    strip()

    compose.onNodeWithContentDescription("Save tab: notes.py").performClick()

    compose.waitUntil(5_000) { runBlocking { checkpoints.list("notes.py") }.isNotEmpty() }
  }
}
