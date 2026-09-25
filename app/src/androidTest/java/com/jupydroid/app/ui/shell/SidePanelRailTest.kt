package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.SidePanel
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The activity bar: which side panel is showing, and the one control that changes it.
 *
 * The reference puts this at the **window's** left edge rather than inside the side area -
 * `hboxPanel.addWidget(leftHandler.sideBar)` beside the split that holds both the panel and the
 * document area (`packages/application/src/shell.ts:468-477`) - which is why it is composed beside
 * `NavDisplay` and not inside a pane.
 */
@RunWith(AndroidJUnit4::class)
class SidePanelRailTest {
  @get:Rule val compose = createComposeRule()

  private val chosen = mutableListOf<SidePanel>()

  /** Three items since P2x-2 - one per [SidePanel], which is what the rail iterates. */
  @Test
  fun everyPanelIsOnTheRail() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(FILES).assertIsDisplayed()
    compose.onNodeWithContentDescription(SEARCH).assertIsDisplayed()
    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).assertIsDisplayed()
  }

  /** Which one is showing is the rail's own answer, and a screen reader's - `selected`, not a tint. */
  @Test
  fun theShowingPanelIsTheSelectedOne() {
    show(SidePanel.TABLE_OF_CONTENTS)

    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).assertIsSelected()
    compose.onNodeWithContentDescription(FILES).assertIsNotSelected()
    compose.onNodeWithContentDescription(SEARCH).assertIsNotSelected()
  }

  /** And the one P2x-2 added is selectable like the two beside it, not merely drawable. */
  @Test
  fun theSearchPanelIsTheSelectedOneWhenItIsShowing() {
    show(SidePanel.SEARCH)

    compose.onNodeWithContentDescription(SEARCH).assertIsSelected()
    compose.onNodeWithContentDescription(FILES).assertIsNotSelected()
  }

  @Test
  fun aPickReportsWhichPanelWasAskedFor() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).performClick()

    compose.runOnIdle { assertEquals(listOf(SidePanel.TABLE_OF_CONTENTS), chosen) }
  }

  /**
   * The collapsed area still has its bar: `collapse()` *"only deselects the active tab, leaving the
   * activity bar visible"* (`packages/application/src/shell.ts:2383-2394`).
   */
  @Test
  fun aCollapsedAreaStillDrawsItsRailWithNothingSelected() {
    show(selected = null)

    compose.onNodeWithContentDescription(FILES).assertIsDisplayed()
    compose.onNodeWithContentDescription(FILES).assertIsNotSelected()
    compose.onNodeWithContentDescription(SEARCH).assertIsNotSelected()
    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).assertIsNotSelected()
  }

  /** The rail reports the pick; deciding it is a collapse is `SidePanel?.chosen`'s job, not this one's. */
  @Test
  fun pickingTheShowingPanelIsStillReported() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(FILES).performClick()

    compose.runOnIdle { assertEquals(listOf(SidePanel.FILES), chosen) }
  }

  private fun show(selected: SidePanel?) {
    compose.setContent {
      JupyterDroidTheme {
        SidePanelRail(selected = selected, onChoose = { chosen += it }, onOpenLauncher = {})
      }
    }
  }

  private companion object {
    const val FILES = "Files"
    const val SEARCH = "Search"
    const val TABLE_OF_CONTENTS = "Table of contents"
  }
}
