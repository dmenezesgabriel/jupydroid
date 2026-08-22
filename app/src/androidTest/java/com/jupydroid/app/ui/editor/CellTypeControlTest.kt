package com.jupydroid.app.ui.editor

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.assertIsAtLeastMinimumTouchTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The control that names the active cell's type - the reference's `cellType` toolbar item.
 *
 * Instrumented rather than JVM because what is in question is whether there is something *visible
 * to press* and whether it **fits the row it shares**, and neither is a question a JVM test can
 * answer. [CellTypeLabelTest] and [CellTypePlacementTest] hold the halves that are pure.
 */
@RunWith(AndroidJUnit4::class)
class CellTypeControlTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theControlNamesTheTypeItWasGiven() {
    compose.setContent { JupyterDroidTheme { CellTypeControl(kind = CellKind.MARKDOWN, onPick = {}) } }

    compose.onNodeWithText("Markdown").assertIsDisplayed()
  }

  /**
   * Every type is offered, the one the cell already is included - see [CellTypePicker] for why
   * the reference's own select does the same.
   */
  /**
   * **The accessibility tree carries the value, not just the control's name.** A fixed
   * `contentDescription` replaces the text under it on Android, so "Cell type" alone would leave
   * TalkBack and every Maestro flow unable to read which type is showing - the failure
   * [SHOW_LINE_NUMBERS] records for menu rows, here for a control.
   */
  @Test
  fun itsNameCarriesTheTypeItIsShowing() {
    compose.setContent { JupyterDroidTheme { CellTypeControl(kind = CellKind.RAW, onPick = {}) } }

    compose.onNodeWithContentDescription("Cell type Raw").assertIsDisplayed()
  }

  @Test
  fun itOffersAllThreeTypes() {
    compose.setContent { JupyterDroidTheme { CellTypeControl(kind = CellKind.CODE, onPick = {}) } }

    compose.onNodeWithContentDescription(cellTypeDescription(CellKind.CODE)).performClick()

    // The open cell is Code, so "Code" is on screen twice - once on the trigger and once as the
    // row that is still offered. That doubling *is* the assertion: a picker that hid the current
    // type would show it once, and its rows would move about as the focus changed.
    CellKind.entries.forEach { kind ->
      val expected = if (kind == CellKind.CODE) 2 else 1
      val found = compose.onAllNodesWithText(cellTypeLabel(kind)).fetchSemanticsNodes().size
      assertEquals("${cellTypeLabel(kind)} appears $found times", expected, found)
    }
  }

  @Test
  fun pickingATypeReportsIt() {
    var picked: CellKind? = null
    compose.setContent { JupyterDroidTheme { CellTypeControl(kind = CellKind.CODE, onPick = { picked = it }) } }

    compose.onNodeWithContentDescription(cellTypeDescription(CellKind.CODE)).performClick()
    compose.onNodeWithContentDescription(cellTypePickDescription(CellKind.RAW)).performClick()

    assertEquals(CellKind.RAW, picked)
  }

  /**
   * Material's 48dp, reserved rather than borrowed from the bar around it - P2l-4's rule, and a
   * `TextButton` draws 40dp so the reservation has to be asked for.
   *
   * **Measured as a touch target and not as drawn bounds**, which is the distinction
   * [CellTouchTargetTest] was written to record: `minimumInteractiveComponentSize` expands what
   * accepts a press while the ripple stays the component's own size, so `getUnclippedBoundsInRoot`
   * reports 40dp on a control that is pressable over 48. This measured exactly that before it used
   * the shared helper.
   */
  @Test
  fun itReservesATouchTarget() {
    compose.setContent { JupyterDroidTheme { CellTypeControl(kind = CellKind.MARKDOWN, onPick = {}) } }

    compose.onNodeWithContentDescription(
      cellTypeDescription(WIDEST_LABEL),
    ).assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  /**
   * **The measurement the 600dp threshold is worth**, and the reason this test is instrumented.
   *
   * [cellTypeOnBar] says the bar takes this control from 600dp up. That is only true if the widest
   * label it can draw actually fits beside the navigation icon and the four actions - measured on
   * the shipped build, at the narrowest width the rule offers it, against the same string drawn
   * with nothing competing for the row. The technique is
   * [EditorTopBarTest.theWholeFileNameFitsAtTheNarrowestWidth]'s: Compose's semantics carry the
   * whole label whether or not it fits, so only the width can tell a squeezed control from a
   * whole one.
   *
   * **What it measured**: "Markdown" at `labelLarge` draws **73.3dp**, on both devices. The action
   * row has 72dp free at 320dp (`evidence/p2o-3-two-pane-threshold/320dp/hierarchy.xml`), so the
   * label alone does not fit there before the caret and the button's padding are counted - which
   * is [cellTypeOnBar]'s threshold arrived at from a measurement rather than an estimate.
   *
   * If this fails the number moves - the measurement is the deliverable, which is P2o-3's rule.
   */
  @Test
  fun theWidestLabelFitsTheBarAtTheNarrowestWidthThatOffersIt() {
    compose.setContent {
      JupyterDroidTheme {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(NARROWEST_WITH_ROOM)) {
          androidx.compose.foundation.layout.Column {
            EditorTopBar(
              title = "Intro to NumPy",
              canUndo = true,
              canRedo = true,
              onRename = {},
              onUndo = {},
              onRedo = {},
              onBack = {},
              activeCellKind = WIDEST_LABEL,
              cellTypeOnBar = true,
            )
            Text(
              text = cellTypeLabel(WIDEST_LABEL),
              style = MaterialTheme.typography.labelLarge,
              modifier = Modifier.testTag(UNCONSTRAINED_LABEL),
            )
          }
        }
      }
    }

    // The unmerged tree, because the control's own label is inside a merging `Button` whose bounds
    // are the *button's* - text plus caret plus padding - and comparing that with a bare `Text`
    // would compare two different things. Index 0 is the bar's, which composes first.
    val drawn = compose.onAllNodesWithText(cellTypeLabel(WIDEST_LABEL), useUnmergedTree = true)[0]
      .getUnclippedBoundsInRoot()
    val unconstrained = compose.onNodeWithTag(UNCONSTRAINED_LABEL, useUnmergedTree = true)
      .getUnclippedBoundsInRoot()
    // Compared at pixel resolution, because two independent layout passes of the same string
    // differ in the seventh decimal of a Dp - measured, `73.33333` against `73.333336` on the
    // tablet - and a label short by six millionths of a dp is not a truncated label. Pixels are
    // what is actually drawn.
    val shown = with(compose.density) { (drawn.right - drawn.left).roundToPx() }
    val wanted = with(compose.density) { (unconstrained.right - unconstrained.left).roundToPx() }
    assertTrue("the label draws ${shown}px of the ${wanted}px it wants", shown >= wanted)
  }

  /**
   * And it does not collide with the actions that share the row - the failure the width above
   * cannot see, since a control drawn at its full width can still be drawn *under* the undo button.
   */
  @Test
  fun itSitsClearOfTheActionsAtThatWidth() {
    compose.setContent {
      JupyterDroidTheme {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(NARROWEST_WITH_ROOM)) {
          EditorTopBar(
            title = "Intro to NumPy",
            canUndo = true,
            canRedo = true,
            onRename = {},
            onUndo = {},
            onRedo = {},
            onBack = {},
            activeCellKind = WIDEST_LABEL,
            cellTypeOnBar = true,
          )
        }
      }
    }

    val control = compose.onNodeWithContentDescription(cellTypeDescription(WIDEST_LABEL)).getUnclippedBoundsInRoot()
    val undo = compose.onNodeWithContentDescription("Undo cell operation").getUnclippedBoundsInRoot()
    assertTrue("the control at $control runs into the actions at $undo", control.right <= undo.left)
  }

  /**
   * **Moved, not duplicated.** Where the bar has no room the control is not on it - P2r-2 puts the
   * row in [NotebookActionsMenu] instead, which is what a `ReactiveToolbar`'s popup opener is
   * (`packages/ui-components/src/components/toolbar.tsx:570-700`). A bar that drew it anyway would
   * be the second home this app refuses everywhere else.
   */
  @Test
  fun theBarLeavesItAloneWhereThereIsNoRoom() {
    compose.setContent {
      JupyterDroidTheme {
        DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(COMPACT)) {
          EditorTopBar(
            title = "Intro to NumPy",
            canUndo = true,
            canRedo = true,
            onRename = {},
            onUndo = {},
            onRedo = {},
            onBack = {},
            activeCellKind = WIDEST_LABEL,
            cellTypeOnBar = false,
          )
        }
      }
    }

    compose.onNodeWithContentDescription(cellTypeDescription(WIDEST_LABEL)).assertDoesNotExist()
  }

  private companion object {
    /** [cellTypeOnBar]'s own boundary, which is the width the rule has to be right about. */
    val NARROWEST_WITH_ROOM = DpSize(600.dp, 800.dp)

    /** The longest of the three the control can be asked to draw. */
    val WIDEST_LABEL = CellKind.MARKDOWN

    /** The emulator's own window, and the narrowest this app is driven at. */
    val COMPACT = DpSize(320.dp, 640.dp)

    /** The same string with a row to itself, which is the width the label wants. */
    const val UNCONSTRAINED_LABEL = "unconstrained-cell-type-label"
  }
}
