package com.jupydroid.app.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
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
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Duplicate from the tab's menu - jupyterlab@7f0e690's `docmanager:duplicate`, ranked just before
 * delete on the `document-title` context menu (`packages/docmanager-extension/schema/plugin.json`):
 * `DocumentManager.duplicate` copies the file beside itself and opens nothing
 * (`packages/docmanager/src/manager.ts`).
 */
@RunWith(AndroidJUnit4::class)
class TabDuplicateTest {
  @get:Rule val compose = createComposeRule()

  /** Records each copy, or refuses it the way the disk refuses a file that is no longer there. */
  private class DuplicatingFiles : WorkspaceFiles by FakeWorkspaceFiles() {
    val copies = mutableListOf<String>()
    var copyRefusal: Throwable? = null

    override suspend fun copy(path: String): String {
      copyRefusal?.let { throw it }
      copies += path
      return path.replace(".py", "-Copy1.py")
    }
  }

  private val backStack = mutableStateListOf<NavKey>(Explorer(BASE_SPACE.id))
  private val open = OpenDocuments()
  private val workspace = DuplicatingFiles()
  private val paths = mutableStateMapOf<NavKey, String>()

  private fun strip() {
    open.opened(FakeTabDocument("notes.py"))
    open.opened(FakeTabDocument("other.py"))
    backStack.add(FileEditor("notes.py"))
    backStack.add(FileEditor("other.py"))
    compose.setContent {
      JupyterDroidTheme {
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

  private fun openTheMenu(title: String) = compose.tabInReach(title).performTouchInput { longClick() }

  /** Duplicate 21, delete 22 in the reference's ranks: the copy is offered just above the delete. */
  @Test
  fun duplicateIsOfferedJustAboveDelete() {
    strip()

    openTheMenu("other.py")

    val duplicate = compose.onNodeWithText("Duplicate").assertIsDisplayed().getUnclippedBoundsInRoot()
    val delete = compose.onNodeWithText("Delete…").getUnclippedBoundsInRoot()
    assertTrue("Duplicate at ${duplicate.top}, Delete… at ${delete.top}", duplicate.top < delete.top)
  }

  /** A tab beneath the top is copied, and nothing opens: the stack and its top stay as they were. */
  @Test
  fun duplicateCopiesTheTabsFileAndOpensNothing() {
    strip()
    val before = backStack.toList()

    openTheMenu("notes.py")
    compose.onNodeWithText("Duplicate").performClick()

    compose.waitUntil(5_000) { workspace.copies.isNotEmpty() }
    assertEquals(listOf("notes.py"), workspace.copies)
    compose.waitForIdle()
    assertEquals(before, backStack.toList())
  }

  /** The reference sends a failed copy to the console alone; on a device that is silence, so it is said. */
  @Test
  fun aRefusedDuplicateSaysWhy() {
    workspace.copyRefusal = IllegalArgumentException("no file at 'notes.py' in the workspace to copy")
    strip()

    openTheMenu("notes.py")
    compose.onNodeWithText("Duplicate").performClick()

    compose.waitUntil(5_000) { compose.onAllNodesWithText("Duplicate Error").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText("no file at 'notes.py' in the workspace to copy").assertIsDisplayed()
  }
}
