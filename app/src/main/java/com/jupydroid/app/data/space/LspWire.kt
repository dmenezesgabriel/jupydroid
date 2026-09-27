package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.LanguageCapabilities
import com.jupydroid.app.domain.port.LanguageFormatOptions
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageRange
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

/** The markup kinds we declare wherever a server may answer markup - hover and resolve share the list. */
private fun markupFormats(): JsonArray = buildJsonArray {
  add("markdown")
  add("plaintext")
}

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
      putJsonObject("textDocument") {
        putJsonObject("completion") {
          // Tells the server which markup a `completionItem/resolve` may answer in - pylsp's
          // docstring renderer emits markdown when the client names it. `snippetSupport` stays
          // undeclared on purpose: with `include_params` on, declaring it would put literal
          // `${1:}` placeholders into `insertText`, which nothing here tab-stops (P5e-0 probe).
          putJsonObject("completionItem") { put("documentationFormat", markupFormats()) }
        }
        // `hover.contentFormat` is the same declaration on the hover side - pylsp's
        // `pylsp_hover` reads it to pick a markup kind (defaulting to markdown when absent),
        // so it's named rather than left to the server's default.
        putJsonObject("hover") { put("contentFormat", markupFormats()) }
      }
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
    return LanguageCapabilities(
      completionProvider = offered(caps["completionProvider"]),
      // `signatureHelpProvider` takes the same boolean-or-options shape - pylsp's reply is
      // the object form (`triggerCharacters: ["(", ",", "="]`, the P5e-0 probe's capture).
      signatureHelpProvider = offered(caps["signatureHelpProvider"]),
      // `definitionProvider` takes the same boolean-or-options shape (`DefinitionOptions`
      // is only `workDoneProgress`) - pylsp's reply is the plain `true` the probe captured.
      definitionProvider = offered(caps["definitionProvider"]),
      // `documentRangeFormattingProvider` - the same shape again (`workDoneProgress` alone),
      // and pylsp's probe-measured `true`.
      documentRangeFormattingProvider = offered(caps["documentRangeFormattingProvider"]),
      positionEncoding = result?.jsonObject?.get("positionEncoding")
        ?.jsonPrimitive?.contentOrNull ?: "utf-16",
      // `hoverProvider` is a plain boolean, and LSP's rule that an undeclared capability is an
      // unsupported one makes `!= true` the gate - pylsp answers `true` explicitly.
      hoverProvider = (caps["hoverProvider"] as? JsonPrimitive)?.booleanOrNull == true,
    )
  }

  /** `true` or an options object both mean the feature is there - only absent-or-`false` is no. */
  private fun offered(provider: JsonElement?): Boolean =
    provider != null && provider != JsonNull && (provider as? JsonPrimitive)?.booleanOrNull != false

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

  /**
   * `TextDocumentPositionParams` - the params every point-in-document request shares
   * (`completion`, `hover`, `definition`, …): which document, and where in it.
   */
  fun positionParams(uri: String, position: LanguagePosition): JsonObject = buildJsonObject {
    putJsonObject("textDocument") { put("uri", uri) }
    putJsonObject("position") {
      put("line", position.line)
      put("character", position.character)
    }
  }

  /**
   * `DocumentRangeFormattingParams` - the document, the range, and the `options` object the
   * spec requires: `tabSize`/`insertSpaces` from the editor's own indentation setting.
   */
  fun rangeFormattingParams(uri: String, range: LanguageRange, options: LanguageFormatOptions): JsonObject =
    buildJsonObject {
      putJsonObject("textDocument") { put("uri", uri) }
      putJsonObject("range") {
        putJsonObject("start") {
          put("line", range.start.line)
          put("character", range.start.character)
        }
        putJsonObject("end") {
          put("line", range.end.line)
          put("character", range.end.character)
        }
      }
      putJsonObject("options") {
        put("tabSize", options.tabSize)
        put("insertSpaces", options.insertSpaces)
      }
    }

  internal fun languageRange(range: JsonObject): LanguageRange? =
    (range["start"] as? JsonObject)?.let(::languagePosition)?.let { start ->
      (range["end"] as? JsonObject)?.let(::languagePosition)?.let { LanguageRange(start, it) }
    }

  private fun languagePosition(pos: JsonObject): LanguagePosition? =
    pos["line"]?.jsonPrimitive?.intOrNull?.let { line ->
      pos["character"]?.jsonPrimitive?.intOrNull?.let { LanguagePosition(line, it) }
    }
}
