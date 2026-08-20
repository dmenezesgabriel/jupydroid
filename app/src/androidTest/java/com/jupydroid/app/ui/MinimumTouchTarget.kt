package com.jupydroid.app.ui

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import org.junit.Assert.assertTrue

/** Material's minimum interactive size, the same value `minimumInteractiveComponentSize` uses. */
private val MINIMUM_TARGET = 48.dp

/**
 * Asserts a control **reserves** at least 48dp of layout, which is the thing that can regress.
 *
 * **Reserved space, not expanded bounds, and the difference is the whole point.** Compose expands
 * the *hit area* of any clickable node to 48dp on its own - so a 32dp row is hittable over 48dp
 * right up until a neighbour claims those pixels, and then it is not. `NodeCoordinator`'s own
 * account of the collision (`outOfBoundsHit`): *"hit in this node's … minimum touch target bounds
 * overlaps with a direct hit in the other node. The node with direct hit will get the event."*
 * A folded cell sits in a `LazyColumn` between an editor and a header, both of which take direct
 * hits, so the slop is exactly the part that cannot be relied on. Material says the same in
 * `minimumInteractiveComponentSize`'s own KDoc: *"This modifier is not needed for touch target
 * expansion to happen. It only affects layout, to make sure there is adequate space for touch
 * target expansion."*
 *
 * **Only for a control that is its own layout node** - a bare `clickable`, or one carrying
 * `minimumInteractiveComponentSize()`. On an `IconButton` or a `TextButton` this reads 40dp against
 * a control that is perfectly correct: the merged semantics node is the `clickable` *inside*
 * `.size(IconButtonDefaults.smallContainerSize())`, and the 48dp reservation is the
 * `minimumInteractiveComponentSize()` node outside it, which carries no semantics of its own
 * (`material3/IconButton.kt`, `IconButtonImpl`). That is the failure this file's other assertion
 * was written to get around - see [assertIsAtLeastMinimumTouchTarget].
 *
 * ```
 * compose.onNodeWithContentDescription(COLLAPSED_SOURCE).assertReservesMinimumTouchTarget()
 * ```
 */
internal fun SemanticsNodeInteraction.assertReservesMinimumTouchTarget() {
  val bounds = getUnclippedBoundsInRoot()
  assertTrue(
    "reserves ${bounds.width} x ${bounds.height}, below the $MINIMUM_TARGET minimum",
    bounds.width >= MINIMUM_TARGET && bounds.height >= MINIMUM_TARGET,
  )
}

/**
 * Asserts a control accepts a press over at least 48dp.
 *
 * **It cannot fail, and that is not what this file used to say.** `touchBoundsInRoot` is the
 * *expanded* rectangle: `SemanticsNode.touchBoundsInRoot` takes the expanded path for any node
 * carrying an `OnClick` action (`node/SemanticsModifierNode.kt`, `useMinimumTouchTarget`), and
 * `NodeCoordinator.touchBoundsInRoot` pads the measured bounds out by
 * `calculateMinimumTouchTargetPadding` - `maxOf(0f, (48dp - measured) / 2)`. So this computes
 * `max(measured, 48dp) >= 48dp` and passes on anything clickable, at any size.
 *
 * The previous docstring claimed the opposite - that a bare `clickable` *"would have a real 24dp
 * target and nothing but this to notice"*. It was wrong, and it was steering the plan:
 * `HiddenCellsRowTest` shipped green against a 40dp row on the strength of it, and P2l-4 was
 * written expecting this assertion to go red. What notices that case is
 * [assertReservesMinimumTouchTarget].
 *
 * What survives is a weaker claim worth one line: the node is clickable, and so it does get the
 * expansion. Kept on the `IconButton`s and `TextButton`s that already reserve their 48dp - where
 * the stronger assertion cannot be asked, for the reason above.
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
