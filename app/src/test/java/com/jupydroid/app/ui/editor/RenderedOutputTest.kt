package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.MimeBundle
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.html.PANDAS_DATAFRAME_HTML
import com.jupydroid.app.mimeBundleOf
import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.plainText
import com.jupydroid.app.terminal.CellStyle
import com.jupydroid.app.terminal.SgrRun
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class RenderedOutputTest {
  private val red = CellStyle.encode(1, CellStyle.COLOR_INDEX_BACKGROUND, 0)

  @Test
  fun render_marksStderrAsErrorAndStdoutAsOrdinary() {
    assertEquals(false, text(CellOutput.Stream(StreamName.STDOUT, "fine")).isError)
    assertEquals(true, text(CellOutput.Stream(StreamName.STDERR, "careful")).isError)
  }

  /** An `input()` echo is the user's own answer, not a warning, so it draws plain. */
  @Test
  fun render_drawsAStdinEchoAsOrdinaryText() {
    assertEquals(false, text(CellOutput.Stream(StreamName.STDIN, "Name? Ada\n")).isError)
  }

  /**
   * A kernel colours its tracebacks with ANSI escapes. Rendered literally they read
   * `[31m---...`, which is what the Run All screenshot showed - noise wrapped around the
   * one thing the user needs. The colour is kept beside the text, not in it.
   */
  @Test
  fun render_coloursATracebackWithoutLeavingItsEscapesInTheText() {
    val error = CellOutput.Error(
      name = "ZeroDivisionError",
      value = "division by zero",
      traceback = listOf(
        "\u001b[31m---\u001b[39m",
        "\u001b[31mZeroDivisionError\u001b[39m   Traceback (most recent call last)",
      ),
    )

    val rendered = text(error)

    assertEquals("---\nZeroDivisionError   Traceback (most recent call last)", rendered.text)
    assertEquals(listOf(SgrRun(0, 3, red), SgrRun(4, 21, red)), rendered.runs)
  }

  /** `print("\u001b[31mred\u001b[0m")`, tqdm, `colored` - a stream colours itself the same way. */
  @Test
  fun render_coloursAStream() {
    val rendered = text(CellOutput.Stream(StreamName.STDOUT, "\u001b[31mred\u001b[0m\n"))

    assertEquals("red", rendered.text)
    assertEquals(listOf(SgrRun(0, 3, red)), rendered.runs)
  }

  /** A `text/plain` payload can carry escapes too - `__repr__`s of `rich` and `colored` objects do. */
  @Test
  fun render_coloursPlainText() {
    val output = CellOutput.ExecuteResult(
      mimeBundleOf("text/plain" to "\u001b[32mok\u001b[0m\n"),
      1,
    )

    val rendered = text(output)

    assertEquals("ok", rendered.text)
    assertEquals(listOf(SgrRun(0, 2, CellStyle.encode(2, CellStyle.COLOR_INDEX_BACKGROUND, 0))), rendered.runs)
  }

  /** With no traceback there is still a name and a value, and they must not read as blank. */
  @Test
  fun render_fallsBackToNameAndValueWhenThereIsNoTraceback() {
    val rendered = text(CellOutput.Error("ValueError", "bad input", emptyList()))

    assertEquals("ValueError: bad input", rendered.text)
    assertTrue(rendered.isError)
  }

  @Test
  fun render_showsTheTextOfAResultThatHasNoRicherForm() {
    assertEquals("42", text(CellOutput.ExecuteResult(mimeBundleOf("text/plain" to "42"), 1)).text)
  }

  /** A figure arrives with a text/plain placeholder beside it; the image is the point. */
  @Test
  fun render_prefersAnImageOverItsTextPlaceholder() {
    val output = CellOutput.DisplayData(
      mimeBundleOf("text/plain" to "<Figure size 640x480>", "image/png" to ONE_PIXEL_PNG),
    )

    val rendered = output.render()

    assertTrue("expected an image, got $rendered", rendered is RenderedOutput.Image)
  }

  /**
   * `IPython.display.Image` sends JPEG, GIF and WebP under their own mimes, and a raw display can
   * send BMP; each one's `text/plain` is `<IPython.core.display.Image object>`, which is what drew.
   */
  @Test
  fun render_drawsEveryImageMimeJupyterLabDraws() {
    val payloads = mapOf(
      "image/jpeg" to ONE_PIXEL_JPEG,
      "image/gif" to ONE_PIXEL_GIF,
      "image/webp" to ONE_PIXEL_WEBP,
      "image/bmp" to ONE_PIXEL_BMP,
    )

    payloads.forEach { (mime, payload) ->
      val rendered = imageOf(mimeBundleOf("text/plain" to "<IPython.core.display.Image object>", mime to payload))
      assertTrue("expected $mime decoded, got $rendered", rendered.encoded.contentEquals(base64(payload)))
    }
  }

  /**
   * Every image mime ranks 90, and JupyterLab breaks the tie by the order its factory lists them:
   * bmp, png, jpeg, gif, webp (jupyterlab@7f0e690 packages/rendermime/src/factories.ts,
   * `registry.ts` `sortedTypes`).
   */
  @Test
  fun render_breaksAnImageTieInTheOrderJupyterLabRegistersThem() {
    val bmpOverPng = imageOf(mimeBundleOf("image/png" to ONE_PIXEL_PNG, "image/bmp" to ONE_PIXEL_BMP))
    val pngOverJpeg = imageOf(mimeBundleOf("image/jpeg" to ONE_PIXEL_JPEG, "image/png" to ONE_PIXEL_PNG))
    val jpegOverGif = imageOf(mimeBundleOf("image/gif" to ONE_PIXEL_GIF, "image/jpeg" to ONE_PIXEL_JPEG))
    val gifOverWebp = imageOf(mimeBundleOf("image/webp" to ONE_PIXEL_WEBP, "image/gif" to ONE_PIXEL_GIF))

    assertTrue("bmp should beat png", bmpOverPng.encoded.contentEquals(base64(ONE_PIXEL_BMP)))
    assertTrue("png should beat jpeg", pngOverJpeg.encoded.contentEquals(base64(ONE_PIXEL_PNG)))
    assertTrue("jpeg should beat gif", jpegOverGif.encoded.contentEquals(base64(ONE_PIXEL_JPEG)))
    assertTrue("gif should beat webp", gifOverWebp.encoded.contentEquals(base64(ONE_PIXEL_GIF)))
  }

  @Test
  fun render_prefersHtmlOverAJpeg() {
    val output = CellOutput.DisplayData(mimeBundleOf("image/jpeg" to ONE_PIXEL_JPEG, "text/html" to "<p>a table</p>"))

    assertTrue("expected a document, got ${output.render()}", output.render() is RenderedOutput.Document)
  }

  @Test
  fun render_fallsBackToTextWhenAJpegCannotBeDecoded() {
    val output = CellOutput.DisplayData(mimeBundleOf("text/plain" to "<Image>", "image/jpeg" to "not base64 at all!!"))

    assertEquals("<Image>", text(output).text)
  }

  /** `Image(..., width=100)` and a retina figure's `w // 2` both arrive as `metadata[mime].width`. */
  @Test
  fun render_readsAnImagesWidthFromItsOwnMimesMetadata() {
    val output = CellOutput.DisplayData(onePixelPng(), metadata = widthUnder("image/png", JsonPrimitive(100)))

    assertEquals(100, (output.render() as RenderedOutput.Image).width)
  }

  @Test
  fun render_readsAnExecuteResultsImageWidthToo() {
    val output = CellOutput.ExecuteResult(onePixelPng(), 1, metadata = widthUnder("image/png", JsonPrimitive(100)))

    assertEquals(100, (output.render() as RenderedOutput.Image).width)
  }

  @Test
  fun render_leavesAnImageWithNoWidthToItsOwnSize() {
    assertNull(imageOf(onePixelPng()).width)
  }

  /** `renderImage` sets `img.width` only when `typeof width === 'number'` (jupyterlab@7f0e690 renderers.ts). */
  @Test
  fun render_ignoresAWidthThatIsNotANumber() {
    val output = CellOutput.DisplayData(onePixelPng(), metadata = widthUnder("image/png", JsonPrimitive("100")))

    assertNull((output.render() as RenderedOutput.Image).width)
  }

  /** The width under another mime describes a form of the image that is not the one drawn. */
  @Test
  fun render_ignoresAWidthUnderAnotherMime() {
    val output = CellOutput.DisplayData(onePixelPng(), metadata = widthUnder("image/jpeg", JsonPrimitive(100)))

    assertNull((output.render() as RenderedOutput.Image).width)
  }

  /** `img.width` is an `unsigned long`, so a fraction is truncated on the way in. */
  @Test
  fun render_truncatesAFractionalWidth() {
    val output = CellOutput.DisplayData(onePixelPng(), metadata = widthUnder("image/png", JsonPrimitive(100.7)))

    assertEquals(100, (output.render() as RenderedOutput.Image).width)
  }

  @Test
  fun render_ignoresANegativeWidth() {
    val output = CellOutput.DisplayData(onePixelPng(), metadata = widthUnder("image/png", JsonPrimitive(-5)))

    assertNull((output.render() as RenderedOutput.Image).width)
  }

  /**
   * `IPython.display.HTML`'s `text/plain` is literally `<IPython.core.display.HTML object>`,
   * so getting the order wrong is not a subtle regression - it is that string on screen.
   */
  @Test
  fun render_prefersHtmlOverItsPlainTextFallback() {
    val output = CellOutput.ExecuteResult(
      mimeBundleOf("text/plain" to "<IPython.core.display.HTML object>", "text/html" to "<h3>Report</h3>"),
      1,
    )

    val rendered = output.render()

    assertTrue("expected a document, got $rendered", rendered is RenderedOutput.Document)
    val heading = (rendered as RenderedOutput.Document).blocks.single() as RichBlock.Heading
    assertEquals("Report", heading.inlines.plainText())
  }

  /**
   * JupyterLab's own ranking, not a guess: `htmlRendererFactory.defaultRank` is 50 and
   * `imageRendererFactory.defaultRank` is 90, and `preferredMimeType` walks them ascending
   * (`packages/rendermime/src/factories.ts`, `registry.ts:105`). Lower rank wins.
   */
  @Test
  fun render_prefersHtmlOverAnImage() {
    val output = CellOutput.DisplayData(
      mimeBundleOf("image/png" to ONE_PIXEL_PNG, "text/html" to "<p>a table</p>", "text/plain" to "fallback"),
    )

    assertTrue("expected a document, got ${output.render()}", output.render() is RenderedOutput.Document)
  }

  /** Markup that draws nothing keeps looking, the way an undecodable image does. */
  @Test
  fun render_fallsBackToTextWhenTheHtmlRendersToNothing() {
    val output = CellOutput.DisplayData(
      mimeBundleOf("text/html" to "<style>.dataframe { color: red; }</style>", "text/plain" to "the table"),
    )

    assertEquals("the table", text(output).text)
  }

  /**
   * A real `DataFrame._repr_html_`, captured from pandas rather than written from memory: the
   * case this whole step exists for. The ASCII grid beside it is what used to be shown.
   */
  @Test
  fun render_drawsADataFrameAsATableRatherThanItsAsciiGrid() {
    val output = CellOutput.ExecuteResult(
      mimeBundleOf("text/html" to PANDAS_DATAFRAME_HTML, "text/plain" to "   a  b\n0  1  x\n1  2  y"),
      1,
    )

    val rendered = output.render()

    assertTrue("expected a document, got $rendered", rendered is RenderedOutput.Document)
    val table = (rendered as RenderedOutput.Document).blocks.single() as RichBlock.Table
    assertEquals(listOf("", "a", "b"), table.rows.first().map { it.inlines.plainText() })
  }

  /** A truncated or non-base64 payload must degrade to its text, not crash the notebook. */
  @Test
  fun render_fallsBackToTextWhenTheImageCannotBeDecoded() {
    val output = CellOutput.DisplayData(
      mimeBundleOf("text/plain" to "<Figure>", "image/png" to "not base64 at all!!"),
    )

    assertEquals("<Figure>", text(output).text)
  }

  @Test
  fun render_showsTheMimeTypesWhenNothingIsRenderable() {
    val rendered = text(CellOutput.DisplayData(mimeBundleOf("application/vnd.custom+json" to "{}")))

    assertTrue(
      "expected the mime type named, got '${rendered.text}'",
      rendered.text.contains("application/vnd.custom+json"),
    )
  }

  /** ClearOutput is an instruction, not a result; there is nothing to draw. */
  @Test
  fun render_drawsNothingForClearOutput() {
    assertNull(CellOutput.ClearOutput().render())
  }

  /** An update redraws the output it targets; it is never drawn as one of its own. */
  @Test
  fun render_drawsNothingForADisplayUpdate() {
    assertNull(
      CellOutput.DisplayUpdate(CellOutput.DisplayData(mimeBundleOf("text/plain" to "x"), displayId = "d1")).render(),
    )
  }

  private fun text(output: CellOutput): RenderedOutput.Text = output.render() as RenderedOutput.Text

  private fun imageOf(bundle: MimeBundle): RenderedOutput.Image =
    CellOutput.DisplayData(bundle).render() as RenderedOutput.Image

  private fun base64(payload: String): ByteArray = Base64.getDecoder().decode(payload)

  private fun onePixelPng(): MimeBundle = mimeBundleOf("image/png" to ONE_PIXEL_PNG)

  private fun widthUnder(mime: String, width: JsonPrimitive): JsonObject =
    buildJsonObject { put(mime, buildJsonObject { put("width", width) }) }

  private companion object {
    /** A real 1x1 PNG, so the decoder is exercised rather than mocked. */
    const val ONE_PIXEL_PNG =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="

    // Real 1x1 images, each written by Pillow 12.1.1 in its own format.
    const val ONE_PIXEL_JPEG =
      "/9j/4AAQSkZJRgABAQAAAQABAAD/2wBDAAgGBgcGBQgHBwcJCQgKDBQNDAsLDBkSEw8UHRofHh0aHBwgJC4nICIsIxwcKDcpLDAx" +
        "NDQ0Hyc5PTgyPC4zNDL/2wBDAQkJCQwLDBgNDRgyIRwhMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIyMjIy" +
        "MjIyMjIyMjL/wAARCAABAAEDASIAAhEBAxEB/8QAHwAAAQUBAQEBAQEAAAAAAAAAAAECAwQFBgcICQoL/8QAtRAAAgEDAwIEAwUF" +
        "BAQAAAF9AQIDAAQRBRIhMUEGE1FhByJxFDKBkaEII0KxwRVS0fAkM2JyggkKFhcYGRolJicoKSo0NTY3ODk6Q0RFRkdISUpTVFVW" +
        "V1hZWmNkZWZnaGlqc3R1dnd4eXqDhIWGh4iJipKTlJWWl5iZmqKjpKWmp6ipqrKztLW2t7i5usLDxMXGx8jJytLT1NXW19jZ2uHi" +
        "4+Tl5ufo6erx8vP09fb3+Pn6/8QAHwEAAwEBAQEBAQEBAQAAAAAAAAECAwQFBgcICQoL/8QAtREAAgECBAQDBAcFBAQAAQJ3AAEC" +
        "AxEEBSExBhJBUQdhcRMiMoEIFEKRobHBCSMzUvAVYnLRChYkNOEl8RcYGRomJygpKjU2Nzg5OkNERUZHSElKU1RVVldYWVpjZGVm" +
        "Z2hpanN0dXZ3eHl6goOEhYaHiImKkpOUlZaXmJmaoqOkpaanqKmqsrO0tba3uLm6wsPExcbHyMnK0tPU1dbX2Nna4uPk5ebn6Onq" +
        "8vP09fb3+Pn6/9oADAMBAAIRAxEAPwDi6KKK+ZP3E//Z"
    const val ONE_PIXEL_GIF = "R0lGODdhAQABAIEAAP8AAAAAAAAAAAAAACwAAAAAAQABAAAIBAABBAQAOw=="
    const val ONE_PIXEL_WEBP =
      "UklGRjwAAABXRUJQVlA4IDAAAADQAQCdASoBAAEAAUAmJaACdLoB+AADsAD+8ut//NgVzXPv9//S4P0uD9Lg/9KQAAA="
    const val ONE_PIXEL_BMP = "Qk06AAAAAAAAADYAAAAoAAAAAQAAAAEAAAABABgAAAAAAAQAAADEDgAAxA4AAAAAAAAAAAAAAAD/AA=="
  }
}
