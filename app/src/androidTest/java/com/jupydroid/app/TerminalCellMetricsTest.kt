package com.jupydroid.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.terminal.TerminalEmulator
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.editor.codeTextStyle
import com.jupydroid.app.ui.terminal.terminalCellMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * [terminalCellMetrics] measures a real glyph on this device rather than restating
 * [TerminalEmulator.DEFAULT_CELL_WIDTH_PIXELS]/`HEIGHT_PIXELS` (13 by 15, the reference's own
 * test-suite constant, never a real device's) - 2c. Needs real font
 * rendering, which only runs instrumented in this project (no Robolectric - `EditorThemeTest`'s
 * own reason).
 */
@RunWith(AndroidJUnit4::class)
class TerminalCellMetricsTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun measuresARealGlyphRatherThanTheReferencesPlaceholder() {
    var first: Size? by mutableStateOf(null)
    var second: Size? by mutableStateOf(null)

    compose.setContent {
      JupyterDroidTheme {
        first = terminalCellMetrics()
        second = terminalCellMetrics()
      }
    }

    val (width, height) = requireNotNull(first)
    assertEquals("repeated measurement of the same style", first, second)
    assertTrue("width $width should be positive", width > 0)
    assertTrue("height $height should be positive", height > 0)
    assertTrue(
      "($width, $height) looks like the reference's own placeholder, not a device measurement",
      width != TerminalEmulator.DEFAULT_CELL_WIDTH_PIXELS.toFloat() ||
        height != TerminalEmulator.DEFAULT_CELL_HEIGHT_PIXELS.toFloat(),
    )
  }

  /**
   * A lone glyph's layout is rounded to a whole pixel (14.0 where a run of them steps 14.81 on the
   * SM-X216B), so the pitch is read the way a run draws it: what one more glyph adds to a run.
   * The two lengths cancel a constant offset the run's right edge carries.
   */
  @Test
  fun theCellWidthIsThePitchARunOfGlyphsIsDrawnAt() {
    var cell: Size? = null
    var pitch = 0f

    compose.setContent {
      JupyterDroidTheme {
        val measurer = rememberTextMeasurer()
        val style = codeTextStyle()
        fun right(glyphs: Int) = measurer.measure("X".repeat(glyphs), style).getLineRight(0)
        pitch = (right(2 * RUN_GLYPHS) - right(RUN_GLYPHS)) / RUN_GLYPHS
        cell = terminalCellMetrics()
      }
    }

    val width = requireNotNull(cell).width
    assertTrue("cell width $width should be the drawn pitch $pitch", abs(width - pitch) < PITCH_TOLERANCE_PX)
  }

  private companion object {
    const val RUN_GLYPHS = 64
    const val PITCH_TOLERANCE_PX = 0.05f
  }
}
