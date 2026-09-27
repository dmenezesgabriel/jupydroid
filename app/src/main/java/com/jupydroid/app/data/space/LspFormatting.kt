package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.LanguageEdit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The `textDocument/rangeFormatting` reply: `TextEdit[]` or `null` (LSP 3.17) - `null` and
 * `[]` are the same nothing, the range was already formatted. An entry missing its `range` or
 * `newText` falls out of the parse rather than apply half an edit - [LspDefinition]'s own rule
 * for a target the client cannot read.
 *
 * The shipped `pylsp` answers through autopep8 with a single whole-document `TextEdit`
 * (`{0,0}` to past the last line, the whole reformatted source as `newText` - "I'm too lazy
 * at the moment to parse diffs into TextEdit items", `pylsp/plugins/autopep8_format.py`), so
 * what the caller does with these is its own care: `NotebookVirtualDocument.cellSourceAfterEdits`
 * applies them to the virtual text and guards the cell boundary before slicing.
 *
 * Kept off [LspWire] the way [LspDefinition] is: the wire holds request params, this holds
 * the formatting reply.
 */
internal object LspFormatting {
  fun editsFrom(result: JsonElement?): List<LanguageEdit> {
    val elements = result as? JsonArray ?: return emptyList()
    return elements.mapNotNull(::editFrom)
  }

  private fun editFrom(element: JsonElement): LanguageEdit? {
    val edit = element as? JsonObject
    val range = (edit?.get("range") as? JsonObject)?.let(LspWire::languageRange)
    val newText = edit?.get("newText")?.jsonPrimitive?.contentOrNull
    return if (range != null && newText != null) LanguageEdit(range, newText) else null
  }
}
