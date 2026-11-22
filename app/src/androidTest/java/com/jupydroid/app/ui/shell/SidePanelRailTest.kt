package com.jupydroid.app.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.ShownPanel
import com.jupydroid.app.SidePanel
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.extension.api.SidePanelContribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

  private var settingsOpened = 0
  private var workspaceLeft = 0

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

  /**
   * Spaces is the one named way back to the list (`workbench.action.closeFolder`, in the reference's
   * File menu), so it is on the rail beside Settings - and, like Settings, opens no panel.
   */
  @Test
  fun spacesIsOnTheRailAndNeverSelected() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(SPACES).assertIsDisplayed().assertIsNotSelected()
  }

  @Test
  fun spacesLeavesTheWorkspaceAndMarksNoPanel() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(SPACES).performClick()

    compose.runOnIdle {
      assertEquals(1, workspaceLeft)
      assertEquals(emptyList<ShownPanel>(), chosen)
    }
  }

  /** At the foot with the gear: below the `+` and every panel, above Settings. */
  @Test
  fun spacesSitsAtTheFootAboveSettings() {
    show(SidePanel.FILES)

    val spaces = compose.onNodeWithContentDescription(SPACES).getUnclippedBoundsInRoot()
    val launcher = compose.onNodeWithContentDescription(NEW_LAUNCHER).getUnclippedBoundsInRoot()
    val gear = compose.onNodeWithContentDescription(SETTINGS).getUnclippedBoundsInRoot()

    assertTrue("Spaces $spaces starts below the + $launcher", spaces.top > launcher.bottom)
    assertTrue("Spaces $spaces ends above the gear $gear", spaces.bottom <= gear.top)
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

  /**
   * Settings is VS Code's Manage: the global activity at the bar's foot, below every view and the
   * `+` (`vscode@3f3380c6 src/vs/workbench/browser/parts/globalCompositeBar.ts`). Within two of its
   * own heights of the bottom rather than flush, so the system bar's inset is not what is measured.
   */
  @Test
  fun settingsSitsAtTheRailsFootBelowEveryOtherItem() {
    show(SidePanel.FILES)

    val gear = compose.onNodeWithContentDescription(SETTINGS).getUnclippedBoundsInRoot()
    val launcher = compose.onNodeWithContentDescription(NEW_LAUNCHER).getUnclippedBoundsInRoot()
    val rail = compose.onRoot().getUnclippedBoundsInRoot()

    assertTrue("gear $gear starts below the + $launcher", gear.top > launcher.bottom)
    assertTrue("gear $gear sits at the foot of $rail", rail.bottom - gear.bottom < gear.height * 2)
  }

  /** Settings is a document, like the `+`'s Launcher: opening it marks no panel. */
  @Test
  fun settingsOpensSettingsAndIsNeverSelected() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(SETTINGS).assertIsNotSelected().performClick()

    compose.runOnIdle {
      assertEquals(1, settingsOpened)
      assertEquals(emptyList<ShownPanel>(), chosen)
    }
  }

  private fun show(selected: SidePanel?) = show(selected?.let { ShownPanel.Shell(it) })

  private fun show(selected: ShownPanel?) {
    compose.setContent {
      JupydroidTheme {
        SidePanelRail(
          selected = selected,
          onChoose = { chosen += it },
          onOpenLauncher = {},
          onOpenSettings = { settingsOpened++ },
          onLeaveWorkspace = { workspaceLeft++ },
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
    const val SETTINGS = "Settings"
    const val SOURCE_CONTROL = "Source Control"
    const val SOURCE_CONTROL_ID = "test.sourceControl"
  }
}
