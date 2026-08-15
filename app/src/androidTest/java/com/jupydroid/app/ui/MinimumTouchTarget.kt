package com.jupydroid.app.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue

/** Material's minimum interactive size, the same value `minimumInteractiveComponentSize` uses. */
private val MINIMUM_TARGET = 48.dp

/**
 * Asserts a control accepts a press over at least 48dp.
 *
 * **`touchBoundsInRoot`, not the layout bounds, and the difference is the whole point.**
 * An `IconButton` lays out at 40dp - `IconButtonTokens.StateLayerSize`, the size of the ripple -
 * and `Modifier.minimumInteractiveComponentSize()` widens the area that *accepts a pointer* to
 * 48dp without changing what is drawn. So `getUnclippedBoundsInRoot` (and with it
 * `assertWidthIsAtLeast`) reports 40dp for a button that is perfectly hittable, and asserting on
 * it would demand a visibly oversized control to satisfy a rule about fingers. Asked the first
 * way, `CellTouchTargetTest` failed at 40dp against buttons that were already correct - and so did
 * `NotebookBarShutdownTest`, which is why this moved out of that suite and into a file of its own.
 *
 * What it is worth keeping for is the thing that *could* break: a control built from a bare
 * `clickable` instead of an `IconButton`, or one that opts out of the minimum, would have a real
 * 24dp target and nothing but this to notice.
 *
 * ```
 * compose.onNodeWithContentDescription("Cell actions").assertIsAtLeastMinimumTouchTarget(compose.density)
 * ```
 */
internal fun SemanticsNodeInteraction.assertIsAtLeastMinimumTouchTarget(density: Density) {
  val touch = fetchSemanticsNode().touchBoundsInRoot
  val width = with(density) { touch.width.toDp() }
  val height = with(density) { touch.height.toDp() }
  assertTrue(
    "touch target is $width x $height, below the $MINIMUM_TARGET minimum",
    width >= MINIMUM_TARGET && height >= MINIMUM_TARGET,
  )
}
