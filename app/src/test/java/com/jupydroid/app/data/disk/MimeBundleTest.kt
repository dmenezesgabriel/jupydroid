package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.CellOutput
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A mime bundle's values are `MultilineString | PartialJSONObject`, and nbformat's two rules for
 * them are **not symmetric**. Both are lifted from `nbformat/v4/rwbase.py` rather than guessed,
 * and read against the installed library:
 *
 * - `_rejoin_mimebundle` joins a list into a string **unless** the key is a JSON mime
 *   (`application/json`, or `application/…+json`) or the list holds anything but strings.
 * - `_split_mimebundle` splits a string into lines **only** when the key starts with `text/`,
 *   or is one of `application/javascript` / `image/svg+xml`.
 *
 * So `image/png` is one long base64 string on disk while `text/plain` is a list of lines, and a
 * widget's `+json` payload is an object that neither rule touches. JupyterLab's own model layer
 * agrees: `convertBundle`/`extract` keep every value as JSON and stringify nothing
 * (`packages/rendermime/src/outputmodel.ts:348-356`).
 */
class MimeBundleTest {
  @Test
  fun read_joinsAListOfLinesIntoOneString() {
    assertEquals(JsonPrimitive("one\ntwo"), read("""{"text/plain":["one\n","two"]}""")["text/plain"])
  }

  @Test
  fun read_leavesAPlainStringAlone() {
    assertEquals(JsonPrimitive("42"), read("""{"text/plain":"42"}""")["text/plain"])
  }

  /** `application/json` and `application/…+json` are left alone however they are shaped. */
  @Test
  fun read_keepsAJsonMimeAsAnObject() {
    val bundle = read("""{"application/vnd.jupyter.widget-view+json":{"model_id":"abc","v":2}}""")

    assertEquals(json("""{"model_id":"abc","v":2}"""), bundle["application/vnd.jupyter.widget-view+json"])
  }

  /** A JSON mime whose value happens to be a list must not be joined into a string. */
  @Test
  fun read_doesNotJoinAListUnderAJsonMime() {
    assertEquals(json("""["a","b"]"""), read("""{"application/json":["a","b"]}""")["application/json"])
  }

  /** "all elements are strings" is part of the rule, not an implementation detail. */
  @Test
  fun read_leavesAListOfNonStringsAlone() {
    assertEquals(json("""[1,2]"""), read("""{"text/plain":[1,2]}""")["text/plain"])
  }

  @Test
  fun write_splitsATextMimeIntoLinesThatKeepTheirNewlines() {
    assertEquals(json("""["one\n","two"]"""), write("text/plain" to JsonPrimitive("one\ntwo"))["text/plain"])
  }

  /** Base64 is one long string on disk: `image/png` is not a `text/` mime and is not split. */
  @Test
  fun write_doesNotSplitAnImagePayload() {
    assertEquals(JsonPrimitive("iVBORw0KGgo="), write("image/png" to JsonPrimitive("iVBORw0KGgo="))["image/png"])
  }

  /** The two non-text mimes nbformat does split, named explicitly in `_non_text_split_mimes`. */
  @Test
  fun write_splitsJavascriptAndSvg() {
    val lines = json("""["a\n","b"]""")

    assertEquals(lines, write("application/javascript" to JsonPrimitive("a\nb"))["application/javascript"])
    assertEquals(lines, write("image/svg+xml" to JsonPrimitive("a\nb"))["image/svg+xml"])
  }

  @Test
  fun write_keepsAnObjectPayloadAsAnObject() {
    val payload = json("""{"model_id":"abc"}""")
    val mime = "application/vnd.jupyter.widget-view+json"

    assertEquals(payload, write(mime to payload)[mime])
  }

  /** The pair that matters: a widget survives a read and a write with its shape intact. */
  @Test
  fun roundTrip_keepsAWidgetPayloadAnObjectAndTextALineList() {
    val source = """{"application/json":{"a":1},"text/plain":["x\n","y"]}"""

    val written = JsonObject(read(source).mapValues { (mime, value) -> splitFor(mime, value) })

    assertEquals(Json.parseToJsonElement(source), written)
  }

  private fun read(bundleJson: String): Map<String, JsonElement> =
    (Json.parseToJsonElement("""{"output_type":"display_data","data":$bundleJson}""") as JsonObject)
      .let { (it.toDomainOutput() as CellOutput.DisplayData).mimeBundle }

  private fun write(entry: Pair<String, JsonElement>): Map<String, JsonElement> {
    val output = CellOutput.DisplayData(mapOf(entry))
    return ((output.toFileOutput() as JsonObject)["data"] as JsonObject)
  }

  private fun splitFor(mime: String, value: JsonElement): JsonElement =
    write(mime to value).getValue(mime)

  private fun json(text: String): JsonElement = Json.parseToJsonElement(text)
}
