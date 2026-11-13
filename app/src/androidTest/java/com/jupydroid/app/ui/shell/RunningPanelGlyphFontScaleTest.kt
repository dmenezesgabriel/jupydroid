package com.jupydroid.app.ui.shell

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.Editor
import com.jupydroid.app.RunningTab
import com.jupydroid.app.data.fake.FakeKernelSessions
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.AtFontScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * What the Running panel's two glyphs do as the system's font size grows: each keeps its proportion to the
 * label beside it, and the row or header that holds them still holds both. The close button's place under a
 * large font is `RunningSidePanelFontScaleTest`'s.
 */
@RunWith(AndroidJUnit4::class)
class RunningPanelGlyphFontScaleTest {
  @get:Rule val compose = createComposeRule()

  private val registry = KernelSessionRegistry()
  private var fontScale by mutableFloatStateOf(1f)
  private var scaled = Density(1f)
  private var shown = false

  /** A glyph and the text it leads, found by what each is tagged or says, and the node that holds both. */
  private enum class Surface(val glyphTag: String, val label: String, val container: SemanticsMatcher) {
    TAB_ROW(ROW_ICON_TAG, TITLE, hasContentDescription("Open tab: $TITLE")),
    SECTION_HEADER(SECTION_CARET_TAG, "Open Tabs", hasText("Open Tabs")),
  }

  /** What one surface measured at one font scale, in dp: the glyph, its label's line and the container. */
  private data class SurfaceAt(val glyph: Dp, val labelLine: Dp, val container: Dp) {
    val ratio: Float get() = glyph.value / labelLine.value
  }

  private fun showPanel() = compose.setContent {
    JupyterDroidTheme {
      AtFontScale(fontScale) {
        scaled = LocalDensity.current
        RunningSidePanel(
          kernelSessions = FakeKernelSessions(registry),
          terminalGateway = FakeTerminalGateway(),
          openTabs = listOf(RunningTab(Editor(TITLE), TITLE)),
          onActivateTab = {},
          onCloseTab = {},
          saveAllEnabled = false,
          onSaveAllTabs = {},
          onOpenSession = {},
          onOpenTerminal = {},
        )
      }
    }
  }

  /** Recomposes the shown panel at [scale] and reads [surface]; the first call shows the panel. */
  private fun surfaceAt(surface: Surface, scale: Float): SurfaceAt {
    if (!shown) showPanel().also { shown = true }
    fontScale = scale
    compose.waitForIdle()
    return SurfaceAt(
      glyph = height(compose.onAllNodes(hasTestTag(surface.glyphTag), useUnmergedTree = true)[0]),
      labelLine = height(compose.onNode(hasText(surface.label), useUnmergedTree = true)),
      container = height(compose.onNode(surface.container)),
    )
  }

  private fun height(node: SemanticsNodeInteraction): Dp =
    with(scaled) { node.fetchSemanticsNode().boundsInRoot.height.toDp() }

  @Test
  fun eachGlyphKeepsItsRatioToItsLabelAtEveryLargerFontScale() {
    val drifted = Surface.entries.associateWith { surface ->
      val atDefault = surfaceAt(surface, 1f).ratio
      LARGER_SCALES.associateWith { surfaceAt(surface, it) }.filterValues {
        abs(it.ratio - atDefault) > RATIO_TOLERANCE
      }.mapValues { (_, reading) -> "$reading against $atDefault at 1.0" }
    }.filterValues { it.isNotEmpty() }

    assertEquals("glyph over label not within $RATIO_TOLERANCE of its value at 1.0", emptyMap<Surface, Any>(), drifted)
  }

  @Test
  fun theRowAndTheHeaderStillHoldTheirGrownGlyphAndLabelAtTwiceTheFontScale() {
    Surface.entries.forEach { surface ->
      val reading = surfaceAt(surface, 2f)

      assertTrue(
        "$surface ${reading.container} is shorter than its glyph or label: $reading",
        reading.container >= maxOf(reading.glyph, reading.labelLine),
      )
    }
  }

  private companion object {
    const val TITLE = "a.ipynb"
    val LARGER_SCALES = listOf(1.3f, 1.5f, 2f)

    /**
     * How far the glyph over label ratio may move from its value at 1.0. Measured on `dev36` (API 36) with each
     * 16dp glyph grown by its 14sp label's own conversion: 0.800 at 1.0 against 0.778, 0.781 and 0.789 at 1.3,
     * 1.5 and 2.0 on both surfaces, a spread of 0.022 that is mostly whole-pixel rounding of a 27 to 38dp
     * label. With no change the ratio fell to 0.593, 0.500 and 0.421.
     */
    const val RATIO_TOLERANCE = 0.05f
  }
}
