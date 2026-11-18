package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Explorer
import com.jupydroid.app.Launcher
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.Settings
import com.jupydroid.app.Terminal
import com.jupydroid.app.activateMainAreaTab
import com.jupydroid.app.data.fake.FakeDocumentCheckpoints
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.PHONE_WIDTH
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The strip scrolls the active tab into view, the way VS Code's `revealActiveTab` and IntelliJ's
 * `scrollToSelectedTab` do: a tab that opens past the row's edge would otherwise be active and
 * unseen, listed `[0,0][0,0]` in a screen reader's tree at 320dp.
 *
 * The strip is held to a phone's 320dp whatever the device is, and five tabs at the 80dp floor are
 * 400dp of row, so the last two start beyond the edge.
 */
@RunWith(AndroidJUnit4::class)
class MainAreaTabStripRevealTest {
  @get:Rule val compose = createComposeRule()

  private val tabs: List<NavKey> = listOf(Launcher, Settings, Terminal("1"), Terminal("2"), Terminal("3"))
  private val backStack = mutableStateListOf<NavKey>()
  private val open = OpenDocuments()
  private val paths = mutableStateMapOf<NavKey, String>()
  private val liveTitles = mutableStateMapOf<NavKey, String>()

  private fun stripAtPhoneWidth(twoPane: Boolean = true, shown: List<NavKey> = tabs) {
    backStack += listOf(Explorer(BASE_SPACE.id)) + shown
    compose.setContent {
      JupyterDroidTheme {
        Box(Modifier.width(PHONE_WIDTH)) {
          TabbedDocumentPane(
            backStack, open, paths, shown, liveTitles, FakeWorkspaceRepository(), FakeWorkspaceFiles(),
            twoPane = twoPane, onCloseTab = {}, onOpenDocument = {}, documentCheckpoints = FakeDocumentCheckpoints(),
          ) {
            Text("the document")
          }
        }
      }
    }
  }

  private fun boundsOf(description: String): Rect =
    compose.onNodeWithContentDescription(description).fetchSemanticsNode().boundsInRoot

  private fun activate(tab: NavKey) {
    compose.runOnIdle { backStack.activateMainAreaTab(tab) }
    compose.waitForIdle()
  }

  /**
   * `boundsInRoot` is clipped by the scroller, so a tab wholly past the edge reports an empty rect
   * at it and a tab half past reports its visible half: only a clipped width equal to the laid-out
   * width is a tab the user can see whole.
   */
  private fun assertTabWhollyVisible(title: String) {
    val node = compose.onNodeWithContentDescription("Tab: $title").fetchSemanticsNode()
    val visibleWidth = node.boundsInRoot.width
    assertEquals("'${node.boundsInRoot}' of '$title' is clipped", node.size.width.toFloat(), visibleWidth, 0.5f)
    assertTrue("'$title' has no visible width", visibleWidth > 0f)
  }

  @Test
  fun aNewestTabPastTheEdgeOpensInsideTheStrip() {
    stripAtPhoneWidth()

    assertTabWhollyVisible("Terminal 3")
  }

  /**
   * At one pane the Up arrow, Reload, Save and Spaces leave the scroller 144dp of the 320, so the
   * two tabs' fair share of the strip's whole width (160dp each) is wider than the row that holds
   * the active one, and no scroll can show it whole.
   */
  @Test
  fun aTabFitsTheRowTheOnePaneStripLeavesIt() {
    stripAtPhoneWidth(twoPane = false, shown = listOf(Terminal("1"), Terminal("2")))

    assertTabWhollyVisible("Terminal 2")
  }

  @Test
  fun activatingATabScrolledAwayBringsItBack() {
    stripAtPhoneWidth()
    compose.onNodeWithContentDescription("Tab: Settings").performTouchInput { swipeLeft() }

    activate(Launcher)

    assertTabWhollyVisible("Launcher")
  }

  @Test
  fun activatingATabAlreadyInViewDoesNotMoveTheRow() {
    stripAtPhoneWidth()
    activate(Terminal("1"))
    val before = boundsOf("Tab: Terminal 1")

    activate(Terminal("1"))

    assertEquals(before, boundsOf("Tab: Terminal 1"))
  }
}
