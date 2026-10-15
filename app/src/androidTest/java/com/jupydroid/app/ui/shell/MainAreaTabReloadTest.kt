package com.jupydroid.app.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Editor
import com.jupydroid.app.Explorer
import com.jupydroid.app.FileEditor
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.Terminal
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.mainAreaTabs
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `docmanager:reload` as something to press: the reload icon beside the strip's save.
 *
 * The re-read itself is `EditorReloadTest`'s and `FileEditorReloadTest`'s. What only a rendered
 * strip can answer is whether there is a control, which document a tap reaches, and that a
 * document with unsaved work is asked about before its edit is thrown away.
 */
@RunWith(AndroidJUnit4::class)
class MainAreaTabReloadTest {
  @get:Rule val compose = createComposeRule()

  private val open = OpenDocuments()
  private val paths = mutableStateMapOf<NavKey, String>()

  private fun strip(vararg entries: NavKey) {
    val backStack = mutableStateListOf(Explorer(BASE_SPACE.id), *entries)
    compose.setContent {
      JupyterDroidTheme {
        TabbedDocumentPane(
          backStack = backStack,
          openDocuments = open,
          openDocumentPaths = paths,
          tabs = backStack.mainAreaTabs(),
          liveTabTitles = emptyMap(),
          workspaceRepository = FakeWorkspaceRepository(),
          onCloseTab = {},
          onOpenDocument = {},
        ) { Text("the document") }
      }
    }
  }

  private fun reloadTab(title: String) = compose.onNodeWithContentDescription("Reload tab: $title")

  /** jupyterlab's `isEnabled` is "has a context", not "is dirty": a clean document is the stale case. */
  @Test
  fun aTapReloadsACleanDocumentOnTopWithoutAsking() {
    val document = FakeTabDocument("b.py")
    open.opened(document)
    strip(Editor("a.ipynb"), FileEditor("b.py"))

    reloadTab("b.py").assertIsEnabled().performClick()

    assertEquals(1, document.reloads)
    compose.onNodeWithText("Reload File from Disk").assertDoesNotExist()
  }

  @Test
  fun aDirtyDocumentAsksAndCancelKeepsItsEdit() {
    val document = FakeTabDocument("b.py", isDirty = true)
    open.opened(document)
    strip(FileEditor("b.py"))

    reloadTab("b.py").performClick()
    compose.onNodeWithText("Reload File from Disk").assertIsDisplayed()
    compose.onNodeWithText("Cancel").performClick()

    assertEquals(0, document.reloads)
    assertTrue(document.dirtyFlow.value)
    compose.onNodeWithText("Reload File from Disk").assertDoesNotExist()
  }

  @Test
  fun aDirtyNotebookAsksByItsTypeAndReloadReloadsIt() {
    val document = FakeTabDocument("a.ipynb", isDirty = true)
    open.opened(document)
    strip(Editor("a.ipynb"))

    reloadTab("a.ipynb").performClick()
    compose.onNodeWithText("Reload Notebook from Disk").assertIsDisplayed()
    compose.onNodeWithText("Reload").performClick()

    assertEquals(1, document.reloads)
  }

  /** Greyed rather than gone, so the strip keeps its shape whichever tab is on top. */
  @Test
  fun aTabThatIsNotADocumentHasNothingToReload() {
    strip(Terminal("1"))

    reloadTab("Terminal 1").assertIsDisplayed().assertIsNotEnabled()
  }

  /** The icon is the top tab's: a tap never reaches a document beneath it. */
  @Test
  fun aTapReachesOnlyTheDocumentOnTop() {
    val beneath = FakeTabDocument("a.ipynb")
    open.opened(beneath)
    open.opened(FakeTabDocument("b.py"))
    strip(Editor("a.ipynb"), FileEditor("b.py"))

    reloadTab("b.py").performClick()

    assertEquals(0, beneath.reloads)
  }
}
