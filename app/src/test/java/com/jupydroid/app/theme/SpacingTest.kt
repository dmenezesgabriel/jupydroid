package com.jupydroid.app.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The spacing scale is on Material's 4dp grid, and it is the only place a gap is named.
 *
 * Written from a measurement rather than a preference: before it there were 63 `.dp` literals
 * across 20 files in 16 distinct values, **13 of them off the grid** - `14.dp` three times in the
 * explorer and its recents, plus `6`, `3`, `2`, `5`, `18`, `1`. Nothing was wrong with any one of
 * them; what was wrong is that a gap had no name, so the next one was chosen by eye against
 * whatever happened to be nearby.
 *
 * The grid itself is Material's, and it is the reason the assertion is `% 4` rather than a list of
 * blessed numbers: a scale that can be extended without editing this test is one that can drift.
 */
class SpacingTest {
  @Test
  fun everyStepIsOnTheFourDpGrid() {
    val offGrid = Spacing.steps.filterValues { it.value.toInt() % 4 != 0 || it.value <= 0f }
    assertEquals("steps off Material's 4dp grid", emptyMap<String, Dp>(), offGrid)
  }

  @Test
  fun everyStepIsWholeDp() {
    val fractional = Spacing.steps.filterValues { it.value != it.value.toInt().toFloat() }
    assertEquals("steps that are not a whole dp", emptyMap<String, Dp>(), fractional)
  }

  /**
   * Ascending, and that is what makes the names mean anything: `sm` has to be smaller than `md`
   * for a reader choosing between them to be choosing by size rather than by memory.
   */
  @Test
  fun theStepsAscend() {
    val values = Spacing.steps.values.toList()
    assertEquals("the scale in the order it is declared", values.sorted(), values)
  }

  @Test
  fun theScaleStartsAtTheGridsOwnStep() = assertEquals(4.dp, Spacing.steps.values.first())
}
