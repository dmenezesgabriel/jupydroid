package com.jupydroid.app

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.terminal.CellStyle
import com.jupydroid.app.terminal.TerminalEmulator
import com.jupydroid.app.terminal.TerminalNotices
import com.jupydroid.app.terminal.TerminalPalette
import com.jupydroid.app.terminal.TerminalReplies
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.terminal.TerminalCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `TerminalCanvas` draws batched-run glyphs rather than plain `Text` rows - `docs/PLAN.md`'s
 * P4i-2d. Background colour rather than glyph ink is what these sample: a glyph's edges
 * anti-alias, a full-cell background rect does not, so it is the pixel a test can actually pin.
 */
@RunWith(AndroidJUnit4::class)
class TerminalCanvasTest {
  @get:Rule val compose = createComposeRule()

  /**
   * `ESC[42m` (SGR background green) over a space - no glyph ink at all, so the whole cell is the
   * background rect and nothing else. Sampled at the cell's centre, which for
   * [com.jupydroid.app.ui.terminal.terminalCellMetrics]'s real device measurement is always
   * comfortably inside it.
   */
  @Test
  fun aColouredBackgroundDrawsThePalettesResolvedColour() {
    val emulator = silentEmulator(TerminalPalette.REFERENCE)
    val sgr = "$ESC[42m ".toByteArray()
    emulator.append(sgr, sgr.size)

    val image = capture { TerminalCanvas(emulator) }
    val pixel = image.toPixelMap()[
      image.width / (emulator.screen.columns * 2), image.height /
        (emulator.screen.rows * 2),
    ]

    assertEquals(Color(TerminalPalette.REFERENCE.at(GREEN_INDEX)), pixel)
  }

  /**
   * `setContent` runs once per test (`AndroidComposeTestRule`'s own rule - calling it twice
   * throws), so the redraw here is `TerminalPaneScreen`'s own mechanism: `key(redraw)` tears the
   * subtree down and rebuilds it, the way the app forces a fresh draw of an in-place mutation
   * `TerminalCanvas` has no `State` of its own to observe.
   */
  @Test
  fun theCursorIsDrawnOnlyWhileDectcemIsSet() {
    val emulator = silentEmulator(TerminalPalette.REFERENCE)
    var redraw by mutableStateOf(0)
    compose.setContent { JupyterDroidTheme { key(redraw) { TerminalCanvas(emulator) } } }

    val enabled = compose.onRoot().captureToImage()
    val cursorX = enabled.width / (emulator.screen.columns * 2)
    val cursorY = enabled.height / (emulator.screen.rows * 2)
    assertEquals(
      Color(TerminalPalette.REFERENCE.at(CellStyle.COLOR_INDEX_CURSOR)),
      enabled.toPixelMap()[cursorX, cursorY],
    )

    val hideCursor = "$ESC[?25l".toByteArray()
    emulator.append(hideCursor, hideCursor.size)
    redraw++
    compose.waitForIdle()

    val disabled = compose.onRoot().captureToImage()
    assertNotEquals(
      Color(TerminalPalette.REFERENCE.at(CellStyle.COLOR_INDEX_CURSOR)),
      disabled.toPixelMap()[cursorX, cursorY],
    )
  }

  private fun capture(content: @Composable () -> Unit): ImageBitmap {
    compose.setContent { JupyterDroidTheme { content() } }
    return compose.onRoot().captureToImage()
  }

  private fun silentEmulator(palette: TerminalPalette) = TerminalEmulator(
    columns = 5,
    rows = 3,
    notices = object : TerminalNotices {
      override fun onBell() = Unit
      override fun onTitleChanged(oldTitle: String?, newTitle: String?) = Unit
      override fun onColorsChanged() = Unit
      override fun onClipboardCopy(text: String) = Unit
    },
    replies = object : TerminalReplies {
      override fun reply(text: String) = Unit
    },
    palette = palette,
  )

  private companion object {
    const val ESC = "\u001B"
    const val GREEN_INDEX = 2
  }
}
