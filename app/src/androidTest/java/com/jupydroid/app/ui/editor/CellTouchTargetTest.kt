package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every control on a cell header accepts a press over at least 48dp, Material's minimum.
 *
 * **This pins behaviour that was already correct, and the measurement is the reason it says so.**
 * The header used to wrap each `IconButton` in `Modifier.size(32.dp)`, which looked like an
 * accessibility bug and was written up as one. It was not: `IconButton` applies
 * `minimumInteractiveComponentSize()` *outside* the caller's modifier, so the pressable area
 * stayed 48dp and only the ripple shrank. Measured on device with the 32dp wrapper still in
 * place - `touch target is 48.0.dp x 48.0.dp`.
 *
 * The wrapper is gone anyway, for the reason that survives: 32dp is smaller than Material's 40dp
 * default and made the header look cramped beside every other control in the app. That is taste,
 * not accessibility, and the two should not be confused.
 *
 * What this test is worth keeping for is the thing that *could* break: a control built from a
 * bare `clickable` instead of an `IconButton`, or one that opts out of the minimum, would have a
 * real 24dp target and no test but this one would notice.
 */
@RunWith(AndroidJUnit4::class)
class CellTouchTargetTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theRunAndMenuButtonsAreBigEnoughToHit() {
    compose.setContent { CellHeader(CODE_CELL, executionCount = null, running = false, {}, {}, {}) }

    compose.onNodeWithContentDescription("Run cell").assertIsAtLeastMinimumTouchTarget()
    compose.onNodeWithContentDescription("Cell actions").assertIsAtLeastMinimumTouchTarget()
  }

  /** Stop replaces Run mid-execution, so it is a separate composition and a separate risk. */
  @Test
  fun theStopButtonIsBigEnoughToHit() {
    compose.setContent { CellHeader(CODE_CELL, executionCount = null, running = true, {}, {}, {}) }

    compose.onNodeWithContentDescription("Stop cell").assertIsAtLeastMinimumTouchTarget()
  }

  /** A markdown cell composes a different branch of the header, with its own Run button. */
  @Test
  fun aMarkdownCellsControlsAreBigEnoughToHit() {
    compose.setContent { CellHeader(MARKDOWN_CELL, executionCount = null, running = false, {}, {}, {}) }

    compose.onNodeWithContentDescription("Run cell").assertIsAtLeastMinimumTouchTarget()
    compose.onNodeWithContentDescription("Cell actions").assertIsAtLeastMinimumTouchTarget()
  }

  /**
   * `touchBoundsInRoot`, not the layout bounds, and the difference is the whole point.
   *
   * An `IconButton` lays out at 40dp - `IconButtonTokens.StateLayerSize`, the size of the ripple
   * - and `Modifier.minimumInteractiveComponentSize()` widens the area that *accepts a pointer*
   * to 48dp without changing what is drawn. So `getUnclippedBoundsInRoot` reports 40dp for a
   * button that is perfectly hittable, and asserting on it would demand a visibly oversized
   * control to satisfy a rule about fingers. Asked the first way, these tests failed at 40dp
   * against buttons that were already correct.
   */
  private fun SemanticsNodeInteraction.assertIsAtLeastMinimumTouchTarget() {
    val touch = fetchSemanticsNode().touchBoundsInRoot
    val width = with(compose.density) { touch.width.toDp() }
    val height = with(compose.density) { touch.height.toDp() }
    assertTrue(
      "touch target is $width x $height, below the $MINIMUM_TARGET minimum",
      width >= MINIMUM_TARGET && height >= MINIMUM_TARGET,
    )
  }

  private companion object {
    /** Material's minimum interactive size, the same value `minimumInteractiveComponentSize` uses. */
    val MINIMUM_TARGET = 48.dp

    val CODE_CELL = Cell(id = "c1", kind = CellKind.CODE, source = "print(1)")
    val MARKDOWN_CELL = Cell(id = "m1", kind = CellKind.MARKDOWN, source = "# hi")
  }
}
