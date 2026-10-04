package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.port.LanguageMarkupKind
import com.jupydroid.app.domain.port.LanguagePosition
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonObjectBuilder
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * `textDocument/signatureHelp` over [StdioLanguageServer] - kept beside the main suite's
 * own file because these cases pushed it past the 500-line gate; the scripted doubles
 * and the `languageServer` scaffold are the same ones [StdioLanguageServerTest] uses.
 */
class StdioLanguageServerSignatureHelpTest {
  private val servers = mutableListOf<ScriptedLanguageServer>()

  private fun languageServer(spawn: () -> ScriptedLanguageServer = { ScriptedLanguageServer().also(servers::add) }) =
    StdioLanguageServer(
      installSpace = { InstalledSpace(File("/dev/null"), 7, false) },
      spawn = { _, _ -> spawn() },
      requestTimeout = 30.seconds,
      log = SpaceLifecycleLog.Silent,
    )

  @Test
  fun signatureHelp_asksAtThePosition_andParsesTheReply() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val languageServer = languageServer { server }

    val help = languageServer.signatureHelp(BASE_SPACE, URI, LanguagePosition(line = 0, character = 13))

    val request = server.sent("textDocument/signatureHelp").single()
    val position = request.getValue("params").jsonObject.getValue("position").jsonObject
    assertEquals(0, position.getValue("line").jsonPrimitive.intOrNull)
    assertEquals(13, position.getValue("character").jsonPrimitive.intOrNull)
    assertEquals(URI, request.textDocument["uri"]!!.jsonPrimitive.content)
    val signature = help!!.signatures.single()
    assertEquals("join(path: LiteralString, /, *paths: LiteralString) -> LiteralString", signature.label)
    assertEquals(listOf("path", "paths"), signature.parameters.map { it.label })
    assertEquals(0, help.activeSignature)
    assertEquals(0, help.activeParameter)
  }

  /** `result: null` and `signatures: []` are the same nothing - the caret is inside no call. */
  @Test
  fun signatureHelp_answeredNullOrEmpty_isNull() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val languageServer = languageServer { server }

    server.signatureHelpResult = JsonNull
    assertEquals(null, languageServer.signatureHelp(BASE_SPACE, URI, LanguagePosition(0, 0)))
    server.signatureHelpResult = buildJsonObject { putJsonArray("signatures") {} }
    assertEquals(null, languageServer.signatureHelp(BASE_SPACE, URI, LanguagePosition(0, 0)))
  }

  /**
   * `signatureHelpProvider` absent is an ask never sent - `hoverProvider`'s own gate, one
   * method later. pylsp advertises the object form (`triggerCharacters`), so an object must
   * also pass - the scripted default carries it.
   */
  @Test
  fun signatureHelpProvider_absent_sendsNoRequest() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.initializeResult = buildJsonObject {
      putJsonObject("capabilities") { put("hoverProvider", true) }
    }
    val languageServer = languageServer { server }

    assertEquals(null, languageServer.signatureHelp(BASE_SPACE, URI, LanguagePosition(0, 0)))
    assertTrue(server.sent("textDocument/signatureHelp").isEmpty())
  }

  /** A `signatureHelp` reply holding the one signature the block builds. */
  private fun signatureReply(signature: JsonObjectBuilder.() -> Unit): JsonObject = buildJsonObject {
    putJsonArray("signatures") { addJsonObject(signature) }
  }

  /**
   * A parameter `label` may be a `[start, end]` pair into the signature's own label rather
   * than a string - VS Code's `getParameterLabelOffsets` takes the pair as the highlight's
   * bounds verbatim, so the port keeps both the substring and the range it came from.
   */
  @Test
  fun signatureHelp_parameterWithOffsetPairLabel_exposesBoth() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.signatureHelpResult = signatureReply {
      put("label", "join(path, *paths)")
      putJsonArray("parameters") {
        addJsonObject {
          putJsonArray("label") {
            add(5)
            add(9)
          }
        }
      }
    }
    val languageServer = languageServer { server }

    val parameter = languageServer.signatureHelp(BASE_SPACE, URI, LanguagePosition(0, 0))!!
      .signatures.single().parameters.single()

    assertEquals("path", parameter.label)
    assertEquals(5 to 9, parameter.labelRange)
  }

  /**
   * Per-signature `activeParameter` is the override LSP allows and `signature.activeParameter
   * ?? hints.activeParameter` honours - a reply that sets it outranks the reply-level index,
   * and a reply that omits it (pylsp's own) leaves the field null.
   */
  @Test
  fun signatureHelp_perSignatureActiveParameter_isKeptBesideTheLabel() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.signatureHelpResult = signatureReply {
      put("label", "f(a, b)")
      put("activeParameter", 1)
    }
    val languageServer = languageServer { server }

    val help = languageServer.signatureHelp(BASE_SPACE, URI, LanguagePosition(0, 0))!!

    assertEquals(1, help.signatures.single().activeParameter)
    assertEquals(0, help.activeParameter)
  }

  /** `documentation` is `string | MarkupContent`, on the signature and each parameter. */
  @Test
  fun signatureHelp_markupDocumentation_parsesOntoSignatureAndParameter() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.signatureHelpResult = signatureReply {
      put("label", "f(a)")
      put("documentation", "Plain docs.")
      putJsonArray("parameters") {
        addJsonObject {
          put("label", "a")
          put("documentation", "The first.")
        }
      }
    }
    val languageServer = languageServer { server }

    val signature = languageServer.signatureHelp(BASE_SPACE, URI, LanguagePosition(0, 0))!!
      .signatures.single()

    assertEquals(LanguageMarkupKind.MARKDOWN, signature.documentation!!.kind)
    assertEquals("Plain docs.", signature.documentation.value)
    assertEquals("The first.", signature.parameters.single().documentation!!.value)
  }

  /**
   * pylsp's own blank markdown - `{"kind": "markdown", "value": ""}` on the scripted
   * default's signature and parameters - is no documentation at all
   * (`parameterHints.css` `.docs.empty` is `display: none`).
   */
  @Test
  fun signatureHelp_blankMarkup_readsAsNoDocumentation() = runBlocking {
    val languageServer = languageServer { ScriptedLanguageServer().also(servers::add) }

    val signature = languageServer.signatureHelp(BASE_SPACE, URI, LanguagePosition(0, 0))!!
      .signatures.single()

    assertEquals(null, signature.documentation)
    assertEquals(null, signature.parameters.first().documentation)
  }

  private val JsonObject.textDocument: JsonObject
    get() = getValue("params").jsonObject.getValue("textDocument").jsonObject

  private companion object {
    const val URI = "file:///home/jupydroid/test.py"
  }
}
