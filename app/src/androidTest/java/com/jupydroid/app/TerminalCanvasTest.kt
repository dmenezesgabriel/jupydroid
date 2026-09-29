package com.jupydroid.app

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import androidx.core.graphics.Insets
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.terminal.CellStyle
import com.jupydroid.app.terminal.TerminalEmulator
import com.jupydroid.app.terminal.TerminalNotices
import com.jupydroid.app.terminal.TerminalPalette
import com.jupydroid.app.terminal.TerminalReplies
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.editor.codeTextStyle
import com.jupydroid.app.ui.terminal.TerminalCanvas
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * `TerminalCanvas` draws batched-run glyphs rather than plain `Text` rows
 * P4i-2d. Background colour rather than glyph ink is what these sample: a glyph's edges
 * anti-alias, a full-cell background rect does not, so it is the pixel a test can actually pin.
 */
@RunWith(AndroidJUnit4::class)
class TerminalCanvasTest {
  @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

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
    var cell: IntSize? = null

    // P4i-3a: `TerminalCanvas` now fills whatever bounded size its caller gives it rather than
    // sizing itself to the grid, so the sample point is the real measured cell centre - reported
    // through `onGridSizeChanged` - not `image.width / columns`, which only held while the two
    // were the same thing.
    val image = capture {
      TerminalCanvas(emulator, onGridSizeChanged = { _, measured ->
        cell = measured
      }, modifier = Modifier.fillMaxSize())
    }
    val (cellWidth, cellHeight) = requireNotNull(cell) { "onGridSizeChanged was never called" }
    val pixel = image.toPixelMap()[cellWidth / 2, cellHeight / 2]

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
    var cell: IntSize? = null
    compose.setContent {
      JupyterDroidTheme {
        key(redraw) {
          TerminalCanvas(emulator, onGridSizeChanged = { _, measured ->
            cell = measured
          }, modifier = Modifier.fillMaxSize())
        }
      }
    }
    compose.waitForIdle()

    val enabled = compose.onRoot().captureToImage()
    val (cellWidth, cellHeight) = requireNotNull(cell) { "onGridSizeChanged was never called" }
    val cursorX = cellWidth / 2
    val cursorY = cellHeight / 2
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

  /**
   * P4i-3a: the grid this reports is the container's own measured size, not
   * `screen.columns`/`rows` echoed back - [silentEmulator] is 5x3, and any real device's canvas
   * area holds far more than that, so a placeholder pass-through would fail this outright.
   */
  @Test
  fun reportsARealMeasuredGridRatherThanTheEmulatorsOwnSize() {
    val emulator = silentEmulator(TerminalPalette.REFERENCE)
    var reportedGrid: IntSize? = null
    var reportedCell: IntSize? = null

    compose.setContent {
      JupyterDroidTheme {
        TerminalCanvas(
          emulator,
          onGridSizeChanged = { grid, cell ->
            reportedGrid = grid
            reportedCell = cell
          },
        )
      }
    }
    compose.waitForIdle()

    val grid = requireNotNull(reportedGrid) { "onGridSizeChanged was never called" }
    val cell = requireNotNull(reportedCell)
    assertTrue("cell $cell should be a real, positive measurement", cell.width > 0 && cell.height > 0)
    assertTrue(
      "grid $grid should reflect a real device's canvas area, not the 5x3 test emulator",
      grid.width > emulator.screen.columns && grid.height > emulator.screen.rows,
    )
  }

  /**
   * the keyboard opening genuinely shrinks the grid, rather than
   * `TerminalCanvas` adding the IME's own inset back to cancel the shrink out (P4i-3a's own
   * compensation, retired - `docs/DECISIONS.md`'s P4j-3b entry). A synthetic `WindowInsetsCompat`
   * dispatched onto the activity's `decorView` stands in for a real keyboard - the headless
   * instrumentation this runs under has no soft keyboard of its own to open.
   */
  @Test
  fun theGridShrinksWhenTheImeInsetOpens() {
    val emulator = silentEmulator(TerminalPalette.REFERENCE)
    var reportedGrid: IntSize? = null

    compose.setContent {
      JupyterDroidTheme {
        TerminalCanvas(
          emulator,
          onGridSizeChanged = { grid, _ -> reportedGrid = grid },
          modifier = Modifier.fillMaxSize(),
        )
      }
    }
    compose.waitForIdle()
    val withoutIme = requireNotNull(reportedGrid) { "onGridSizeChanged was never called" }

    compose.runOnUiThread {
      val withIme = WindowInsetsCompat.Builder()
        .setInsets(WindowInsetsCompat.Type.ime(), Insets.of(0, 0, 0, IME_INSET_PIXELS))
        .setVisible(WindowInsetsCompat.Type.ime(), true)
        .build()
      ViewCompat.dispatchApplyWindowInsets(compose.activity.window.decorView, withIme)
    }
    compose.waitForIdle()

    val withIme = requireNotNull(reportedGrid)
    assertTrue(
      "grid $withIme should be shorter than $withoutIme once the IME opens",
      withIme.height < withoutIme.height,
    )
  }

