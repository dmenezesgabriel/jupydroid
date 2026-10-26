package com.jupydroid.app

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.navigation3.runtime.NavKey
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.extension.api.DocumentContribution
import com.jupydroid.extension.api.SidePanelMove
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * A tab an extension draws moves to the side panel its contribution names, as jupyter-chat's `moveChat`
 * does from the main area (jupyter-chat@2c224bc `packages/jupyterlab-chat-extension/src/index.ts`): the
 * panel is told the file and shown, then the tab closes.
 */
class ContributedDocumentMoveTest {
  private val steps = RecordingSidePanelMoveSteps()
  private val chatTab = ContributedDocument("ai.chat", "a.chat")
  private val move = SidePanelMove("ai.chat.panel", steps::open)

  @Test
  fun thePanelIsToldTheFileAndShownBeforeTheTabCloses() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), chatTab)

    backStack.moveToSidePanel(chatTab, move, twoPane = true, steps::show, steps::close)

    assertEquals(
      listOf("open a.chat", "show ${ShownPanel.Contributed("ai.chat.panel")}", "close $chatTab"),
      steps.taken,
    )
  }

  @Test
  fun atTwoPanesOnlyTheTabLeavesTheStack() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), FileEditor("b.py"), chatTab)

    backStack.moveToSidePanel(chatTab, move, twoPane = true, steps::show) { backStack.remove(it) }

    assertEquals(listOf(Explorer(BASE_SPACE.id), FileEditor("b.py")), backStack)
  }

  /** At one pane the side panel is the window, drawn only while the browser's entry is on top. */
  @Test
  fun atOnePaneTheBrowserComesForward() {
    val backStack = mutableListOf<NavKey>(Explorer(BASE_SPACE.id), FileEditor("b.py"), chatTab)

    backStack.moveToSidePanel(chatTab, move, twoPane = false, steps::show) { backStack.remove(it) }

    assertEquals(listOf(FileEditor("b.py"), Explorer(BASE_SPACE.id)), backStack)
  }

  @Test
  fun aContributionWithoutAMoveOffersNone() {
    val still = DocumentContribution(id = "ai.chat", suffix = ".chat", icon = Icons.Filled.Star) { _, _ -> }

    assertNull(listOf(still).sidePanelMoveOf(chatTab))
  }

  @Test
  fun aContributionNoExtensionGivesAnyMoreOffersNone() {
    assertNull(emptyList<DocumentContribution>().sidePanelMoveOf(chatTab))
  }

  @Test
  fun theContributionsMoveIsFoundByTheTabsId() {
    val moving = DocumentContribution("ai.chat", ".chat", Icons.Filled.Star, movesTo = move) { _, _ -> }

    assertEquals(move, listOf(moving).sidePanelMoveOf(chatTab))
  }

  /** Each step the move takes, in the order it took them. */
  private class RecordingSidePanelMoveSteps {
    val taken = mutableListOf<String>()

    fun open(path: String) {
      taken += "open $path"
    }

    fun show(panel: ShownPanel) {
      taken += "show $panel"
    }

    fun close(tab: NavKey) {
      taken += "close $tab"
    }
  }
}
