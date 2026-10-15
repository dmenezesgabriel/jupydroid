package com.jupydroid.app.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.ShownPanel
import com.jupydroid.app.SidePanel
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.extension.api.SidePanelContribution
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

  private val chosen = mutableListOf<ShownPanel>()

  /** Five items - one per [SidePanel], which is what the rail iterates. */
  @Test
  fun everyPanelIsOnTheRail() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(FILES).assertIsDisplayed()
    compose.onNodeWithContentDescription(SESSIONS_AND_TABS).assertIsDisplayed()
    compose.onNodeWithContentDescription(SEARCH).assertIsDisplayed()
    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).assertIsDisplayed()
    compose.onNodeWithContentDescription(DEBUGGER).assertIsDisplayed()
  }

  /** Spaces are managed from the home, before one is entered - never from a panel inside one. */
  @Test
  fun spacesAreNotOnTheRail() {
    show(SidePanel.FILES)

    compose.onAllNodesWithContentDescription(SPACES).assertCountEquals(0)
  }

  /** Which one is showing is the rail's own answer, and a screen reader's - `selected`, not a tint. */
  @Test
  fun theShowingPanelIsTheSelectedOne() {
    show(SidePanel.TABLE_OF_CONTENTS)

    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).assertIsSelected()
    compose.onNodeWithContentDescription(FILES).assertIsNotSelected()
    compose.onNodeWithContentDescription(SEARCH).assertIsNotSelected()
  }

  /** And search is selectable like the two beside it, not merely drawable. */
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

    compose.runOnIdle { assertEquals(listOf(ShownPanel.Shell(SidePanel.TABLE_OF_CONTENTS)), chosen) }
  }

  /**
   * The collapsed area still has its bar: `collapse()` *"only deselects the active tab, leaving the
   * activity bar visible"* (`packages/application/src/shell.ts:2383-2394`).
   */
  @Test
  fun aCollapsedAreaStillDrawsItsRailWithNothingSelected() {
    show(null as ShownPanel?)

    compose.onNodeWithContentDescription(FILES).assertIsDisplayed()
    compose.onNodeWithContentDescription(FILES).assertIsNotSelected()
    compose.onNodeWithContentDescription(SESSIONS_AND_TABS).assertIsNotSelected()
    compose.onNodeWithContentDescription(SEARCH).assertIsNotSelected()
    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).assertIsNotSelected()
  }

  /** The rail reports the pick; deciding it is a collapse is `SidePanel?.chosen`'s job, not this one's. */
  @Test
  fun pickingTheShowingPanelIsStillReported() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(FILES).performClick()

    compose.runOnIdle { assertEquals(listOf(ShownPanel.Shell(SidePanel.FILES)), chosen) }
  }

  /** An extension's panel is an item like the shell's own, after Search (ADR-0104). */
  @Test
  fun aContributedPanelIsOnTheRail() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(SOURCE_CONTROL).assertIsDisplayed()
    compose.onNodeWithContentDescription(SOURCE_CONTROL).assertIsNotSelected()
  }

  @Test
  fun pickingAContributedPanelReportsItsId() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(SOURCE_CONTROL).performClick()

    compose.runOnIdle { assertEquals(listOf(ShownPanel.Contributed(SOURCE_CONTROL_ID)), chosen) }
  }

  @Test
  fun aContributedPanelShowingIsTheSelectedOne() {
    show(ShownPanel.Contributed(SOURCE_CONTROL_ID))

    compose.onNodeWithContentDescription(SOURCE_CONTROL).assertIsSelected()
    compose.onNodeWithContentDescription(SEARCH).assertIsNotSelected()
  }

  private fun show(selected: SidePanel?) = show(selected?.let { ShownPanel.Shell(it) })

  private fun show(selected: ShownPanel?) {
    compose.setContent {
      JupyterDroidTheme {
        SidePanelRail(
          selected = selected,
          onChoose = { chosen += it },
          onOpenLauncher = {},
          contributions = listOf(SidePanelContribution(SOURCE_CONTROL_ID, SOURCE_CONTROL, Icons.Filled.AccountTree) {}),
        )
      }
    }
  }

  private companion object {
    const val FILES = "Files"
    const val SPACES = "Spaces"
    const val SESSIONS_AND_TABS = "Sessions and Tabs"
    const val SEARCH = "Search"
    const val TABLE_OF_CONTENTS = "Table of contents"
    const val DEBUGGER = "Debugger"
    const val SOURCE_CONTROL = "Source Control"
    const val SOURCE_CONTROL_ID = "test.sourceControl"
  }
}
