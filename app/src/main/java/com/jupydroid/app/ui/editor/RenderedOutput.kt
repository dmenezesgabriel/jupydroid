package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.StreamName
import java.util.Base64

/** What the editor can actually draw for one output. */
sealed interface RenderedOutput {
  data class Text(val text: String, val isError: Boolean) : RenderedOutput

  data class Image(val png: ByteArray) : RenderedOutput {
    // ByteArray identity would make two identical images unequal, and Compose would redraw.
    override fun equals(other: Any?): Boolean = other is Image && png.contentEquals(other.png)

    override fun hashCode(): Int = png.contentHashCode()
  }
}

/**
 * Turns a kernel output into something drawable, or null when there is nothing to draw.
 *
 * Kept out of the composable and free of Android types so the choices it makes - which mime
 * type wins, what a traceback reads like - are unit-testable.
 */
fun CellOutput.render(): RenderedOutput? = when (this) {
  is CellOutput.Stream -> RenderedOutput.Text(text.trimEnd(), isError = name == StreamName.STDERR)
  is CellOutput.Error -> RenderedOutput.Text(readable(), isError = true)
  is CellOutput.ExecuteResult -> mimeBundle.renderRichest()
  is CellOutput.DisplayData -> mimeBundle.renderRichest()
  // An instruction to the front end rather than a result.
  CellOutput.ClearOutput -> null
}

/**
 * The traceback if there is one, since it carries the line that failed; otherwise the name
 * and value, which are all a bare exception has.
 */
private fun CellOutput.Error.readable(): String =
  traceback.takeIf { it.isNotEmpty() }
    ?.joinToString("\n") { it.withoutAnsiColours() }
    ?: "$name: $value"

/**
 * An image if there is one, because a figure's `text/plain` is only ever a placeholder like
 * `<Figure size 640x480>`. Text otherwise, and the mime types themselves as a last resort so
 * an unrenderable output says what it was rather than appearing blank.
 */
private fun Map<String, String>.renderRichest(): RenderedOutput {
  decodedPng()?.let { return RenderedOutput.Image(it) }
  val text = get("text/plain") ?: keys.joinToString().ifEmpty { "" }
  return RenderedOutput.Text(text.trimEnd(), isError = false)
}

/**
 * Null when absent or undecodable - a truncated payload degrades to text, never a crash.
 *
 * Whitespace is stripped and then the *strict* decoder used: the lenient MIME one ignores
 * invalid characters, so it happily "decodes" text that is not base64 at all, and the
 * fallback would never fire. nbformat wraps its base64 across lines, hence the stripping.
 */
private fun Map<String, String>.decodedPng(): ByteArray? =
  get("image/png")?.filterNot { it.isWhitespace() }
    ?.let { runCatching { Base64.getDecoder().decode(it) }.getOrNull() }

/**
 * Removes the SGR escapes a kernel colours its tracebacks with. Both the real `ESC [ … m`
 * form and the bare `[…m` left behind when something has already eaten the escape byte -
 * the second is what reaches us, and rendering it literally is what put `[31m` on screen.
 */
private fun String.withoutAnsiColours(): String = replace(ANSI_SGR, "")

private val ANSI_SGR = Regex("?\\[[0-9;]*m")
