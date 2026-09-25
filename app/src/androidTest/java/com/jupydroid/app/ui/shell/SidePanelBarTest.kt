package com.jupydroid.app.ui.shell

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

  /** `onePaneDestinations` plus the Launcher - and the table of contents is **not** one of them. */
  @Test
  fun theBarOffersFilesSearchAndLauncherAndNoOutline() {
    show(SidePanel.FILES)

    compose.onNodeWithContentDescription(FILES).assertIsDisplayed()
    compose.onNodeWithContentDescription(SEARCH).assertIsDisplayed()
    compose.onNodeWithContentDescription(NEW_LAUNCHER).assertIsDisplayed()
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

  /**
   * **The items spread on a small screen**, which is the reference's own recommendation for one:
   * *"In small screens ... its `arrangement` should be `ShortNavigationBarArrangement.EqualWeight`,
   * so that the navigation items are equally distributed on the bar"*
   * (`androidx.compose.material3.ShortNavigationBar`'s KDoc, material3 1.4.0).
   *
   * Three items therefore fill the bar between them, which is also what puts each within a thumb's
   * reach of the edge it is nearest.
   */
  @Test
  fun theItemsFillTheBarOnASmallScreen() {
    showAt(PHONE)

    assertEquals(
      "three items equally distributed sit at the bar's sixth points",
      1f / 3f,
      centreGapFraction(PHONE),
      TOLERANCE_FRACTION,
    )
  }

  /**
   * **And they group on a medium screen**, which is the other half of the same sentence: *"In
   * medium screens ... its `arrangement` should be `ShortNavigationBarArrangement.Centered`, so that
   * the navigation items are distributed grouped on the center of the bar"*.
   *
   * **This is a defect the tablet found and the phone could not.** Measured at 800dp before the fix
   * (`evidence/p2y-bottom-navigation/800dp/`): each item was **396dp** wide with its centre at 198dp
   * and 602dp - a 24dp icon inside a tap target half the window across, and 404dp of dead bar
   * between the two. The reference's own `calculateCenteredContentHorizontalPadding` reserves
   * `(100 - 10 * (itemsCount + 3)) / 2` percent of padding a side - **20%** a side for three items,
   * so the group spans the middle 60% of the bar and stays centred in it.
   */
  @Test
  fun theItemsGroupInTheMiddleOnAMediumScreen() {
    showAt(TABLET_PORTRAIT)

    // Three items in the middle 60% of the bar puts their centres a **fifth** of it apart, against
    // the third that spreading them puts between them - which is the difference the tablet exposed.
    assertEquals(
      "three items grouped take three fifths of the bar between them",
      0.2f,
      centreGapFraction(TABLET_PORTRAIT),
      TOLERANCE_FRACTION,
    )
    val files = compose.onNodeWithContentDescription(FILES).getUnclippedBoundsInRoot()
    val launcher = compose.onNodeWithContentDescription(NEW_LAUNCHER).getUnclippedBoundsInRoot()
    val groupCentre = (files.left.value + launcher.right.value) / 2

    assertEquals("and the group is centred", TABLET_PORTRAIT.width.value / 2, groupCentre, 2f)
  }

  private fun show(selected: SidePanel) {
    compose.setContent {
      JupyterDroidTheme {
        SidePanelBar(selected = selected, onChoose = { chosen += it }, onOpenLauncher = {})
      }
    }
  }

  /**
   * How far apart the two items' centres sit, as a fraction of the bar's width.
   *
   * **Scale-free on purpose**: it is the one number that separates the two arrangements without
   * depending on the component's own item padding, which is what a first pass at this assertion
   * measured by accident - two items "filling" a 320dp bar read 312dp, the missing 8dp being the
   * gap the bar puts between them.
   */
  private fun centreGapFraction(size: DpSize): Float {
    val files = compose.onNodeWithContentDescription(FILES).getUnclippedBoundsInRoot()
    val search = compose.onNodeWithContentDescription(SEARCH).getUnclippedBoundsInRoot()
    val centreOf = { b: DpRect -> (b.left.value + b.right.value) / 2 }
    return (centreOf(search) - centreOf(files)) / size.width.value
  }

  /**
   * The bar at a stated width. `ForcedSize` changes the **layout constraints**, which is what the
   * bar reads for itself - unlike `currentWindowAdaptiveInfo()`, which measures the activity's
   * window and would leave a test driving one branch twice (`ActionRowPlacement`).
   */
  private fun showAt(size: DpSize) {
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
        JupyterDroidTheme {
          SidePanelBar(selected = SidePanel.FILES, onChoose = { chosen += it }, onOpenLauncher = {})
        }
      }
    }
  }

  private companion object {
    /** The emulator's own window, which is this project's compact device. */
    val PHONE = DpSize(320.dp, 640.dp)

    /** The tablet rotated to portrait - `w800dp`, measured, and the width P2y's bar exists for. */
    val TABLET_PORTRAIT = DpSize(800.dp, 1280.dp)

    /**
     * Slack for the gap the bar puts between its items, not a range the rule may drift in: the two
     * arrangements are 0.5 and 0.25 apart, so a 0.02 window cannot confuse one for the other.
     */
    const val TOLERANCE_FRACTION = 0.02f

    const val FILES = "Files"
    const val SEARCH = "Search"
    const val TABLE_OF_CONTENTS = "Table of contents"
  }
}
