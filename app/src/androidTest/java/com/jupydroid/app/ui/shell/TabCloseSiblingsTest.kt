package com.jupydroid.app.ui.shell

import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
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
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Close All Other Tabs" and "Close Tabs to Right" on a tab's menu - jupyterlab@7f0e690's
 * `application:close-other-tabs` and `application:close-right-tabs`, on every main-area tab
 * (`packages/application-extension/schema/commands.json` `context`): a `widget.close()` per
 * sibling, so each goes through the close gate and a dirty one still asks
 * (`packages/application-extension/src/index.tsx`).
 */
@RunWith(AndroidJUnit4::class)
class TabCloseSiblingsTest {
  @get:Rule val compose = createComposeRule()

  private val backStack = mutableStateListOf<NavKey>(Explorer(BASE_SPACE.id))
  private val open = OpenDocuments()
  private val paths = mutableStateMapOf<NavKey, String>()

  private fun strip(vararg entries: NavKey) {
    backStack.addAll(entries)
    compose.setContent {
      JupyterDroidTheme {
        val tabs = backStack.mainAreaTabs()
        val closeTab = rememberMainAreaCloseGate(backStack, tabs, open, paths)
        TabbedDocumentPane(
          backStack = backStack,
          openDocuments = open,
          openDocumentPaths = paths,
          tabs = tabs,
          liveTabTitles = emptyMap(),
          workspaceRepository = FakeWorkspaceRepository(),
          workspaceFiles = FakeWorkspaceFiles(),
          onCloseTab = closeTab,
          onOpenDocument = {},
          documentCheckpoints = FakeDocumentCheckpoints(),
        ) { Text("the document") }
      }
    }
  }

  private fun menuOf(title: String) = compose.tabInReach(title).performTouchInput { longClick() }

  @Test
  fun closeTabsToRightClosesOnlyTheTabsAfterThePressedOne() {
    strip(FileEditor("a.py"), Terminal("1"), FileEditor("b.py"), Editor("c.ipynb"))

    menuOf("Terminal 1")
    compose.onNodeWithText(CLOSE_RIGHT).performClick()

    assertEquals(listOf(Explorer(BASE_SPACE.id), FileEditor("a.py"), Terminal("1")), backStack)
  }

  @Test
  fun closeAllOtherTabsLeavesThePressedTabAlone() {
    strip(FileEditor("a.py"), Terminal("1"), FileEditor("b.py"), Editor("c.ipynb"))

    menuOf("a.py")
    compose.onNodeWithText(CLOSE_OTHERS).performClick()

    assertEquals(listOf(Explorer(BASE_SPACE.id), FileEditor("a.py")), backStack)
  }

  /** The gate's own ask, queued per dirty sibling: Cancel keeps that one while the clean ones go. */
  @Test
  fun aDirtySiblingStillAsksAndCancelKeepsIt() {
    open.opened(FakeTabDocument("b.py", isDirty = true))
    strip(FileEditor("a.py"), FileEditor("b.py"), Editor("c.ipynb"))

    menuOf("a.py")
    compose.onNodeWithText(CLOSE_OTHERS).performClick()
    compose.onNodeWithText("Save your work").assertIsDisplayed()
    compose.onNodeWithText("Cancel").performClick()

    assertEquals(listOf(Explorer(BASE_SPACE.id), FileEditor("a.py"), FileEditor("b.py")), backStack)
  }

  /** `isEnabled`: others needs a second widget, right needs one after the pressed tab. */
  @Test
  fun theRowsAreGreyedWhereThereIsNothingToClose() {
    strip(FileEditor("a.py"), FileEditor("b.py"))

    menuOf("b.py")

    compose.onNodeWithText(CLOSE_OTHERS).assertIsEnabled()
    compose.onNodeWithText(CLOSE_RIGHT).assertIsNotEnabled()
  }

  @Test
  fun aLoneTabGreysBothRows() {
    strip(FileEditor("a.py"))

    menuOf("a.py")

    compose.onNodeWithText(CLOSE_OTHERS).assertIsNotEnabled()
    compose.onNodeWithText(CLOSE_RIGHT).assertIsNotEnabled()
  }

  /** The selector is every main-area tab, so a terminal's press opens the close rows and no document row. */
  @Test
  fun aTerminalTabsMenuHoldsTheCloseRowsAlone() {
    strip(Terminal("1"), FileEditor("a.py"))

    menuOf("Terminal 1")

    compose.onNodeWithText(CLOSE_OTHERS).assertIsDisplayed()
    compose.onAllNodesWithText("Rename…").assertCountEquals(0)
  }

  private companion object {
    const val CLOSE_OTHERS = "Close All Other Tabs"
    const val CLOSE_RIGHT = "Close Tabs to Right"
  }
}
