package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.LanguageCapabilities
import com.jupydroid.app.domain.port.LanguageCompletion
import com.jupydroid.app.domain.port.LanguageEdit
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/**
 * The JSON shapes LSP methods carry, kept off [StdioLanguageServer] so the adapter holds the
 * lifecycle and this holds the wire - request params out, reply elements in.
 */
internal object LspWire {
  /**
   * The `initialize` params, mirroring `initializeParams()` in the reference's
   * `packages/lsp/src/connection.ts` (`processId`/`rootUri`/`workspaceFolders` null), with
   * `positionEncodings: ["utf-16"]` declared because utf-16 is the count a Compose text field
   * already keeps.
   */
  fun initializeParams(): JsonObject = buildJsonObject {
    put("processId", JsonNull)
    put("rootUri", JsonNull)
    put("initializationOptions", JsonNull)
    put("workspaceFolders", JsonNull)
    putJsonObject("clientInfo") { put("name", "jupydroid") }
    putJsonObject("capabilities") {
      putJsonObject("textDocument") { putJsonObject("completion") {} }
      putJsonObject("general") { putJsonArray("positionEncodings") { add("utf-16") } }
    }
  }

  /**
   * The `initialize` reply's capability surface. `completionProvider` answers `true` **or** an
   * options object (`resolveProvider`, `triggerCharacters`) - absent-or-false is the only no.
   * `positionEncoding` defaults to `utf-16`, the protocol's own default.
   */
  fun capabilitiesFrom(result: JsonElement?): LanguageCapabilities {
    val caps = result?.jsonObject?.get("capabilities") as? JsonObject ?: JsonObject(emptyMap())
    val completion = caps["completionProvider"]
    return LanguageCapabilities(
      completionProvider = completion != null &&
        completion != JsonNull &&
        (completion as? JsonPrimitive)?.booleanOrNull != false,
      positionEncoding = result?.jsonObject?.get("positionEncoding")
        ?.jsonPrimitive?.contentOrNull ?: "utf-16",
    )
  }

  /** A `didOpen`'s `textDocument` - uri, language id, initial version, and the whole text. */
  fun didOpenParams(uri: String, languageId: String, text: String): JsonObject = buildJsonObject {
    putJsonObject("textDocument") {
      put("uri", uri)
      put("languageId", languageId)
      put("version", 0)
      put("text", text)
    }
  }

  /** A whole-text `didChange` - the reference's own `sendFullTextChange` shape. */
  fun didChangeParams(uri: String, version: Int, text: String): JsonObject = buildJsonObject {
    putJsonObject("textDocument") {
      put("uri", uri)
      put("version", version)
    }
    putJsonArray("contentChanges") { add(buildJsonObject { put("text", text) }) }
  }

  fun didCloseParams(uri: String): JsonObject = buildJsonObject {
    putJsonObject("textDocument") { put("uri", uri) }
  }

  fun completionParams(uri: String, position: LanguagePosition): JsonObject = buildJsonObject {
    putJsonObject("textDocument") { put("uri", uri) }
    putJsonObject("position") {
      put("line", position.line)
      put("character", position.character)
    }
  }

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
    )
  }

  /** `textEdit` is a `TextEdit` (`{range, newText}`) or an `InsertReplaceEdit` (`{insert, replace}`). */
  private fun languageEdit(edit: JsonObject): LanguageEdit? =
    ((edit["range"] as? JsonObject) ?: (edit["insert"] as? JsonObject))
      ?.let(::languageRange)
      ?.let { range ->
        edit["newText"]?.jsonPrimitive?.contentOrNull?.let { LanguageEdit(range, it) }
      }

  private fun languageRange(range: JsonObject): LanguageRange? =
    (range["start"] as? JsonObject)?.let(::languagePosition)?.let { start ->
      (range["end"] as? JsonObject)?.let(::languagePosition)?.let { LanguageRange(start, it) }
    }

  private fun languagePosition(pos: JsonObject): LanguagePosition? =
    pos["line"]?.jsonPrimitive?.intOrNull?.let { line ->
      pos["character"]?.jsonPrimitive?.intOrNull?.let { LanguagePosition(line, it) }
    }
}
