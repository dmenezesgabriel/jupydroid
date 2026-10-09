package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.MimeBundle
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.html.HtmlDocument
import com.jupydroid.app.richtext.RichBlock
import com.jupydroid.app.terminal.SgrRun
import com.jupydroid.app.terminal.SgrText
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import java.util.Base64

/** What the editor can actually draw for one output. */
sealed interface RenderedOutput {
  /**
   * [text] is what the output says, with its escapes gone - what search and the collapsed
   * preview read - and [runs] are the colours its escapes asked for, laid over it when drawn.
   */
  data class Text(val text: String, val isError: Boolean, val runs: List<SgrRun> = emptyList()) : RenderedOutput

  data class Image(val png: ByteArray) : RenderedOutput {
    // ByteArray identity would make two identical images unequal, and Compose would redraw.
    override fun equals(other: Any?): Boolean = other is Image && png.contentEquals(other.png)

    override fun hashCode(): Int = png.contentHashCode()
  }

  /** Parsed markup - `text/html` today, and whatever else parses to the same model later. */
  data class Document(val blocks: List<RichBlock>) : RenderedOutput
}

/**
 * Turns a kernel output into something drawable, or null when there is nothing to draw.
 *
 * Kept out of the composable and free of Android types so the choices it makes - which mime
 * type wins, what a traceback reads like - are unit-testable.
 */
fun CellOutput.render(): RenderedOutput? = when (this) {
  is CellOutput.Stream -> colouredText(text, isError = name == StreamName.STDERR)
  is CellOutput.Error -> colouredText(readable(), isError = true)
  is CellOutput.ExecuteResult -> mimeBundle.renderRichest()
  is CellOutput.DisplayData -> mimeBundle.renderRichest()
  // An instruction to the front end rather than a result.
  is CellOutput.ClearOutput -> null
}

/**
 * The traceback if there is one, since it carries the line that failed; otherwise the name
 * and value, which are all a bare exception has.
 */
private fun CellOutput.Error.readable(): String = traceback.takeIf { it.isNotEmpty() }
  ?.joinToString("\n")
  ?: "$name: $value"

/**
 * The best form the bundle offers, richest first, then the mime types themselves as a last
 * resort so an unrenderable output says what it was rather than appearing blank.
 *
 * The order is JupyterLab's, read off `defaultRank` rather than guessed: `text/html` 50,
 * `image/png` 90, `text/plain` 120, walked ascending and first-present wins
 * (`packages/rendermime/src/factories.ts`; `registry.ts:105`). Only the three we can draw are
 * listed - a registry holds the types it has a renderer for, and the rest fall through.
 *
 * A type that is present but unusable - a truncated image, markup that parses to nothing -
 * keeps looking rather than rendering blank, which is the one thing JupyterLab's registry
 * does not do for us: it picks by presence, not by whether the render then worked.
 */
private fun MimeBundle.renderRichest(): RenderedOutput =
  RANKED_MIME_TYPES.firstNotNullOfOrNull { mime -> get(mime)?.asText()?.let { renderAs(mime, it) } }
    ?: RenderedOutput.Text(keys.joinToString(), isError = false)

private val RANKED_MIME_TYPES = listOf("text/html", "image/png", "text/plain")

/**
 * Null for a payload that is not a string, which is a real answer rather than a guard: every
 * type in [RANKED_MIME_TYPES] is a text mime, so an object under one of them is markup this app
 * cannot draw, and falling through to the next is what a registry with no renderer for it does.
 * JupyterLab reaches for `String(model.data[mime])` only *after* picking a mime it can render.
 */
private fun JsonElement.asText(): String? = (this as? JsonPrimitive)?.takeIf { it.isString }?.content

private fun renderAs(mimeType: String, payload: String): RenderedOutput? = when (mimeType) {
  "text/html" -> HtmlDocument.parse(payload).takeIf { it.isNotEmpty() }?.let(RenderedOutput::Document)
  "image/png" -> payload.decodedPng()?.let(RenderedOutput::Image)
  else -> colouredText(payload, isError = false)
}

/**
 * Null when undecodable - a truncated payload degrades to text, never a crash.
 *
 * Whitespace is stripped and then the *strict* decoder used: the lenient MIME one ignores
 * invalid characters, so it happily "decodes" text that is not base64 at all, and the
 * fallback would never fire. nbformat wraps its base64 across lines, hence the stripping.
 */
private fun String.decodedPng(): ByteArray? = filterNot { it.isWhitespace() }
  .let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

/** [raw] without its escapes or trailing whitespace, and the colours those escapes asked for. */
private fun colouredText(raw: String, isError: Boolean): RenderedOutput.Text =
  SgrText.parse(raw).trimEnd().let { RenderedOutput.Text(it.text, isError, it.runs) }
