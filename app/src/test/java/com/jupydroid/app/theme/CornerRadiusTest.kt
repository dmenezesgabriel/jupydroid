package com.jupydroid.app.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The corner-radius scale is the reference's own registered one, pinned by **equality**.
 *
 * Not by a property, the way [SpacingTest] holds its steps to Material's 4dp grid: that scale is a
 * grid this app chose, and a grid is a rule a new step can be checked against. This one is a
 * transcription of six registered sizes, so the only thing that can be checked is whether each
 * number is still the number the reference states - and a transcribed number drifting silently is
 * not hypothetical here. `ColorRamp.LightGray4` held `#DADADA` for months where the reference
 * states `#DADADA99`, and every test over it passed, because they asserted membership rather than
 * value (`docs/DECISIONS.md`).
 *
 * Two of the values are **off** Material's 4dp grid - 2 and 6 - which is why this scale is a
 * second file rather than more steps on [Spacing]. They answer to different references.
 */
class CornerRadiusTest {
  /**
   * `vscode/src/vs/platform/theme/common/sizes/baseSizes.ts:90-108`, re-read at the clone's own
   * sha `3f3380c668d`. Spelled out here rather than derived from [CornerRadius], so the
   * assertion is against the reference and not against the thing under test.
   */
  private val theReferencesScale = mapOf(
    "xSmall" to 2.dp,
    "small" to 4.dp,
    "medium" to 6.dp,
    "large" to 8.dp,
    "xLarge" to 12.dp,
  )

  @Test
  fun theScaleIsTheReferencesOwnValues() =
    assertEquals("baseSizes.ts's registered corner radii", theReferencesScale, CornerRadius.steps)

  /**
   * Ascending, which is what makes the names mean anything: a reader choosing `small` over `medium`
   * has to be choosing by size rather than by memory. [SpacingTest] holds its own scale to this.
   */
  @Test
  fun theStepsAscend() {
    val values = CornerRadius.steps.values.toList()
    assertEquals("the scale in the order it is declared", values.sorted(), values)
  }

  /**
   * **`cornerRadius.circle` is deliberately not on this scale, and that is asserted rather than
   * only commented** - a later edit that "completes" the transcription has to fail here rather
   * than pass quietly.
   *
   * The reference registers it at **9999px** (`baseSizes.ts:110-112`), which is CSS's way of
   * spelling a shape Compose names outright: `androidx.compose.foundation.shape.CircleShape`. A
   * `9999.dp` val no consumer could legitimately draw is exactly the field
   * `scripts/check_palette_consumers.py` exists to refuse, and P7a-1's "two fields, not six" is
   * the same rule applied to a palette.
   */
  @Test
  fun theCircleTokenIsNotOnTheScale() {
    val enormous = CornerRadius.steps.filterValues { it > 100.dp }
    assertEquals("a CSS pill radius transcribed as a Dp", emptyMap<String, Dp>(), enormous)
    assertEquals("circle is Compose's own shape, not a step", false, "circle" in CornerRadius.steps)
  }
}