  /**
   * the terminal's own font-size setting reaches [TerminalCanvas] through
   * a `textStyle` parameter, plumbed into [com.jupydroid.app.ui.terminal.terminalCellMetrics]'s
   * existing seam - so a larger size must measure a wider cell, not merely accept the parameter.
   */
  @Test
  fun aLargerTextStyleMeasuresAWiderCell() {
    val emulator = silentEmulator(TerminalPalette.REFERENCE)
    var smallCell: IntSize? = null
    var largeCell: IntSize? = null

    compose.setContent {
      JupyterDroidTheme {
        TerminalCanvas(
          emulator,
          textStyle = codeTextStyle(fontSize = 10.sp),
          onGridSizeChanged = { _, cell -> smallCell = cell },
        )
      }
      JupyterDroidTheme {
        TerminalCanvas(
          emulator,
          textStyle = codeTextStyle(fontSize = 30.sp),
          onGridSizeChanged = { _, cell -> largeCell = cell },
        )
      }
    }
    compose.waitForIdle()

    val small = requireNotNull(smallCell) { "the 10sp onGridSizeChanged was never called" }
    val large = requireNotNull(largeCell) { "the 30sp onGridSizeChanged was never called" }
    assertTrue("a 30sp cell $large should be wider than a 10sp cell $small", large.width > small.width)
  }

  /**
   * [TerminalCanvas]'s new `topRow` parameter actually shifts which
   * external rows it draws, not merely accepted and ignored. `key(topRow)` remounts the subtree
   * the same way [theCursorIsDrawnOnlyWhileDectcemIsSet] already does, since `TerminalCanvas` has
   * no `State` of its own to recompose from a parameter change alone.
   */
  @Test
  fun aNonZeroTopRowRendersTheScrolledBackRowInstead() {
    val emulator = silentEmulator(TerminalPalette.REFERENCE)
    writeRow(emulator, row = 0, text = "old")
    emulator.screen.grid.scrollDownOneLine(0, emulator.screen.rows, CellStyle.NORMAL)
    writeRow(emulator, row = 0, text = "new")
    var topRow by mutableStateOf(0)

    compose.setContent {
      JupyterDroidTheme {
        key(topRow) {
          TerminalCanvas(emulator, topRow = topRow, modifier = Modifier.fillMaxSize())
        }
      }
    }
    compose.waitForIdle()
    compose.onNodeWithText("new").assertExists()
    compose.onNodeWithText("old").assertDoesNotExist()

    // A 1-row scroll on a 3-row screen still keeps "new" (now at external row 0) inside the
    // 3-row window `[-1, 2)` - the reference's own behaviour, not a bug this fix should hide.
    // What proves the scroll is "old" (external row -1, out of the `[0, 3)` window a moment ago)
    // becoming visible without anything having changed the grid itself.
    topRow = -1
    compose.waitForIdle()
    compose.onNodeWithText("old").assertExists()
  }

  /**
   * Termux's own `TerminalRenderer.render` side effect, made an explicit condition here: the
   * cursor's row is always among the *live* screen's external rows, so once [TerminalCanvas]
   * draws a window that has scrolled away from it, the cursor is not among the rows drawn.
   */
  @Test
  fun theCursorIsHiddenOnceScrolledAwayFromTheLiveScreen() {
    val emulator = silentEmulator(TerminalPalette.REFERENCE)
    emulator.screen.grid.scrollDownOneLine(0, emulator.screen.rows, CellStyle.NORMAL)
    var topRow by mutableStateOf(0)
    var cell: IntSize? = null

    compose.setContent {
      JupyterDroidTheme {
        key(topRow) {
          TerminalCanvas(
            emulator,
            topRow = topRow,
            onGridSizeChanged = { _, measured -> cell = measured },
            modifier = Modifier.fillMaxSize(),
          )
        }
      }
    }
    compose.waitForIdle()
    val (cellWidth, cellHeight) = requireNotNull(cell) { "onGridSizeChanged was never called" }
    val cursorX = cellWidth / 2
    val cursorY = cellHeight / 2
    val atLiveTop = compose.onRoot().captureToImage()
    assertEquals(
      Color(TerminalPalette.REFERENCE.at(CellStyle.COLOR_INDEX_CURSOR)),
      atLiveTop.toPixelMap()[cursorX, cursorY],
    )

    topRow = -1
    compose.waitForIdle()
    val scrolledBack = compose.onRoot().captureToImage()
    assertNotEquals(
      Color(TerminalPalette.REFERENCE.at(CellStyle.COLOR_INDEX_CURSOR)),
      scrolledBack.toPixelMap()[cursorX, cursorY],
    )
  }

  private fun writeRow(emulator: TerminalEmulator, row: Int, text: String) {
    for ((column, character) in text.withIndex()) {
      emulator.screen.grid.setChar(column, row, character.code, CellStyle.NORMAL)
    }
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

    /** Comfortably larger than any real cell height, so at least one row is guaranteed to go. */
    const val IME_INSET_PIXELS = 600
  }
}
