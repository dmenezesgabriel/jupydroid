package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.html.PANDAS_DATAFRAME_HTML
import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.richtext.plainText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderedOutputTest {
  @Test
  fun render_marksStderrAsErrorAndStdoutAsOrdinary() {
    assertEquals(false, text(CellOutput.Stream(StreamName.STDOUT, "fine")).isError)
    assertEquals(true, text(CellOutput.Stream(StreamName.STDERR, "careful")).isError)
  }

  /**
   * A kernel colours its tracebacks with ANSI escapes. Rendered literally they read
   * `[31m---...`, which is what the Run All screenshot showed - noise wrapped around the
   * one thing the user needs.
   */
  @Test
  fun render_stripsTheAnsiColoursOutOfATraceback() {
    val error = CellOutput.Error(
      name = "ZeroDivisionError",
      value = "division by zero",
      traceback = listOf(
        "[31m---------------------------------------[39m",
        "[31mZeroDivisionError[39m   Traceback (most recent call last)",
      ),
    )

    val rendered = text(error).text

    assertTrue("escapes must be gone, got '$rendered'", !rendered.contains(""))
    assertTrue("the bracket codes must be gone too, got '$rendered'", !rendered.contains("[31m"))
    assertTrue(rendered.contains("ZeroDivisionError"))
    assertTrue(rendered.contains("Traceback (most recent call last)"))
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
    assertEquals("42", text(CellOutput.ExecuteResult(mapOf("text/plain" to "42"), 1)).text)
  }

  /** A figure arrives with a text/plain placeholder beside it; the image is the point. */
  @Test
  fun render_prefersAnImageOverItsTextPlaceholder() {
    val output = CellOutput.DisplayData(
      mapOf("text/plain" to "<Figure size 640x480>", "image/png" to ONE_PIXEL_PNG),
    )

    val rendered = output.render()

    assertTrue("expected an image, got $rendered", rendered is RenderedOutput.Image)
  }

  /**
   * `IPython.display.HTML`'s `text/plain` is literally `<IPython.core.display.HTML object>`,
   * so getting the order wrong is not a subtle regression - it is that string on screen.
   */
  @Test
  fun render_prefersHtmlOverItsPlainTextFallback() {
    val output = CellOutput.ExecuteResult(
      mapOf("text/plain" to "<IPython.core.display.HTML object>", "text/html" to "<h3>Report</h3>"),
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
      mapOf("image/png" to ONE_PIXEL_PNG, "text/html" to "<p>a table</p>", "text/plain" to "fallback"),
    )

    assertTrue("expected a document, got ${output.render()}", output.render() is RenderedOutput.Document)
  }

  /** Markup that draws nothing keeps looking, the way an undecodable image does. */
  @Test
  fun render_fallsBackToTextWhenTheHtmlRendersToNothing() {
    val output = CellOutput.DisplayData(
      mapOf("text/html" to "<style>.dataframe { color: red; }</style>", "text/plain" to "the table"),
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
      mapOf("text/html" to PANDAS_DATAFRAME_HTML, "text/plain" to "   a  b\n0  1  x\n1  2  y"),
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
      mapOf("text/plain" to "<Figure>", "image/png" to "not base64 at all!!"),
    )

    assertEquals("<Figure>", text(output).text)
  }

  @Test
  fun render_showsTheMimeTypesWhenNothingIsRenderable() {
    val rendered = text(CellOutput.DisplayData(mapOf("application/vnd.custom+json" to "{}")))

    assertTrue(
      "expected the mime type named, got '${rendered.text}'",
      rendered.text.contains("application/vnd.custom+json"),
    )
  }

  /** ClearOutput is an instruction, not a result; there is nothing to draw. */
  @Test
  fun render_drawsNothingForClearOutput() {
    assertNull(CellOutput.ClearOutput.render())
  }

  private fun text(output: CellOutput): RenderedOutput.Text = output.render() as RenderedOutput.Text

  private companion object {
    /** A real 1x1 PNG, so the decoder is exercised rather than mocked. */
    const val ONE_PIXEL_PNG =
      "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
  }
}
