package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Explorer
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.Terminal
import com.jupydroid.app.data.fake.FakeDocumentCheckpoints
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A press on the middle of a tab is the tab's, never its close's, at the narrowest width a tab gets.
 *
 * A strip with more tabs than `maxWidth / 96dp` gives every tab the floor, and the close's 24dp slot
 * is widened to a 48dp press area that reaches 12dp past the slot's left edge. This presses the
 * middle of the first, inactive, tab at both phone widths a strip fills: four tabs at 320dp and
 * five at 360dp. The tabs are terminals, so no document is needed to draw them.
 */
@RunWith(AndroidJUnit4::class)
class TabMiddlePressTest {
  @get:Rule val compose = createComposeRule()

  private val backStack = mutableStateListOf<NavKey>()
  private val open = OpenDocuments()
  private val paths = mutableStateMapOf<NavKey, String>()
  private val closed = mutableListOf<NavKey>()

  private fun stripOf(tabCount: Int, window: Dp): List<NavKey> {
    val tabs: List<NavKey> = (1..tabCount).map { Terminal("$it") }
    backStack += listOf(Explorer(BASE_SPACE.id)) + tabs
    compose.setContent {
      JupydroidTheme {
        Box(Modifier.width(window)) {
          TabbedDocumentPane(
            backStack, open, paths, tabs, emptyMap(), FakeWorkspaceRepository(), FakeWorkspaceFiles(),
            twoPane = true, onCloseTab = { closed += it }, onOpenDocument = {},
            documentCheckpoints = FakeDocumentCheckpoints(),
          ) { Text("the document") }
        }
      }
    }
    return tabs
  }

  private fun pressMiddleOfFirstTab(longPress: Boolean) {
    compose.tabInReach(FIRST_TAB_TITLE).performTouchInput {
      if (longPress) longClick(center) else click(center)
    }
    compose.waitForIdle()
  }

  @Test
  fun aTapOnTheMiddleOfAFloorWidthTabActivatesItAt320dp() {
    val tabs = stripOf(tabCount = 4, window = 320.dp)

    pressMiddleOfFirstTab(longPress = false)

    assertEquals("closed, active", emptyList<NavKey>() to tabs.first(), closed to backStack.last())
  }

  @Test
  fun aTapOnTheMiddleOfAFloorWidthTabActivatesItAt360dp() {
    val tabs = stripOf(tabCount = 5, window = 360.dp)

    pressMiddleOfFirstTab(longPress = false)

    assertEquals("closed, active", emptyList<NavKey>() to tabs.first(), closed to backStack.last())
  }

  @Test
  fun aLongPressOnTheMiddleOfAFloorWidthTabOpensItsMenuAt320dp() {
    stripOf(tabCount = 4, window = 320.dp)

    pressMiddleOfFirstTab(longPress = true)

    compose.onNodeWithText("Close All Other Tabs").assertIsDisplayed()
    assertEquals(emptyList<NavKey>(), closed)
  }

  @Test
  fun aLongPressOnTheMiddleOfAFloorWidthTabOpensItsMenuAt360dp() {
    stripOf(tabCount = 5, window = 360.dp)

    pressMiddleOfFirstTab(longPress = true)

    compose.onNodeWithText("Close All Other Tabs").assertIsDisplayed()
    assertEquals(emptyList<NavKey>(), closed)
  }

  private companion object {
    const val FIRST_TAB_TITLE = "Terminal 1"
  }
}
