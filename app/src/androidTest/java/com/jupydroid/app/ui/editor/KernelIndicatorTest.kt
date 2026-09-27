package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.assertIsAtLeastMinimumTouchTarget
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The kernel indicator (P2w-2): which kernel the notebook runs on, and what it is doing.
 *
 * Instrumented rather than JVM because every question here is about *placement* - which control
 * the indicator sits between, whether its name is drawn at a given width, and whether there is
 * something big enough to press. The status rule itself is `KernelStatusTest`.
 */
@RunWith(AndroidJUnit4::class)
class KernelIndicatorTest {
  @get:Rule val compose = createComposeRule()

  /**
   * **Right of Find and left of the `⋮`**, which is where the reference puts it once its own
   * layout is read rather than recalled. JupyterLab ranks `kernelName` 1000 and
   * `executionProgress` 1002, after the spacer at 100 - the far right of the toolbar - but the
   * toolbar's *last* widget is the popup opener: `ReactiveToolbar` inserts it first and every
   * later `insertItem` clamps to `widgets.length - 1`
   * (`packages/ui-components/src/components/toolbar.tsx:391`, `:470-480`). This app's popup opener
   * is [NotebookActionsMenu], as [labelsFitOnActionRow] already states about itself. Android's
   * top-app-bar convention puts the overflow last too.
   */
  @Test
  fun theIndicatorSitsBetweenFindAndTheOverflow() {
    showTopBar(WIDE)

    val find = compose.onNodeWithContentDescription(FIND_ACTION).getUnclippedBoundsInRoot()
    val kernel = compose.onNodeWithContentDescription(IDLE_DESCRIPTION).getUnclippedBoundsInRoot()
    val overflow = compose.onNodeWithContentDescription(NOTEBOOK_ACTIONS).getUnclippedBoundsInRoot()

    assertTrue("the indicator at $kernel is not right of find at $find", kernel.left >= find.right)
    assertTrue("the indicator at $kernel is not left of the overflow at $overflow", kernel.right <= overflow.left)
  }

  /** `sessionContext.kernelDisplayName` is what the reference's `kernelName` item draws. */
  @Test
  fun aWideBarNamesTheKernel() {
    showTopBar(WIDE)

    compose.onNodeWithText(KERNEL_NAME).assertIsDisplayed()
  }

  /**
   * **The name goes and the status stays**, which is the departure P2w-2 records. The reference
   * sheds its rightmost items into the popup first (`toolbar.tsx:661-700`), so it would push the
   * execution indicator in *before* the kernel name; a phone is this app's primary device and the
   * state is what has to stay glanceable there. Free width in this row at 320dp is 72dp measured
   * (`evidence/p2o-3-two-pane-threshold/`), and a named indicator wants about 110dp.
   */
  @Test
  fun aNarrowBarKeepsTheStatusAndDropsTheName() {
    showTopBar(COMPACT)

    compose.onNodeWithText(KERNEL_NAME).assertDoesNotExist()
    compose.onNodeWithContentDescription(IDLE_DESCRIPTION).assertIsDisplayed()
  }

  /**
   * Even with the name gone. Material's minimum, and the rule `CellTouchTargetTest` pins for every
   * control in the app - a 24dp glyph is not a target.
   */
  @Test
  fun theIndicatorIsBigEnoughToHitOnANarrowBar() {
    showTopBar(COMPACT)

    compose.onNodeWithContentDescription(IDLE_DESCRIPTION)
      .assertIsAtLeastMinimumTouchTarget(compose.density)
  }

  /**
   * **One node carries the name, the state and the click**, which is [UndoRedo]'s rule and
   * load-bearing here for its reason: a `contentDescription` on a child `Icon` lands one level
   * below the node holding the click, so a flow asking for the state cannot press it. It is also
   * the only thing a narrow bar has left to assert on, since the name is not drawn there.
   */
  @Test
  fun theDescriptionNamesTheKernelAndItsState() {
    showTopBar(COMPACT, status = KernelStatus.BUSY)

    compose.onNodeWithContentDescription("Kernel Python 3: Busy").assertIsDisplayed()
  }

  @Test
  fun aNotebookThatHasNotRunSaysItHasNoKernel() {
    showTopBar(WIDE, status = KernelStatus.NO_KERNEL)

    compose.onNodeWithContentDescription("Kernel Python 3: No Kernel").assertIsDisplayed()
  }

  /**
   * Not a picker - `KernelGateway` has no kernelspec listing and the Space has one kernel, so a
   * picker over one entry is chrome. What the control opens is [KernelMenu], the menu the bottom
   * bar's `⋮` already anchors: the thing that says a kernel is stuck is the thing you restart it
   * from.
   */
  @Test
  fun tappingTheIndicatorOpensTheKernelMenu() {
    showTopBar(WIDE)

    compose.onNodeWithContentDescription(IDLE_DESCRIPTION).performClick()

    compose.onNodeWithText("Shut Down Kernel").assertIsDisplayed()
  }

  private fun showTopBar(size: DpSize, status: KernelStatus = KernelStatus.IDLE) {
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(size)) {
        JupyterDroidTheme {
          EditorTopBar(
            canUndo = false,
            canRedo = false,
            onUndo = {},
            onRedo = {},
            onBack = {},
            kernelIndicator = {
              KernelIndicator(status = status, nameShown = size.width >= MEDIUM)
            },
          )
        }
      }
    }
  }

  private companion object {
    /** The emulator's own window, and the narrowest this app is driven at. */
    val COMPACT = DpSize(320.dp, 640.dp)

    /** The tablet's portrait window, which is where the name fits. */
    val WIDE = DpSize(800.dp, 1280.dp)

    val MEDIUM = 600.dp

    /** The shipped kernelspec's `display_name`, which is what the Space advertises. */
    const val KERNEL_NAME = "Python 3"
    const val IDLE_DESCRIPTION = "Kernel Python 3: Idle"
    const val NOTEBOOK_ACTIONS = "Notebook actions"
  }
}
