package com.jupydroid.app.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Editor
import com.jupydroid.app.Explorer
import com.jupydroid.app.FileEditor
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.Terminal
import com.jupydroid.app.data.fake.FakeDocumentCheckpoints
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.mainAreaTabs
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `docmanager:save` as something to press: the save icon at the tab strip's trailing edge.
 *
 * The write itself is `EditorAutosaveTest`'s and `FileEditorAutosaveTest`'s. What only a rendered
 * strip can answer is whether there is a control, whether it is live exactly while the document on
 * top owes a write, and which document a tap reaches.
 */
@RunWith(AndroidJUnit4::class)
class MainAreaTabSaveTest {
  @get:Rule val compose = createComposeRule()

  private val open = OpenDocuments()
  private val paths = mutableStateMapOf<NavKey, String>()

  private fun strip(vararg entries: NavKey) {
    val backStack = mutableStateListOf(Explorer(BASE_SPACE.id), *entries)
    compose.setContent {
      JupydroidTheme {
        TabbedDocumentPane(
          backStack = backStack,
          openDocuments = open,
          openDocumentPaths = paths,
          tabs = backStack.mainAreaTabs(),
          liveTabTitles = emptyMap(),
          workspaceRepository = FakeWorkspaceRepository(),
          workspaceFiles = FakeWorkspaceFiles(),
          onCloseTab = {},
          onOpenDocument = {},
          documentCheckpoints = FakeDocumentCheckpoints(),
        ) { Text("the document") }
      }
    }
  }

  private fun saveTab(title: String) = compose.onNodeWithContentDescription("Save tab: $title")

  @Test
  fun aTapSavesTheDocumentOnTopAndItStaysOpen() {
    val document = FakeTabDocument("b.py", isDirty = true)
    open.opened(document)
    strip(Editor("a.ipynb"), FileEditor("b.py"))

    saveTab("b.py").assertIsEnabled().performClick()

    assertEquals(1, document.savesRequested)
    saveTab("b.py").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Tab: b.py").assertIsDisplayed()
  }

  /** VS Code's `ActiveEditorDirtyContext`: a document that owes nothing has nothing to save. */
  @Test
  fun aCleanDocumentOnTopHasNothingToSave() {
    open.opened(FakeTabDocument("b.py"))
    strip(FileEditor("b.py"))

    saveTab("b.py").assertIsDisplayed().assertIsNotEnabled()
  }

  /** Greyed rather than gone, so the strip keeps its shape whichever tab is on top. */
  @Test
  fun aTabThatIsNotADocumentHasNothingToSave() {
    strip(Terminal("1"))

    saveTab("Terminal 1").assertIsDisplayed().assertIsNotEnabled()
  }

  /** The icon is the top tab's: a document owing a write beneath it is not what a tap would reach. */
  @Test
  fun aDirtyDocumentBeneathTheTopDoesNotLightTheIcon() {
    val beneath = FakeTabDocument("a.ipynb", isDirty = true)
    open.opened(beneath)
    open.opened(FakeTabDocument("b.py"))
    strip(Editor("a.ipynb"), FileEditor("b.py"))

    saveTab("b.py").assertIsNotEnabled()
    assertEquals(0, beneath.savesRequested)
  }

  /** Fixed beside the scrolling tabs, as the one-pane arrow is: more tabs than fit never push it off. */
  @Test
  fun theIconStaysOnScreenWhenTheTabsOverflow() {
    open.opened(FakeTabDocument("f29.py", isDirty = true))
    strip(*Array(TABS_PAST_ANY_WIDTH) { FileEditor("f$it.py") })

    saveTab("f29.py").assertIsDisplayed().assertIsEnabled()
  }

  private companion object {
    /** Thirty tabs at the 96dp floor are 2880dp of strip - wider than a tablet in landscape. */
    const val TABS_PAST_ANY_WIDTH = 30
  }
}
