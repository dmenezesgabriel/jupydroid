package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.LanguageCompletion
import com.jupydroid.app.domain.port.LanguageEdit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The `textDocument/completion` reply - `CompletionItem[]` or `{isIncomplete, items}`; LSP
 * allows either and pylsp sends the list. Kept off [LspWire] the way [LspMarkup] and
 * [LspNotifications] are: the wire holds request params and the leaves replies share, this
 * holds the completion reply.
 */
internal object LspCompletion {
  /** `CompletionItem[]` or `{isIncomplete, items}` - LSP allows either; pylsp sends the list. */
  fun completionItems(result: JsonElement?): List<LanguageCompletion> {
    val items = when (result) {
      is JsonArray -> result
      is JsonObject -> result["items"] as? JsonArray
      else -> null
    } ?: return emptyList()
    return items.mapNotNull { (it as? JsonObject)?.let(::completionItem) }
  }

  /** `insertText`, else `textEdit.newText`, else `label` - LSP's own insertion precedence. */
  private fun completionItem(item: JsonObject): LanguageCompletion? {
    val label = item["label"]?.jsonPrimitive?.contentOrNull ?: return null
    val edit = (item["textEdit"] as? JsonObject)?.let(::languageEdit)
    return LanguageCompletion(
      label = label,
      insertText = item["insertText"]?.jsonPrimitive?.contentOrNull ?: edit?.newText ?: label,
      detail = item["detail"]?.jsonPrimitive?.contentOrNull,
      edit = edit,
      kind = item["kind"]?.jsonPrimitive?.intOrNull,
      sortText = item["sortText"]?.jsonPrimitive?.contentOrNull,
    )
  }

  /** `textEdit` is a `TextEdit` (`{range, newText}`) or an `InsertReplaceEdit` (`{insert, replace}`). */
  private fun languageEdit(edit: JsonObject): LanguageEdit? =
    ((edit["range"] as? JsonObject) ?: (edit["insert"] as? JsonObject))
      ?.let(LspWire::languageRange)
      ?.let { range ->
        edit["newText"]?.jsonPrimitive?.contentOrNull?.let { LanguageEdit(range, it) }
      }
}
