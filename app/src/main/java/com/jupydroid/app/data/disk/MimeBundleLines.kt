package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.MimeBundle
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray

/**
 * nbformat's two rules for the multi-line text inside a mime bundle, which are **not
 * symmetric**. Both are lifted from `nbformat/v4/rwbase.py` rather than guessed:
 *
 * - `_rejoin_mimebundle` joins a list into a string on read, **unless** the key is a JSON mime
 *   or the list holds anything but strings.
 * - `_split_mimebundle` splits a string into lines on write, **only** when the key starts with
 *   `text/` or is one of `_non_text_split_mimes`.
 *
 * The asymmetry is real and load-bearing: `image/png` is one long base64 string on disk while
 * `text/plain` beside it is a list of lines. Writing an image split into lines, or a text mime
 * as one string, both produce a file no Jupyter tool would have written.
 */
internal fun JsonObject.rejoinedBundle(): MimeBundle = mapValues { (mime, payload) ->
  if (mime.isJsonMime()) payload else payload.rejoinedLines()
}

internal fun MimeBundle.splitIntoLines(): JsonObject =
  JsonObject(mapValues { (mime, payload) -> if (mime.isSplit()) payload.splitLines() else payload })

/**
 * A JSON mime is left alone in whatever shape it arrived: nbformat's `_is_json_mime` is
 * `application/json` or anything `application/…+json`, which is where widget views live.
 */
private fun String.isJsonMime(): Boolean =
  this == "application/json" || (startsWith("application/") && endsWith("+json"))

/** Any `text/…` mime, plus the two nbformat names outright in `_non_text_split_mimes`. */
private fun String.isSplit(): Boolean = startsWith("text/") || this in NON_TEXT_SPLIT_MIMES

/** "…and every element is a string" is part of nbformat's rule, not an implementation detail. */
private fun JsonElement.rejoinedLines(): JsonElement {
  val lines = (this as? JsonArray)?.takeIf { array -> array.all { it is JsonPrimitive && it.isString } }
  return lines?.let { JsonPrimitive(it.joinToString("") { line -> (line as JsonPrimitive).content }) } ?: this
}

/**
 * Lines that keep their newlines, which is Python's `str.splitlines(True)`: `"a\nb"` becomes
 * `["a\n", "b"]` and `"42\n"` becomes `["42\n"]`, not `["42\n", ""]`. Joining the result returns
 * the original exactly, which is what makes the pair with [rejoinedLines] lossless.
 */
private fun JsonElement.splitLines(): JsonElement {
  val text = (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: return this
  val parts = text.split("\n")
  return buildJsonArray {
    parts
      .mapIndexed { index, line -> if (index == parts.lastIndex) line else "$line\n" }
      .filter { it.isNotEmpty() }
      .forEach { add(JsonPrimitive(it)) }
  }
}

private val NON_TEXT_SPLIT_MIMES = setOf("application/javascript", "image/svg+xml")
