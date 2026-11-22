package com.jupydroid.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * An image output's drawn size, which is JupyterLab's `<img>`: the width the kernel asked for, else
 * its own with one image pixel to a dp, capped at the column, the height by the aspect ratio, at the
 * column's start (jupyterlab@7f0e690 packages/rendermime/src/renderers.ts `renderImage`,
 * `style/base.css` `.jp-RenderedImage img`; ADR-0086).
 */
@RunWith(AndroidJUnit4::class)
class CellOutputImageTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun anImageWithNoWidthDrawsAtItsOwnPixelsInDpAtTheColumnsStart() {
    showImage(pngOf(STRIP_WIDTH, STRIP_HEIGHT), width = null, column = WIDE_COLUMN)

    val image = imageBounds()
    assertEquals(STRIP_WIDTH.toFloat(), image.width.value, TOLERANCE)
    assertEquals(STRIP_HEIGHT.toFloat(), image.height.value, TOLERANCE)
    assertEquals(columnBounds().left.value, image.left.value, TOLERANCE)
  }

  /** `height` is not read: JupyterLab's `height: auto` outranks the attribute, so the ratio holds. */
  @Test
  fun anImageWithAWidthDrawsAtThatWidthAndItsOwnAspectRatio() {
    showImage(pngOf(STRIP_WIDTH, STRIP_HEIGHT), width = ASKED_WIDTH, column = WIDE_COLUMN)

    val image = imageBounds()
    assertEquals(ASKED_WIDTH.toFloat(), image.width.value, TOLERANCE)
    assertEquals(ASKED_WIDTH * STRIP_HEIGHT / STRIP_WIDTH.toFloat(), image.height.value, TOLERANCE)
  }

  /** `max-width: 100%`: an image wider than the column shrinks to it, keeping its ratio. */
  @Test
  fun anImageWiderThanTheColumnShrinksToIt() {
    showImage(pngOf(OVERSIZED_WIDTH, STRIP_HEIGHT), width = null, column = NARROW_COLUMN)

    val image = imageBounds()
    assertEquals(NARROW_COLUMN.value, image.width.value, TOLERANCE)
    assertEquals(NARROW_COLUMN.value * STRIP_HEIGHT / OVERSIZED_WIDTH, image.height.value, TOLERANCE)
  }

  /**
   * 6400² pixels decode to 160 MiB, past even API 36's 150 MiB canvas limit, and drawn whole they
   * throw at draw. Shrunk while decoding, the image fills the column with its ratio kept (ADR-0087).
   */
  @Test
  fun anImageTooLargeToDrawWholeDrawsShrunkToTheColumn() {
    showImage(pngOf(HUGE_SIDE, HUGE_SIDE), width = null, column = WIDE_COLUMN)

    val image = imageBounds()
    assertEquals(WIDE_COLUMN.value, image.width.value, TOLERANCE)
    assertEquals(WIDE_COLUMN.value, image.height.value, TOLERANCE)
  }

  /**
   * 300 x 60000 pixels are past the canvas floor at the worst case's eight bytes a pixel, so they
   * decode at half size, 150 wide. The image is still 300 pixels wide, and draws 300 dp wide: an
   * `<img>` draws at its natural width whatever its decoder did (ADR-0087).
   */
  @Test
  fun anImageShrunkWhileDecodingDrawsAtItsOwnWidth() {
    showImage(pngOf(TALL_WIDTH, TALL_HEIGHT), width = null, column = WIDE_COLUMN)

    assertEquals(TALL_WIDTH.toFloat(), imageBounds().width.value, TOLERANCE)
  }

  private fun showImage(png: String, width: Int?, column: Dp) {
    val metadata = buildJsonObject {
      width?.let { put("image/png", buildJsonObject { put("width", it) }) }
    }
    val output = CellOutput.DisplayData(mapOf("image/png" to JsonPrimitive(png)), metadata = metadata)
    compose.setContent {
      JupyterDroidTheme {
        // `requiredWidth`, not `width`: the column is the width a test names, and at the emulator's 320dp a
        // `width(600.dp)` is measured down to the window's 320 so the wide-column cases read the window.
        // Scrolled, as the cell list is: under a bounded height `aspectRatio` sizes an image taller than
        // the screen from the height instead, and its width is no longer the one asked for.
        Box(modifier = Modifier.requiredWidth(column).verticalScroll(rememberScrollState())) {
          CellOutputs(
            outputs = listOf(output),
            hidden = false,
            scrolled = false,
            search = emptyList(),
            onExpand = {},
            inputRequest = null,
            onAnswerInput = {},
          )
        }
      }
    }
  }

  private fun imageBounds(): DpRect =
    compose.onNodeWithContentDescription("Cell image output").getUnclippedBoundsInRoot()

  private fun columnBounds(): DpRect = compose.onNodeWithContentDescription("Cell output").getUnclippedBoundsInRoot()

  /** A real PNG, encoded on the device, so the decoder the app uses is the one measured. */
  private fun pngOf(width: Int, height: Int): String {
    val bytes = ByteArrayOutputStream()
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, bytes)
    return Base64.getEncoder().encodeToString(bytes.toByteArray())
  }

  private companion object {
    /** A strip: 96 px is what JupyterLab draws 96 CSS px wide. */
    const val STRIP_WIDTH = 96
    const val STRIP_HEIGHT = 8
    const val ASKED_WIDTH = 100
    const val OVERSIZED_WIDTH = 2000
    const val HUGE_SIDE = 6400
    const val TALL_WIDTH = 300
    const val TALL_HEIGHT = 60000
    val WIDE_COLUMN = 600.dp
    val NARROW_COLUMN = 300.dp

    /** A layout rounds to whole pixels, which at any density is under a dp. */
    const val TOLERANCE = 1f
  }
}
