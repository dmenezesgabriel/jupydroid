package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.SidePanel
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The activity bar where the shell draws **one pane**: the same bar, moved to the bottom.
 *
 * VS Code's own activity bar has a bottom position carrying the same containers -
 * `ActivityBarPosition.BOTTOM` (`vscode/…/services/layout/browser/layoutService.ts:75-80`), set by
 * `LayoutSettings.ACTIVITY_BAR_LOCATION` (`…/parts/activitybar/activitybarPart.ts:604-616`) - so
 * this is a relocation rather than an Android control invented beside one.
 *
 * The pair with [SidePanelRailTest] is the point of reading them together: what differs is the two
 * things that must (the destinations, and that a re-pick does not collapse) and nothing else.
 */
@RunWith(AndroidJUnit4::class)
class SidePanelBarTest {
  @get:Rule val compose = createComposeRule()

  private val chosen = mutableListOf<SidePanel>()

  /** `onePaneDestinations`, drawn - and the table of contents is **not** one of them. */
  @Test
  fun theBarOffersFilesAndSearchAndNoOutline() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(FILES).assertIsDisplayed()
    compose.onNodeWithContentDescription(SEARCH).assertIsDisplayed()
    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).assertDoesNotExist()
  }

  /**
   * **Icon-only, name in semantics** - the rail's own rule (`SidePanelRail`) for the reason a flow
   * cost this project in P2b: a screen reader and a Maestro flow address one string. Measured here
   * rather than argued: `ExplorerScreen` already titles itself "Files" and `SearchSidePanel`
   * "Search", so a labelled item would put each string on screen twice and `onNodeWithText` throws
   * on more than one match. VS Code's bar is icon-only in every position it has.
   */
  @Test
  fun theItemsCarryNoVisibleText() {
    show(SidePanel.FILES)

    compose.onNodeWithText(FILES).assertDoesNotExist()
    compose.onNodeWithText(SEARCH).assertDoesNotExist()
  }

  /** Which one is showing is the bar's own answer, and a screen reader's - `selected`, not a tint. */
  @Test
  fun theShowingPanelIsTheSelectedOne() {
    show(SidePanel.SEARCH)

    compose.onNodeWithContentDescription(SEARCH).assertIsSelected()
    compose.onNodeWithContentDescription(FILES).assertIsNotSelected()
  }

  @Test
  fun aPickReportsWhichPanelWasAskedFor() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(SEARCH).performClick()

    compose.runOnIdle { assertEquals(listOf(SidePanel.SEARCH), chosen) }
  }

  /**
   * **The bar does not collapse, where the rail does**, and it is the one behavioural difference
   * between them. `collapse()` deselects the tab and leaves the activity bar over an empty area
   * (`packages/application/src/shell.ts:2383-2394`) - coherent beside a document, and an empty
   * window without one. Pinned here so the pair cannot drift into agreeing.
   */
  @Test
  fun rePickingTheShowingPanelReportsItRatherThanCollapsing() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(FILES).performClick()

    compose.runOnIdle { assertEquals(listOf(SidePanel.FILES), chosen) }
    compose.onNodeWithContentDescription(FILES).assertIsSelected()
  }

  private fun show(selected: SidePanel) {
    compose.setContent {
      JupyterDroidTheme {
        SidePanelBar(selected = selected, onChoose = { chosen += it })
      }
    }
  }

  private companion object {
    const val FILES = "Files"
    const val SEARCH = "Search"
    const val TABLE_OF_CONTENTS = "Table of contents"
  }
}
