package com.jupydroid.app.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
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
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.mainAreaTabs
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.extension.api.RENAME_FIELD_TAG
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.time.Instant

/**
 * Save As from the tab's menu: the path is asked, a taken one asked about, the copy written, and
 * then the old tab closes for the copy to open - jupyterlab@7f0e690's `CommandIDs.saveAs` closing
 * the old file and opening the new on the save's `fileChanged`
 * (`packages/docmanager-extension/src/index.tsx`).
 */
@RunWith(AndroidJUnit4::class)
class TabSaveAsTest {
  @get:Rule val compose = createComposeRule()

  private val backStack = mutableStateListOf<NavKey>(Explorer(BASE_SPACE.id))
  private val open = OpenDocuments()
  private val document = FakeTabDocument("dir/notes.py", isDirty = true)
  private val opened = mutableListOf<String>()
  private val paths = mutableStateMapOf<NavKey, String>()

  private fun strip(workspace: FakeWorkspaceRepository = FakeWorkspaceRepository()) {
    open.opened(document)
    backStack.add(FileEditor("dir/notes.py"))
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
          onOpenDocument = { opened += it },
          documentCheckpoints = FakeDocumentCheckpoints(workspace),
        ) { Text("the document") }
      }
    }
  }

  private fun saveAs(to: String) {
    compose.onNodeWithContentDescription("Tab: notes.py").performTouchInput { longClick() }
    compose.onNodeWithText("Save As…").performClick()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextClearance()
    compose.onNodeWithTag(RENAME_FIELD_TAG).performTextInput(to)
    compose.onNodeWithText("Save").performClick()
  }

  /** The dialog edits the whole path, so a copy can go into another folder. */
  @Test
  fun theDialogHoldsTheDocumentsPath() {
    strip()

    compose.onNodeWithContentDescription("Tab: notes.py").performTouchInput { longClick() }
    compose.onNodeWithText("Save As…").performClick()

    compose.onNodeWithText("Save File As…").assertIsDisplayed()
    compose.onNodeWithText("Path").assertIsDisplayed()
    compose.onNodeWithTag(RENAME_FIELD_TAG).assertTextContains("dir/notes.py")
  }

  /** The edits went to the copy, so the old tab drops them unwritten and goes without asking. */
  @Test
  fun aSavedCopyReplacesTheTab() {
    strip()

    saveAs("dir/notes-copy.py")

    compose.waitUntil(5_000) { opened.isNotEmpty() }
    assertEquals(listOf("dir/notes-copy.py"), document.copies)
    assertEquals(listOf("dir/notes-copy.py"), opened)
    assertEquals(1, document.discards)
    compose.onAllNodesWithContentDescription("Tab: notes.py").assertCountEquals(0)
  }

  /** `_maybeOverWrite`: a taken path is asked about, and only Overwrite writes over it. */
  @Test
  fun aTakenPathAsksBeforeItIsReplaced() {
    val taken = WorkspaceEntry("other.py", "dir/other.py", WorkspaceEntryKind.FILE, Instant.EPOCH)
    strip(FakeWorkspaceRepository(entriesByPath = mapOf("dir" to listOf(taken))))

    saveAs("dir/other.py")

    compose.waitUntil(5_000) { compose.onAllNodesWithText("Overwrite file?").fetchSemanticsNodes().isNotEmpty() }
    assertEquals(emptyList<String>(), document.copies)
    compose.onNodeWithText("Overwrite").performClick()

    compose.waitUntil(5_000) { opened.isNotEmpty() }
    assertEquals(listOf("dir/other.py"), document.copies)
  }

  /** `File Save Error for %1`: a refused write says why, and the tab stays as it was. */
  @Test
  fun aRefusedCopySaysWhyAndKeepsTheTab() {
    document.copyRefusal = IOException("no folder 'gone' in the workspace")
    strip()

    saveAs("gone/notes.py")

    compose.waitUntil(5_000) { compose.onAllNodesWithText("Save Error").fetchSemanticsNodes().isNotEmpty() }
    compose.onNodeWithText("no folder 'gone' in the workspace").assertIsDisplayed()
    compose.onNodeWithContentDescription("Tab: notes.py").assertIsDisplayed()
    assertEquals(0, document.discards)
  }
}
