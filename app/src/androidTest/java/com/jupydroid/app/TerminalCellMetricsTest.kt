package com.jupydroid.app

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.terminal.TerminalEmulator
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.terminal.terminalCellMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

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
    var first: IntSize? by mutableStateOf(null)
    var second: IntSize? by mutableStateOf(null)

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
      width != TerminalEmulator.DEFAULT_CELL_WIDTH_PIXELS || height != TerminalEmulator.DEFAULT_CELL_HEIGHT_PIXELS,
    )
  }
}
