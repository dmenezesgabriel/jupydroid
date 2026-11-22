package com.jupydroid.app.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
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
import com.jupydroid.app.domain.port.WorkspaceFiles
import com.jupydroid.app.mainAreaTabs
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Delete from the tab's menu - jupyterlab@7f0e690's `docmanager:delete`, bound to the tab through
 * `data-type="document-title"` (`packages/docmanager-extension/schema/plugin.json`): asked first,
 * then `deleteFile`, whose `deleteWidgets` disposes the document's widgets without the unsaved-work
 * ask (`packages/docmanager/src/manager.ts`, `widgetmanager.ts`).
 */
@RunWith(AndroidJUnit4::class)
class TabDeleteTest {
  @get:Rule val compose = createComposeRule()

  /** Records each delete, or refuses it the way the disk refuses a folder with something in it. */
  private class DeletingFiles : WorkspaceFiles by FakeWorkspaceFiles() {
    val deletes = mutableListOf<String>()
    var deleteRefusal: Throwable? = null

    override suspend fun delete(path: String) {
      deleteRefusal?.let { throw it }
      deletes += path
    }
  }

  private val backStack = mutableStateListOf<NavKey>(Explorer(BASE_SPACE.id))
  private val open = OpenDocuments()
  private val notes = FakeTabDocument("notes.py", isDirty = true)
  private val other = FakeTabDocument("other.py")
  private val workspace = DeletingFiles()
  private val paths = mutableStateMapOf<NavKey, String>()

  private fun strip() {
    open.opened(notes)
    open.opened(other)
    backStack.add(FileEditor("notes.py"))
    backStack.add(FileEditor("other.py"))
    compose.setContent {
      JupydroidTheme {
        TabbedDocumentPane(
          backStack = backStack,
          openDocuments = open,
          openDocumentPaths = paths,
          tabs = backStack.mainAreaTabs(),
          liveTabTitles = emptyMap(),
          workspaceRepository = FakeWorkspaceRepository(),
          workspaceFiles = workspace,
          onCloseTab = {},
          onOpenDocument = {},
          documentCheckpoints = FakeDocumentCheckpoints(),
        ) { Text("the document") }
      }
    }
  }

  private fun deleteFromTheMenu(title: String) {
    compose.tabInReach(title).performTouchInput { longClick() }
    compose.onNodeWithText("Delete…").performClick()
  }

  private fun waitFor(text: String) =
    compose.waitUntil(5_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }

  /** The ask names the file and says permanently, as the browser's own delete does. */
  @Test
  fun deleteIsAskedInTheBrowsersWords() {
    strip()

    deleteFromTheMenu("other.py")

    compose.onNodeWithText("Are you sure you want to permanently delete: other.py?").assertIsDisplayed()
  }

  /** A tab beneath the top is deleted and closed, unasked about its unsaved work, and the top stays. */
  @Test
  fun aConfirmedDeleteRemovesTheFileAndClosesItsTab() {
    strip()

    deleteFromTheMenu("notes.py")
    compose.onNode(hasText("Delete") and hasClickAction()).performClick()

    compose.waitUntil(5_000) { FileEditor("notes.py") !in backStack }
    assertEquals(listOf("notes.py"), workspace.deletes)
    assertEquals(1, notes.discards)
    assertEquals(FileEditor("other.py"), backStack.last())
  }

  @Test
  fun aCancelledDeleteTouchesNothing() {
    strip()

    deleteFromTheMenu("notes.py")
    compose.onNodeWithText("Cancel").performClick()

    assertTrue(workspace.deletes.isEmpty())
    assertTrue(FileEditor("notes.py") in backStack)
    assertEquals(0, notes.discards)
  }

  /** `deleteFile` reaches the console alone in the reference; on a device that is silence, so it is said. */
  @Test
  fun aRefusedDeleteSaysWhyAndKeepsTheTab() {
    workspace.deleteRefusal = IllegalStateException("could not delete 'notes.py' from the workspace")
    strip()

    deleteFromTheMenu("notes.py")
    compose.onNode(hasText("Delete") and hasClickAction()).performClick()

    waitFor("Delete Error")
    compose.onNodeWithText("could not delete 'notes.py' from the workspace").assertIsDisplayed()
    assertTrue(FileEditor("notes.py") in backStack)
    assertEquals(0, notes.discards)
  }
}
