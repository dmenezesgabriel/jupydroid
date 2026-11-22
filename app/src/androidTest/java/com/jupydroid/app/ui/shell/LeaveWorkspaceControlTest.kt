package com.jupydroid.app.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.assertIsDisplayed
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
import com.jupydroid.app.SpacesHome
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
 * The strip's "Spaces" control: one named, visible way out of a Space to the list, at the width
 * where the strip is a document's whole window and nothing else on screen can leave. At two panes
 * the rail beside the document carries it instead, so the strip draws none.
 */
@RunWith(AndroidJUnit4::class)
class LeaveWorkspaceControlTest {
  @get:Rule val compose = createComposeRule()

  private val backStack = mutableStateListOf<NavKey>(SpacesHome, Explorer(BASE_SPACE.id))
  private val open = OpenDocuments()
  private val paths = mutableStateMapOf<NavKey, String>()

  private fun strip(twoPane: Boolean, vararg entries: NavKey) {
    backStack.addAll(entries)
    compose.setContent {
      JupydroidTheme {
        val tabs = backStack.mainAreaTabs()
        val gate = rememberMainAreaCloseGate(backStack, tabs, open, paths)
        TabbedDocumentPane(
          backStack = backStack,
          openDocuments = open,
          openDocumentPaths = paths,
          tabs = tabs,
          liveTabTitles = emptyMap(),
          workspaceRepository = FakeWorkspaceRepository(),
          workspaceFiles = FakeWorkspaceFiles(),
          twoPane = twoPane,
          onCloseTab = gate.closeTab,
          onOpenDocument = {},
          documentCheckpoints = FakeDocumentCheckpoints(),
          onLeaveWorkspace = gate.leaveWorkspace,
        ) { Text("the document") }
      }
    }
  }

  private fun spaces() = compose.onNodeWithContentDescription("Spaces")

  @Test
  fun aOnePaneStripNamesTheWayBackToSpaces() {
    strip(twoPane = false, FileEditor("a.py"))

    spaces().assertIsDisplayed()
  }

  @Test
  fun aTwoPaneStripDrawsNoSuchControl() {
    strip(twoPane = true, FileEditor("a.py"))

    spaces().assertDoesNotExist()
  }

  @Test
  fun tappingItLeavesForTheSpacesList() {
    strip(twoPane = false, FileEditor("a.py"), Editor("b.ipynb"))

    spaces().performClick()

    assertEquals(listOf<NavKey>(SpacesHome), backStack.toList())
  }

  @Test
  fun tappingItOverADirtyDocumentAsksBeforeLeaving() {
    open.opened(FakeTabDocument("a.py", isDirty = true))
    strip(twoPane = false, FileEditor("a.py"))

    spaces().performClick()
    compose.onNodeWithText("Save your work").assertIsDisplayed()
    compose.onNodeWithText("Discard").performClick()

    assertEquals(listOf<NavKey>(SpacesHome), backStack.toList())
  }
}
