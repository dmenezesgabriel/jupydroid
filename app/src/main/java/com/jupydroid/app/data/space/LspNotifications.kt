package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.LanguageDiagnostic
import com.jupydroid.app.domain.port.PublishDiagnostics
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The JSON-RPC notifications a language server pushes unprompted - sibling to [LspWire], which
 * holds the client-initiated shapes. Today the only method a consumer owns is
 * `textDocument/publishDiagnostics`; [JsonRpcChannel] routes every notification and this is
 * where each one's wire shape is parsed.
 */
internal object LspNotifications {
  /**
   * A `textDocument/publishDiagnostics` frame as a [PublishDiagnostics], or null when
   * [notification] is any other method or is missing the fields the method owes.
   */
  fun publishDiagnosticsFrom(notification: JsonObject): PublishDiagnostics? {
    if (notification["method"]?.jsonPrimitive?.contentOrNull != "textDocument/publishDiagnostics") return null
    return (notification["params"] as? JsonObject)?.let(::publishDiagnostics)
  }

  private fun publishDiagnostics(params: JsonObject): PublishDiagnostics? =
    params["uri"]?.jsonPrimitive?.contentOrNull?.let { uri ->
      val diagnostics = (params["diagnostics"] as? JsonArray).orEmpty().mapNotNull {
        (it as? JsonObject)?.let(::diagnostic)
      }
      PublishDiagnostics(uri, diagnostics)
    }

  /** One `Diagnostic` - `severity` defaults to 1 (error), the protocol's own rule for an absent one. */
  private fun diagnostic(item: JsonObject): LanguageDiagnostic? =
    (item["range"] as? JsonObject)?.let(LspWire::languageRange)?.let { range ->
      item["message"]?.jsonPrimitive?.contentOrNull?.let { message ->
        LanguageDiagnostic(
          range = range,
          severity = item["severity"]?.jsonPrimitive?.intOrNull ?: 1,
          // `code` is a string or a number on the wire; either reduces to what a user greps for.
          code = item["code"]?.jsonPrimitive?.contentOrNull,
          source = item["source"]?.jsonPrimitive?.contentOrNull,
          message = message,
        )
      }
    }
}
