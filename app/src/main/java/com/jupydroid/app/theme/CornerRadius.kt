package com.jupydroid.app.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The corners this app rounds, transcribed from the reference's own registered scale.
 *
 * `vscode/src/vs/platform/theme/common/sizes/baseSizes.ts:90-108` registers five radii by name and
 * a sixth this file declines - see below. Before this, the one rounded thing in the app carried
 * `TOGGLE_CORNER = 8.dp`, a number chosen against nothing; 8px is the reference's
 * `cornerRadius.large`, so it was right by accident and unable to say so.
 *
 * **This is not [Spacing], and the two must not be folded.** [Spacing] is Material's 4dp grid,
 * which this app chose and which a new step can be checked *against*. These are transcribed sizes
 * and two of them - 2 and 6 - are off that grid, so putting them on it would make the grid a lie.
 * Each scale states which reference it answers to; that is the same rule `theme/ChromePalette.kt`
 * and `theme/EditorPalette.kt` follow for colour.
 *
 * **No workbench *part* gets a radius.** The ask was for "discrete borders with
 * border-radius", and the reference has the borders and not the radius: every `border-radius` under
 * `parts/sidebar/`, `parts/activitybar/` and `parts/editor/media/` is a badge, a floating panel, a
 * drop target or a modal. Fidelity won, the way it did when parity outranked the contrast floor
 * (ADR-0041).
 *
 * **`cornerRadius.circle` (9999px, `:110-112`) is not here.** It is CSS's way of spelling a shape
 * Compose names outright - `androidx.compose.foundation.shape.CircleShape` - so transcribing it
 * would add a `9999.dp` val no consumer could legitimately draw, which is exactly the field
 * `scripts/check_palette_consumers.py` refuses for a colour and exactly what "two fields, not
 * six" refused for a palette. `CornerRadiusTest` asserts the absence rather than trusting this
 * paragraph.
 *
 * ```
 * Modifier.clip(RoundedCornerShape(CornerRadius.large))
 * ```
 */
object CornerRadius {
  /** `cornerRadius.xSmall` - *"very compact UI elements"* (`baseSizes.ts:94-96`). */
  val xSmall: Dp = 2.dp

  /** `cornerRadius.small` - *"compact UI elements"* (`baseSizes.ts:98-100`). */
  val small: Dp = 4.dp

  /** `cornerRadius.medium` - the reference's own *"base corner radius"* (`baseSizes.ts:90-92`). */
  val medium: Dp = 6.dp

  /** `cornerRadius.large` - *"prominent UI elements"* (`baseSizes.ts:102-104`). */
  val large: Dp = 8.dp

  /** `cornerRadius.xLarge` - *"very prominent UI elements"* (`baseSizes.ts:106-108`). */
  val xLarge: Dp = 12.dp

  /** The scale in order, for [com.jupydroid.app.theme.CornerRadiusTest] and for nothing else. */
  val steps: Map<String, Dp> = mapOf(
    "xSmall" to xSmall,
    "small" to small,
    "medium" to medium,
    "large" to large,
    "xLarge" to xLarge,
  )
}
