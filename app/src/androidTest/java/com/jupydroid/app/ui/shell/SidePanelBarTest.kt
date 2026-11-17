package com.jupydroid.app.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.ShownPanel
import com.jupydroid.app.SidePanel
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.extension.api.SidePanelContribution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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

  private val chosen = mutableListOf<ShownPanel>()
  private val terminals = mutableListOf<String>()
  private var settingsOpened = 0
  private var workspaceLeft = 0

  /** `onePaneDestinations` plus the Launcher and More - and the outline is **not** one. */
  @Test
  fun theBarOffersFilesRunningSearchLauncherAndMoreAndNoOutline() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(FILES).assertIsDisplayed()
    compose.onNodeWithContentDescription(SESSIONS_AND_TABS).assertIsDisplayed()
    compose.onNodeWithContentDescription(SEARCH).assertIsDisplayed()
    compose.onNodeWithContentDescription(NEW_LAUNCHER).assertIsDisplayed()
    compose.onNodeWithContentDescription(MORE).assertIsDisplayed()
    compose.onNodeWithContentDescription(TABLE_OF_CONTENTS).assertDoesNotExist()
  }

  /**
   * New terminal gave the bar's fifth slot up to More and moved into the modal rail behind it, with
   * Settings at the rail's foot (ADR-0109): neither is on screen until More is tapped.
   */
  @Test
  fun moreOpensARailWithNewTerminalAndSettingsAtItsFoot() {
    show(SidePanel.FILES)
    compose.onNodeWithText(NEW_TERMINAL).assertDoesNotExist()
    compose.onNodeWithText(OPEN_SETTINGS).assertDoesNotExist()
    compose.onNodeWithText(LEAVE_WORKSPACE).assertDoesNotExist()

    compose.onNodeWithContentDescription(MORE).performClick()

    val terminal = compose.onNodeWithText(NEW_TERMINAL).assertIsDisplayed().getUnclippedBoundsInRoot()
    val settings = compose.onNodeWithText(OPEN_SETTINGS).assertIsDisplayed().getUnclippedBoundsInRoot()
    val spaces = compose.onNodeWithText(LEAVE_WORKSPACE).assertIsDisplayed().getUnclippedBoundsInRoot()
    assertTrue("Settings ($settings) sits below New terminal ($terminal)", settings.top >= terminal.bottom)
    assertTrue("Spaces ($spaces) sits above Settings ($settings)", spaces.bottom <= settings.top)
    compose.onNodeWithContentDescription(MORE).assertIsNotSelected()
  }

  /**
   * The rail's terminal *launches*, like the launcher's own item: a fresh `Terminal` key, never a
   * panel pick, so `onChoose` hears nothing - and the rail closes behind it.
   */
  @Test
  fun theRailsTerminalAsksForAShellAndClosesTheRail() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(MORE).performClick()
    compose.onNodeWithText(NEW_TERMINAL).performClick()

    compose.runOnIdle {
      assertEquals(listOf("terminal"), terminals)
      assertEquals(emptyList<ShownPanel>(), chosen)
    }
    compose.onNodeWithText(NEW_TERMINAL).assertDoesNotExist()
  }

  @Test
  fun theRailsSettingsOpensSettingsAndClosesTheRail() {
    show(SidePanel.SEARCH)

    compose.onNodeWithContentDescription(MORE).performClick()
    compose.onNodeWithText(OPEN_SETTINGS).performClick()

    compose.runOnIdle { assertEquals(1, settingsOpened) }
    compose.onNodeWithText(OPEN_SETTINGS).assertDoesNotExist()
  }

  /** Leaving is the Spaces row's act: the rail closes behind it and no panel is picked. */
  @Test
  fun theRailsSpacesLeavesTheWorkspaceAndClosesTheRail() {
    show(SidePanel.SEARCH)

    compose.onNodeWithContentDescription(MORE).performClick()
    compose.onNodeWithText(LEAVE_WORKSPACE).performClick()

    compose.runOnIdle {
      assertEquals(1, workspaceLeft)
      assertEquals(emptyList<ShownPanel>(), chosen)
    }
    compose.onNodeWithText(LEAVE_WORKSPACE).assertDoesNotExist()
  }

  /**
   * Every contributed panel is a row of the rail, after New terminal and before Settings, in the
   * order the extensions gave them, then the foot - Spaces over Settings: the bar holds five and no more
   * (ADR-0109).
   */
  @Test
  fun moreListsEveryContributedPanelBetweenNewTerminalAndTheFoot() {
    show(SidePanel.FILES)
    compose.onNodeWithText(SOURCE_CONTROL).assertDoesNotExist()

    compose.onNodeWithContentDescription(MORE).performClick()

    val rows = listOf(NEW_TERMINAL, SOURCE_CONTROL, CHAT, LEAVE_WORKSPACE, OPEN_SETTINGS)
      .map { compose.onNodeWithText(it).assertIsDisplayed().getUnclippedBoundsInRoot() }
    rows.zipWithNext().forEach { (above, below) -> assertTrue("$below sits below $above", below.top >= above.bottom) }
  }

  /** A contributed row is a panel pick, unlike New terminal and Settings, and it closes the rail. */
  @Test
  fun aContributedRowShowsItsPanelAndClosesTheRail() {
    show(SidePanel.SEARCH)

    compose.onNodeWithContentDescription(MORE).performClick()
    compose.onNodeWithText(SOURCE_CONTROL).performClick()

    compose.runOnIdle { assertEquals(listOf<ShownPanel>(ShownPanel.Contributed(SOURCE_CONTROL_ID)), chosen) }
    compose.onNodeWithText(SOURCE_CONTROL).assertDoesNotExist()
  }

  /**
   * The showing contributed panel is marked on its row, as VS Code radio-checks the active container
   * in its overflow menu (`vscode@3f3380c6 src/vs/workbench/browser/parts/compositeBarActions.ts`),
   * and none of the bar's items is, since none of them is showing.
   */
  @Test
  fun theShowingContributedPanelsRowIsSelectedAndNoBarItemIs() {
    show(ShownPanel.Contributed(SOURCE_CONTROL_ID))

    listOf(FILES, SESSIONS_AND_TABS, SEARCH, MORE)
      .forEach { compose.onNodeWithContentDescription(it).assertIsNotSelected() }
    compose.onNodeWithContentDescription(MORE).performClick()
    compose.onNodeWithText(SOURCE_CONTROL).assertIsSelected()
    compose.onNodeWithText(CHAT).assertIsNotSelected()
  }

  /** Back closes the rail and leaves the panel where it was, asking for nothing. */
  @Test
  fun backClosesTheRailWithoutActing() {
    show(SidePanel.SEARCH)
    compose.onNodeWithContentDescription(MORE).performClick()
    compose.onNodeWithText(NEW_TERMINAL).assertIsDisplayed()

    Espresso.pressBack()

    compose.onNodeWithText(NEW_TERMINAL).assertDoesNotExist()
    compose.onNodeWithContentDescription(SEARCH).assertIsSelected()
    compose.runOnIdle {
      assertEquals(emptyList<String>(), terminals)
      assertEquals(0, settingsOpened)
    }
  }

  /**
   * **Icon-only, name in semantics** - the rail's own rule (`SidePanelRail`) for the reason a flow
   * already cost this project: a screen reader and a Maestro flow address one string. Measured here
   * rather than argued: `ExplorerScreen` already titles itself "Files" and `SearchSidePanel`
   * "Search", so a labelled item would put each string on screen twice and `onNodeWithText` throws
   * on more than one match. VS Code's bar is icon-only in every position it has.
   */
  @Test
  fun theItemsCarryNoVisibleText() {
    show(SidePanel.FILES)

    compose.onNodeWithText(FILES).assertDoesNotExist()
    compose.onNodeWithText(SESSIONS_AND_TABS).assertDoesNotExist()
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

    compose.runOnIdle { assertEquals(listOf(ShownPanel.Shell(SidePanel.SEARCH)), chosen) }
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

    compose.runOnIdle { assertEquals(listOf(ShownPanel.Shell(SidePanel.FILES)), chosen) }
    compose.onNodeWithContentDescription(FILES).assertIsSelected()
  }

  /**
   * **The items spread on a small screen**, which is the reference's own recommendation for one:
   * *"In small screens ... its `arrangement` should be `ShortNavigationBarArrangement.EqualWeight`,
   * so that the navigation items are equally distributed on the bar"*
   * (`androidx.compose.material3.ShortNavigationBar`'s KDoc, material3 1.4.0).
   *
   * Five items therefore fill the bar between them - the reference's own ceiling for a bottom bar
   * (*"three to five destinations of equal importance"*), which is also what puts each within a
   * thumb's reach of the edge it is nearest.
   */
  @Test
  fun theItemsFillTheBarOnASmallScreen() {
    showAt(PHONE)

    assertEquals(
      "five items equally distributed sit at the bar's tenth points",
      1f / 5f,
      centreGapFraction(PHONE),
      TOLERANCE_FRACTION,
    )
  }

  /**
   * **And they group on a medium screen**, which is the other half of the same sentence: *"In
   * medium screens ... its `arrangement` should be `ShortNavigationBarArrangement.Centered`, so that
   * the navigation items are distributed grouped on the center of the bar"*.
   *
   * **This is a defect the tablet found and the phone could not.** Measured at 800dp before the fix:
   * each item was **396dp** wide with its centre at 198dp
   * and 602dp - a 24dp icon inside a tap target half the window across, and 404dp of dead bar
   * between the two. The reference's own `calculateCenteredContentHorizontalPadding` reserves
   * `(100 - 10 * (itemsCount + 3)) / 2` percent of padding a side - **10%** a side for five items,
   * so the group spans the middle 80% of the bar and stays centred in it.
   */
  @Test
  fun theItemsGroupInTheMiddleOnAMediumScreen() {
    showAt(TABLET_PORTRAIT)

    // Five items in the middle 80% of the bar puts adjacent centres **0.16** of it apart, against
    // the fifth that spreading them puts between them - which is the difference a wide bar
    // exposes.
    assertEquals(
      "five items grouped take 80% of the bar between them",
      0.16f,
      centreGapFraction(TABLET_PORTRAIT),
      TOLERANCE_FRACTION,
    )
    val filesNode = compose.onNodeWithContentDescription(FILES)
    val files = filesNode.getUnclippedBoundsInRoot()
    val more = compose.onNodeWithContentDescription(MORE).getUnclippedBoundsInRoot()
    val groupCentre = (files.left.value + more.right.value) / 2
    // `ForcedSize` fixes the dp the bar lays out in but not the pixels behind them, so one pixel is read
    // off the node itself: its width in dp over its width in px.
    val dpPerPixel = (files.right - files.left).value / filesNode.fetchSemanticsNode().size.width

    assertEquals(
      "and the group is centred",
      TABLET_PORTRAIT.width.value / 2,
      groupCentre,
      centreSlackDp(ONE_PANE_ITEMS, dpPerPixel),
    )
  }

  private fun show(selected: SidePanel) = show(ShownPanel.Shell(selected))

  private fun show(selected: ShownPanel) {
    compose.setContent {
      JupyterDroidTheme {
        SidePanelBar(
          selected = selected,
          onChoose = { chosen += it },
          onOpenLauncher = {},
          onNewTerminal = { terminals += "terminal" },
          onOpenSettings = { settingsOpened++ },
          onLeaveWorkspace = { workspaceLeft++ },
          contributions = CONTRIBUTIONS,
        )
      }
    }
  }

  /**
   * How far apart two *adjacent* items' centres sit, as a fraction of the bar's width - the first
   * two of [onePaneDestinations], which are neighbours in every arrangement.
   *
   * **Scale-free on purpose**: it is the one number that separates the two arrangements without
   * depending on the component's own item padding, which is what a first pass at this assertion
   * measured by accident - items "filling" a bar read as width minus the gap the bar puts between
   * them.
   */
  private fun centreGapFraction(size: DpSize): Float {
    val files = compose.onNodeWithContentDescription(FILES).getUnclippedBoundsInRoot()
    val running = compose.onNodeWithContentDescription(SESSIONS_AND_TABS).getUnclippedBoundsInRoot()
    val centreOf = { b: DpRect -> (b.left.value + b.right.value) / 2 }
    return (centreOf(running) - centreOf(files)) / size.width.value
  }

  /**
   * The bar at a stated width. `ForcedSize` changes the **layout constraints**, which is the only
   * width the bar reads - `SidePanelBar` asks its own constraints, never the window
   * (`ActionRowPlacement`).
   */
  private fun showAt(size: DpSize) {
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
        JupyterDroidTheme {
          SidePanelBar(
            selected = ShownPanel.Shell(SidePanel.FILES),
            onChoose = { chosen += it },
            onOpenLauncher = {},
            onNewTerminal = { terminals += "terminal" },
            onOpenSettings = { settingsOpened++ },
            onLeaveWorkspace = { workspaceLeft++ },
            contributions = CONTRIBUTIONS,
          )
        }
      }
    }
  }

  private companion object {
    /** The emulator's own window, which is this project's compact device. */
    val PHONE = DpSize(320.dp, 640.dp)

    /** The tablet rotated to portrait - `w800dp`, measured, and the width the bar exists for. */
    val TABLET_PORTRAIT = DpSize(800.dp, 1280.dp)

    /**
     * Slack for the gap the bar puts between its items, not a range the rule may drift in: the two
     * arrangements are 0.5 and 0.25 apart, so a 0.02 window cannot confuse one for the other.
     */
    const val TOLERANCE_FRACTION = 0.02f

    /** Files, Sessions and Tabs, Search, New Launcher and More: the five the bar draws. */
    const val ONE_PANE_ITEMS = 5

    const val FILES = "Files"
    const val SESSIONS_AND_TABS = "Sessions and Tabs"
    const val SEARCH = "Search"
    const val TABLE_OF_CONTENTS = "Table of contents"
    const val SOURCE_CONTROL = "Source Control"
    const val SOURCE_CONTROL_ID = "git.sourceControl"
    const val CHAT = "Chat"

    /** Two, as the app's Git and AI extensions contribute, so the rows' order is observable. */
    val CONTRIBUTIONS = listOf(
      SidePanelContribution(SOURCE_CONTROL_ID, SOURCE_CONTROL, Icons.Filled.AccountTree) {},
      SidePanelContribution("ai.chat", CHAT, Icons.AutoMirrored.Filled.Chat) {},
    )
  }
}
