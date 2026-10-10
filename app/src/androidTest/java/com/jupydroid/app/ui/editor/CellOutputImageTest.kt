package com.jupydroid.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
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

  private fun showImage(png: String, width: Int?, column: Dp) {
    val metadata = buildJsonObject {
      width?.let { put("image/png", buildJsonObject { put("width", it) }) }
    }
    val output = CellOutput.DisplayData(mapOf("image/png" to JsonPrimitive(png)), metadata = metadata)
    compose.setContent {
      JupyterDroidTheme {
        Box(modifier = Modifier.width(column)) {
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
    val WIDE_COLUMN = 600.dp
    val NARROW_COLUMN = 300.dp

    /** A layout rounds to whole pixels, which at any density is under a dp. */
    const val TOLERANCE = 1f
  }
}
