package com.jupydroid.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.navigation3.runtime.NavKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Editor
import com.jupydroid.app.Explorer
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.fake.FakeDocumentCheckpoints
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.AtFontScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * What a tab's glyph and row do as the system's font size grows: the glyph keeps its proportion to the
 * label beside it, and the 32dp row still holds both, because the label's line is 28dp at twice the scale.
 *
 * The label's line is the one its style states (`bodySmall`'s line height) converted by the same density
 * the glyph is, because the tab clears the label's semantics and no node reports its size.
 */
@RunWith(AndroidJUnit4::class)
class MainAreaTabFontScaleTest {
  @get:Rule val compose = createComposeRule()

  private val tabs: List<NavKey> = listOf(Editor(TITLE))
  private val backStack = mutableStateListOf<NavKey>(Explorer(BASE_SPACE.id), *tabs.toTypedArray())
  private val open = OpenDocuments()
  private val paths = mutableStateMapOf<NavKey, String>()
  private val liveTitles = mutableStateMapOf<NavKey, String>()

  /** What the tab measured at one font scale, in dp, beside the label's line under the same density. */
  private data class TabAt(val glyph: Dp, val labelLine: Dp, val row: Dp) {
    val ratio: Float get() = glyph.value / labelLine.value
  }

  private var fontScale by mutableFloatStateOf(1f)
  private var scaled = Density(1f)
  private var labelLine = Dp.Unspecified

  private fun showTab() {
    compose.setContent {
      JupydroidTheme {
        AtFontScale(fontScale) {
          scaled = LocalDensity.current
          labelLine = with(scaled) { MaterialTheme.typography.bodySmall.lineHeight.toDp() }
          TabbedDocumentPane(
            backStack, open, paths, tabs, liveTitles, FakeWorkspaceRepository(),
            FakeWorkspaceFiles(), twoPane = true, onCloseTab = {}, onOpenDocument = {},
            documentCheckpoints = FakeDocumentCheckpoints(),
          ) { Box(Modifier) }
        }
      }
    }
  }

  /** Recomposes the shown tab at [scale] and reads it; the first call shows the tab. */
  private fun tabAt(scale: Float): TabAt {
    if (!shown) showTab().also { shown = true }
    fontScale = scale
    compose.waitForIdle()
    return TabAt(glyph = glyphSide(scaled), labelLine = labelLine, row = rowHeight(scaled))
  }

  private var shown = false

  private fun glyphSide(density: Density): Dp {
    val bounds = compose.onNodeWithTag(TAB_GLYPH_TAG, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
    return with(density) { bounds.width.toDp() }
  }

  private fun rowHeight(density: Density): Dp {
    val bounds = compose.onNodeWithContentDescription("Tab: $TITLE").fetchSemanticsNode().boundsInRoot
    return with(density) { bounds.height.toDp() }
  }

  @Test
  fun theRowIsThirtyTwoDpAtTheDefaultFontScale() {
    val tab = tabAt(1f)

    assertEquals("row at 1.0 is $tab", 32f, tab.row.value, 0.5f)
  }

  @Test
  fun theGlyphKeepsItsRatioToTheLabelAtEveryLargerFontScale() {
    val atDefault = tabAt(1f).ratio

    val drifted = LARGER_SCALES.associateWith(::tabAt).filterValues { abs(it.ratio - atDefault) > RATIO_TOLERANCE }

    assertEquals(
      "tabs whose glyph over label is not within $RATIO_TOLERANCE of $atDefault at 1.0",
      emptyMap<Float, TabAt>(),
      drifted,
    )
  }

  @Test
  fun theRowStillHoldsTheGrownGlyphAndLabelAtTwiceTheFontScale() {
    val tab = tabAt(2f)

    assertTrue("row ${tab.row} is shorter than its glyph or label: $tab", tab.row >= maxOf(tab.glyph, tab.labelLine))
  }

  private companion object {
    const val TITLE = "a.ipynb"
    val LARGER_SCALES = listOf(1.3f, 1.5f, 2f)

    /**
     * How far the glyph over label ratio may move from its value at 1.0. Measured on `dev36` (API 36) at 1.3, 1.5
     * and 2.0 with the glyph following the label: 0.875 at 1.0 against 0.941, 0.957 and 0.929, a spread of at most
     * 0.082, because Android 14's font curve grows 14sp by 1.86 at 2.0 where it grows the label's 16sp by 1.75.
     * Without it the ratio fell to 0.693, 0.609 and 0.500.
     */
    const val RATIO_TOLERANCE = 0.1f
  }
}
