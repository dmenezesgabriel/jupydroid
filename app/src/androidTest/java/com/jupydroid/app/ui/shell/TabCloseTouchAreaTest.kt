package com.jupydroid.app.ui.shell

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Editor
import com.jupydroid.app.Explorer
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.Terminal
import com.jupydroid.app.data.fake.FakeDocumentCheckpoints
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Where a press on a tab's close lands, which is what ADR-0142 states about the strip's sizes.
 *
 * The close reserves 24dp, and Compose widens a clickable's press area to 48dp around it, so the
 * area is the widened one cut wherever a neighbour has a direct hit of its own. Each press is aimed
 * in dp from the close's top-left corner, so the numbers do not move with the device's density.
 * The document under the strip is a clickable, which is the neighbour that cuts the area below the
 * strip's bottom edge; the active tab is the one under test so the strip has scrolled it whole into
 * view and a press on its own label, which activates an active tab, changes nothing the test reads.
 */
@RunWith(AndroidJUnit4::class)
class TabCloseTouchAreaTest {
  @get:Rule val compose = createComposeRule()

  private val tabs: List<NavKey> = listOf(Terminal("1"), Editor(TITLE))
  private val backStack = mutableStateListOf<NavKey>(Explorer(BASE_SPACE.id), *tabs.toTypedArray())
  private val open = OpenDocuments()
  private val paths = mutableStateMapOf<NavKey, String>()
  private val liveTitles = mutableStateMapOf<NavKey, String>()
  private val closed = mutableListOf<NavKey>()
  private var documentPresses = 0

  private fun showStrip(twoPane: Boolean) {
    compose.setContent {
      JupyterDroidTheme {
        TabbedDocumentPane(
          backStack, open, paths, tabs, liveTitles, FakeWorkspaceRepository(), FakeWorkspaceFiles(),
          twoPane = twoPane, onCloseTab = { closed += it }, onOpenDocument = {},
          documentCheckpoints = FakeDocumentCheckpoints(),
        ) {
          Box(Modifier.fillMaxSize().clickable { documentPresses++ }) { Text("the document") }
        }
      }
    }
    compose.waitForIdle()
  }

  private fun pressFromCloseCorner(right: Dp, down: Dp) {
    compose.onNodeWithContentDescription("Close tab: $TITLE").performTouchInput {
      click(Offset(right.toPx(), down.toPx()))
    }
    compose.waitForIdle()
  }

  @Test
  fun aPressTenDpIntoTheLabelClosesTheTab() {
    showStrip(twoPane = true)

    pressFromCloseCorner(right = (-10).dp, down = ROW_MIDDLE)

    assertEquals(listOf<NavKey>(Editor(TITLE)), closed)
  }

  @Test
  fun aPressSixteenDpIntoTheLabelDoesNotCloseTheTab() {
    showStrip(twoPane = true)

    pressFromCloseCorner(right = (-16).dp, down = ROW_MIDDLE)

    assertEquals(emptyList<NavKey>(), closed)
  }

  @Test
  fun aPressPastTheTabsRightEdgeDoesNotCloseTheTab() {
    showStrip(twoPane = true)

    pressFromCloseCorner(right = 30.dp, down = ROW_MIDDLE)

    assertEquals(emptyList<NavKey>(), closed)
  }

  @Test
  fun aPressBelowTheTwoPaneStripGoesToTheDocument() {
    showStrip(twoPane = true)

    pressFromCloseCorner(right = 12.dp, down = BELOW_THE_ROW)

    assertEquals("document presses, tabs closed", 1 to 0, documentPresses to closed.size)
  }

  @Test
  fun aPressBelowTheOnePaneRowStillClosesTheTab() {
    showStrip(twoPane = false)

    pressFromCloseCorner(right = 12.dp, down = BELOW_THE_ROW)

    assertEquals("document presses, tabs closed", 0 to 1, documentPresses to closed.size)
  }

  private companion object {
    const val TITLE = "a.ipynb"

    /** Half of the 24dp slot. */
    val ROW_MIDDLE = 12.dp

    /** 2dp under the 32dp row, whose bottom edge is 28dp under the slot's top. */
    val BELOW_THE_ROW = 30.dp
  }
}
