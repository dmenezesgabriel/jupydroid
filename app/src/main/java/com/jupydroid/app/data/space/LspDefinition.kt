package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.LanguageLocation
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The `textDocument/definition` reply: `Location`, `Location[]`, `LocationLink[]` or `null`
 * (LSP 3.17) - `null` and `[]` are the same nothing, the symbol at the position has no
 * definition the server knows. `LocationLink` entries carry `targetUri`/`targetRange` instead
 * of `uri`/`range`, so they fall out of the parse rather than jump wrong - pylsp answers a
 * plain `Location[]` (`LspSurfaceProbeTest`'s capture), and a target the client cannot read is
 * worse than no target.
 *
 * Kept off [LspWire] the way [LspMarkup] is: the wire holds request params and the leaves
 * replies share, this holds the definition reply.
 */
internal object LspDefinition {
  fun locationsFrom(result: JsonElement?): List<LanguageLocation> {
    val elements = when (result) {
      is JsonArray -> result
      is JsonObject -> listOf(result)
      else -> return emptyList()
    }
    return elements.mapNotNull(::locationFrom)
  }

  private fun locationFrom(element: JsonElement): LanguageLocation? {
    val location = element as? JsonObject
    val uri = location?.get("uri")?.jsonPrimitive?.contentOrNull
    val range = (location?.get("range") as? JsonObject)?.let(LspWire::languageRange)
    return if (uri != null && range != null) LanguageLocation(uri, range) else null
  }
}
