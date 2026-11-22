package com.jupydroid.app.ui.shell

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.AtFontScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * What a More rail row's glyph and row do as the system's font size grows: the glyph keeps its
 * proportion to the label beside it, and the row still holds both.
 *
 * `WideNavigationRailItem` places its icon at the size the icon measured and clips nothing
 * (`NavigationItem.kt` in material3 1.4.0), so a larger glyph needs no replacement slot; the second
 * test is what reads that on a device.
 */
@RunWith(AndroidJUnit4::class)
class MoreRailFontScaleTest {
  @get:Rule val compose = createComposeRule()

  private var fontScale by mutableFloatStateOf(1f)
  private var scaled = Density(1f)
  private var shown = false

  /** What the row measured at one font scale, in dp: the glyph, the label's line and the row. */
  private data class RowAt(val glyph: Dp, val labelLine: Dp, val row: Dp) {
    val ratio: Float get() = glyph.value / labelLine.value
  }

  private fun showRow() = compose.setContent {
    JupydroidTheme {
      AtFontScale(fontScale) {
        scaled = LocalDensity.current
        MoreRailRow(LABEL, Icons.Filled.Terminal, onClick = {})
      }
    }
  }

  /** Recomposes the shown row at [scale] and reads it; the first call shows the row. */
  private fun rowAt(scale: Float): RowAt {
    if (!shown) showRow().also { shown = true }
    fontScale = scale
    compose.waitForIdle()
    return RowAt(
      glyph = height(compose.onNodeWithTag(MORE_ROW_GLYPH_TAG, useUnmergedTree = true)),
      labelLine = height(compose.onNodeWithText(LABEL, useUnmergedTree = true)),
      row = height(compose.onNodeWithText(LABEL)),
    )
  }

  /** In the merged tree the label's node is the row's own, so [rowAt] reads the row from it and the label unmerged. */
  private fun height(node: SemanticsNodeInteraction): Dp =
    with(scaled) { node.fetchSemanticsNode().boundsInRoot.height.toDp() }

  @Test
  fun theGlyphKeepsItsRatioToTheLabelAtEveryLargerFontScale() {
    val atDefault = rowAt(1f).ratio

    val drifted = LARGER_SCALES.associateWith(::rowAt).filterValues { abs(it.ratio - atDefault) > RATIO_TOLERANCE }

    assertEquals(
      "rows whose glyph over label is not within $RATIO_TOLERANCE of $atDefault at 1.0",
      emptyMap<Float, RowAt>(),
      drifted,
    )
  }

  @Test
  fun theRowHoldsTheGrownGlyphAndLabelAtTwiceTheFontScale() {
    val row = rowAt(2f)

    assertTrue("row ${row.row} is shorter than its glyph or label: $row", row.row >= maxOf(row.glyph, row.labelLine))
  }

  private companion object {
    const val LABEL = "New terminal"
    val LARGER_SCALES = listOf(1.3f, 1.5f, 2f)

    /**
     * How far the glyph over label ratio may move from its value at 1.0. Measured on `dev36` (API 36) with the
     * glyph grown by the label's own conversion: 1.200 at 1.0 against 1.185, 1.188 and 1.184 at 1.3, 1.5 and 2.0,
     * a spread of 0.016 that is mostly whole-pixel rounding of a 27 to 38dp label. Converted from its own 24sp
     * instead, the glyph reached only 0.947 at 2.0, because Android 14's curve grows 24sp by 1.5 where it grows
     * the 14sp label by 1.9; with no change at all, 0.63.
     */
    const val RATIO_TOLERANCE = 0.05f
  }
}
