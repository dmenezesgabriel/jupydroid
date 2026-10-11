package com.jupydroid.app.ui.editor

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeRemoteImageReader
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.html.HtmlDocument
import com.jupydroid.app.markdown.MarkdownDocument
import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.theme.JupyterDroidTheme
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream
import java.util.Base64

/**
 * A markdown image naming the cell's own attachment, a workspace file or a web address draws in its
 * line, at an image output's size - its own pixels in dp, capped at the column (ADR-0086) - and one
 * naming nothing draws its alt text. An HTML `<img>` draws through the same node, at its `width`.
 */
@RunWith(AndroidJUnit4::class)
class RichTextImageTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun anAttachmentDrawsAtItsOwnPixelsInDp() {
    show("![chart](attachment:chart.png)", pngOf(STRIP_WIDTH, STRIP_HEIGHT), column = WIDE_COLUMN)

    val image = imageBounds()
    assertEquals(STRIP_WIDTH.toFloat(), image.width.value, TOLERANCE)
    assertEquals(STRIP_HEIGHT.toFloat(), image.height.value, TOLERANCE)
  }

  @Test
  fun anAttachmentWiderThanTheColumnShrinksToIt() {
    show("![chart](attachment:chart.png)", pngOf(OVERSIZED_WIDTH, STRIP_HEIGHT), column = NARROW_COLUMN)

    val image = imageBounds()
    assertEquals(NARROW_COLUMN.value, image.width.value, TOLERANCE)
    assertEquals(NARROW_COLUMN.value * STRIP_HEIGHT / OVERSIZED_WIDTH, image.height.value, TOLERANCE)
  }

  @Test
  fun anImageNamingNoAttachmentDrawsItsAltText() {
    show("![missing chart](attachment:other.png)", pngOf(STRIP_WIDTH, STRIP_HEIGHT), column = WIDE_COLUMN)

    compose.onNodeWithText("missing chart").assertExists()
    compose.onAllNodesWithContentDescription("missing chart", useUnmergedTree = true).assertCountEquals(0)
  }

  /** Read from beside the notebook, through the attachment resolver a markdown cell sits in. */
  @Test
  fun aWorkspaceFileDrawsAtItsOwnPixelsInDp() {
    showFromWorkspace("![chart](img/chart.png)")

    val image = imageBounds()
    assertEquals(STRIP_WIDTH.toFloat(), image.width.value, TOLERANCE)
    assertEquals(STRIP_HEIGHT.toFloat(), image.height.value, TOLERANCE)
  }

  @Test
  fun anImageNamingNoWorkspaceFileDrawsItsAltText() {
    showFromWorkspace("![missing chart](img/gone.png)")

    compose.onNodeWithText("missing chart").assertExists()
    compose.onAllNodesWithContentDescription("missing chart", useUnmergedTree = true).assertCountEquals(0)
  }

  /** Fetched through the root's resolver, past the workspace and attachment ones a markdown cell sits in. */
  @Test
  fun aWebImageDrawsAtItsOwnPixelsInDp() {
    showFromWorkspace("![chart]($CHART_URL)")

    val image = imageBounds()
    assertEquals(STRIP_WIDTH.toFloat(), image.width.value, TOLERANCE)
    assertEquals(STRIP_HEIGHT.toFloat(), image.height.value, TOLERANCE)
  }

  /** Cleartext is refused everywhere but loopback (ADR-0087), so an `http:` image is never fetched. */
  @Test
  fun anHttpImageDrawsItsAltText() {
    showFromWorkspace("![missing chart](${CHART_URL.replace("https:", "http:")})")

    compose.onNodeWithText("missing chart").assertExists()
    compose.onAllNodesWithContentDescription("missing chart", useUnmergedTree = true).assertCountEquals(0)
  }

  /**
   * 6400² pixels decode to 160 MiB, past even API 36's 150 MiB canvas limit, and drawn whole they
   * throw at draw. Shrunk while decoding, the image fills the column with its ratio kept.
   */
  @Test
  fun anImageTooLargeToDrawWholeDrawsShrunkToTheColumn() {
    showFromWorkspace("![chart](img/chart.png)", pngBytesOf(HUGE_SIDE, HUGE_SIDE))

    val image = imageBounds()
    assertEquals(WIDE_COLUMN.value, image.width.value, TOLERANCE)
    assertEquals(WIDE_COLUMN.value, image.height.value, TOLERANCE)
  }

  /** JupyterLab's `<img width>`: the width written, in pixels, with the picture's own ratio. */
  @Test
  fun anImgDrawsAtItsWidthAttribute() {
    showFromWorkspace("<img src=\"img/chart.png\" alt=\"chart\" width=\"$IMG_WIDTH\">")

    val image = imageBounds()
    assertEquals(IMG_WIDTH.toFloat(), image.width.value, TOLERANCE)
    assertEquals(IMG_WIDTH.toFloat() * STRIP_HEIGHT / STRIP_WIDTH, image.height.value, TOLERANCE)
  }

  /** `max-width: 100%` caps the written width too. */
  @Test
  fun anImgWiderThanTheColumnShrinksToIt() {
    showFromWorkspace("<img src=\"img/chart.png\" alt=\"chart\" width=\"$OVERSIZED_WIDTH\">")

    val image = imageBounds()
    assertEquals(WIDE_COLUMN.value, image.width.value, TOLERANCE)
    assertEquals(WIDE_COLUMN.value * STRIP_HEIGHT / STRIP_WIDTH, image.height.value, TOLERANCE)
  }

  /** A `text/html` output draws as `CellOutputsView` draws one, outside any cell's attachments. */
  @Test
  fun anImgInAnHtmlOutputDrawsAWorkspaceFile() {
    showBlocksFromWorkspace(HtmlDocument.parse("<p>Plot: <img src=\"img/chart.png\" alt=\"chart\"></p>"))

    val image = imageBounds()
    assertEquals(STRIP_WIDTH.toFloat(), image.width.value, TOLERANCE)
    assertEquals(STRIP_HEIGHT.toFloat(), image.height.value, TOLERANCE)
  }

  /** [png] is `reports/img/chart.png` beside `reports/nb.ipynb`, and is served at [CHART_URL]. */
  private fun showFromWorkspace(source: String, png: ByteArray = pngBytesOf(STRIP_WIDTH, STRIP_HEIGHT)) =
    showBlocksFromWorkspace(MarkdownDocument.parse(source), png) { cell ->
      CellAttachmentImagesResolved(buildJsonObject {}) { cell() }
    }

  private fun showBlocksFromWorkspace(
    blocks: List<RichBlock>,
    png: ByteArray = pngBytesOf(STRIP_WIDTH, STRIP_HEIGHT),
    around: @Composable (@Composable () -> Unit) -> Unit = { it() },
  ) {
    val workspace = FakeWorkspaceRepository()
    runBlocking { workspace.writeBytes("reports/img/chart.png", png) }
    compose.setContent {
      JupyterDroidTheme {
        Box(modifier = Modifier.width(WIDE_COLUMN)) {
          RemoteImagesResolved(FakeRemoteImageReader(mapOf(CHART_URL to png))) {
            WorkspaceImagesResolved("reports/nb.ipynb", workspace) { around { RichText(blocks) } }
          }
        }
      }
    }
  }

  private fun show(source: String, png: String, column: Dp) {
    val preserved = buildJsonObject {
      putJsonObject("attachments") { putJsonObject("chart.png") { put("image/png", png) } }
    }
    compose.setContent {
      JupyterDroidTheme {
        Box(modifier = Modifier.width(column)) {
          CellAttachmentImagesResolved(preserved) { RichText(MarkdownDocument.parse(source)) }
        }
      }
    }
  }

  private fun imageBounds(): DpRect =
    compose.onNodeWithContentDescription("chart", useUnmergedTree = true).getUnclippedBoundsInRoot()

  /** A real PNG, encoded on the device, so the decoder the app uses is the one measured. */
  private fun pngOf(width: Int, height: Int): String = Base64.getEncoder().encodeToString(pngBytesOf(width, height))

  private fun pngBytesOf(width: Int, height: Int): ByteArray {
    val bytes = ByteArrayOutputStream()
    Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).compress(Bitmap.CompressFormat.PNG, 100, bytes)
    return bytes.toByteArray()
  }

  private companion object {
    const val STRIP_WIDTH = 96
    const val STRIP_HEIGHT = 8
    const val IMG_WIDTH = 48
    const val OVERSIZED_WIDTH = 2000
    const val HUGE_SIDE = 6400
    const val CHART_URL = "https://images.example/chart.png"
    val WIDE_COLUMN = 600.dp
    val NARROW_COLUMN = 300.dp

    /** A layout rounds to whole pixels, which at any density is under a dp. */
    const val TOLERANCE = 1f
  }
}
