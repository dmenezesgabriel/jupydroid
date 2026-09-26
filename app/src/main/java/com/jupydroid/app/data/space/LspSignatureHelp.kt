package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.LanguageParameter
import com.jupydroid.app.domain.port.LanguageSignature
import com.jupydroid.app.domain.port.LanguageSignatureHelp
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * The `textDocument/signatureHelp` reply: `signatures[]` of `{label, parameters[], docs?}`
 * plus the server's own `activeSignature`/`activeParameter` picks, both defaulting to 0 -
 * the `?? 0` `parameterHintsModel.ts` itself applies. `null` and empty `signatures` are the
 * same nothing: the caret sits inside no call the server knows.
 *
 * Kept off [LspWire] the way [LspMarkup] is: the wire holds request params and the leaves
 * replies share, this holds the signature reply.
 */
internal object LspSignatureHelp {
  fun signatureHelpFrom(result: JsonElement?): LanguageSignatureHelp? {
    val reply = result as? JsonObject
    val signatures = (reply?.get("signatures") as? JsonArray).orEmpty().mapNotNull(::signatureFrom)
    if (reply == null || signatures.isEmpty()) return null
    return LanguageSignatureHelp(
      signatures = signatures,
      activeSignature = reply["activeSignature"]?.jsonPrimitive?.intOrNull ?: 0,
      activeParameter = reply["activeParameter"]?.jsonPrimitive?.intOrNull ?: 0,
    )
  }

  private fun signatureFrom(element: JsonElement): LanguageSignature? {
    val signature = element as? JsonObject
    val label = signature?.get("label")?.jsonPrimitive?.contentOrNull
    if (signature == null || label == null) return null
    return LanguageSignature(
      label = label,
      documentation = LspMarkup.documentationOf(signature["documentation"]),
      parameters = (signature["parameters"] as? JsonArray).orEmpty()
        .mapNotNull { parameterFrom(it, label) },
      // A per-signature `activeParameter` outranks the reply-level one - the
      // `signature.activeParameter ?? hints.activeParameter` VS Code itself honours.
      activeParameter = signature["activeParameter"]?.jsonPrimitive?.intOrNull,
    )
  }

  private fun parameterFrom(element: JsonElement, signatureLabel: String): LanguageParameter? {
    val parameter = element as? JsonObject
    val label = parameter?.get("label")
    if (parameter == null || label == null) return null
    val range = parameterRange(label)
    val text = range?.let { parameterSlice(signatureLabel, it) } ?: (label as? JsonPrimitive)?.contentOrNull
    return text?.let {
      LanguageParameter(
        label = it,
        labelRange = range,
        documentation = LspMarkup.documentationOf(parameter["documentation"]),
      )
    }
  }

  /**
   * A parameter `label` may be a `[start, end]` pair into the signature's own label instead
   * of a string - `provideSignatureHelp.ts`'s `getParameterLabelOffsets` takes the pair as
   * the highlight's bounds verbatim, so the range rides beside the substring it yields.
   */
  private fun parameterRange(label: JsonElement): Pair<Int, Int>? {
    val bounds = (label as? JsonArray)?.map { it.jsonPrimitive.intOrNull } ?: return null
    return bounds.takeIf { it.size == 2 && it.all { bound -> bound != null } }
      ?.let { bounds[0]!! to bounds[1]!! }
  }

  /** Out-of-range offsets slice no label rather than crash - the reply is malformed, not fatal. */
  private fun parameterSlice(signature: String, range: Pair<Int, Int>): String? {
    if (range.first !in 0..signature.length || range.second < range.first) return null
    return signature.substring(range.first, range.second.coerceAtMost(signature.length))
  }
}
