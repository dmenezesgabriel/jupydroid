package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.LanguageHover
import com.jupydroid.app.domain.port.LanguageMarkup
import com.jupydroid.app.domain.port.LanguageMarkupKind
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The `contents` shapes a `textDocument/hover` reply may carry - `docs/PLAN.md` P5e-3.
 *
 * Kept off [LspWire] for the same reason the wire is off the adapter: one home per kind of
 * shape, and these are the five `MarkedString`/`MarkupContent` spellings LSP allows where
 * [LspWire] holds the request params and item replies.
 */
internal object LspMarkup {
  /**
   * A `textDocument/hover` reply's `result`. Null, an absent `contents`, and blank contents all
   * answer "nothing here" - pylsp's off-symbol reply is `{"contents": ""}` (its `hover.py`'s own
   * `if not definition`), not a JSON `null` - and an empty hover is worth no popup.
   */
  fun hoverFrom(result: JsonElement?): LanguageHover? {
    val reply = result as? JsonObject ?: return null
    return contentsOf(reply["contents"])?.let { markup ->
      LanguageHover(markup, (reply["range"] as? JsonObject)?.let(LspWire::languageRange))
    }
  }

  /**
   * `contents` as one [LanguageMarkup] whatever its wire form: `MarkupContent` passes through,
   * a bare `MarkedString` reads as markdown, a `{language, value}` one becomes its fenced
   * block, and `MarkedString[]` joins the three forms with blank lines - the same flattening
   * VS Code's `MarkedString` renderer does before display.
   */
  private fun contentsOf(contents: JsonElement?): LanguageMarkup? = when (contents) {
    is JsonObject -> markupObject(contents)
    is JsonArray -> contents.mapNotNull { markedString(it) }
      .joinToString("\n\n")
      .takeIf { it.isNotEmpty() }
      ?.let { LanguageMarkup(LanguageMarkupKind.MARKDOWN, it) }
    else -> markedString(contents)?.let { LanguageMarkup(LanguageMarkupKind.MARKDOWN, it) }
  }

  /** `MarkupContent` (`{kind, value}`) or the `MarkedString` object (`{language, value}`). */
  private fun markupObject(contents: JsonObject): LanguageMarkup? {
    val value = contents["value"]?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) ?: return null
    val kind = contents["kind"]?.jsonPrimitive?.contentOrNull
    return when {
      kind != null -> LanguageMarkup(markupKindOf(kind), value)
      else -> LanguageMarkup(LanguageMarkupKind.MARKDOWN, markedCode(contents["language"], value))
    }
  }

  /** One `MarkedString` as markdown text - a code span is its fence, a bare one passes through. */
  private fun markedString(item: JsonElement?): String? = when (item) {
    is JsonObject -> item["value"]?.jsonPrimitive?.contentOrNull
      ?.takeIf(String::isNotBlank)
      ?.let { markedCode(item["language"], it) }
    else -> item?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank)
  }

  private fun markedCode(language: JsonElement?, value: String): String =
    "```${language?.jsonPrimitive?.contentOrNull.orEmpty()}\n$value\n```"

  private fun markupKindOf(kind: String): LanguageMarkupKind =
    if (kind == "markdown") LanguageMarkupKind.MARKDOWN else LanguageMarkupKind.PLAINTEXT
}
