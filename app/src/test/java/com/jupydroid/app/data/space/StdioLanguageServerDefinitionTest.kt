package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.port.LanguagePosition
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * `textDocument/definition` over [StdioLanguageServer] - its own file the way
 * [StdioLanguageServerSignatureHelpTest] is: the scripted doubles and the `languageServer`
 * scaffold are the same ones [StdioLanguageServerTest] uses, and the 500-line file gate is
 * what keeps them apart.
 */
class StdioLanguageServerDefinitionTest {
  private val servers = mutableListOf<ScriptedLanguageServer>()

  private fun languageServer(
    log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
    spawn: () -> ScriptedLanguageServer = { ScriptedLanguageServer().also(servers::add) },
  ) = StdioLanguageServer(
    installSpace = { InstalledSpace(File("/dev/null"), 7, false) },
    spawn = { spawn() },
    requestTimeout = 30.seconds,
    log = log,
  )

  @Test
  fun definition_asksAtThePosition_andParsesTheLocations() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val languageServer = languageServer { server }

    val locations = languageServer.definition(BASE_SPACE, URI, LanguagePosition(line = 2, character = 8))

    val request = server.sent("textDocument/definition").single()
    val position = request.getValue("params").jsonObject.getValue("position").jsonObject
    assertEquals(2, position.getValue("line").jsonPrimitive.intOrNull)
    assertEquals(8, position.getValue("character").jsonPrimitive.intOrNull)
    assertEquals(URI, request.textDocument["uri"]!!.jsonPrimitive.content)
    // The scripted default is the probe's own reply: one Location naming the asked uri and
    // compute_total's definition span (`evidence/p5e-0-lsp-surface/`).
    val location = locations.single()
    assertEquals(URI, location.uri)
    assertEquals(LanguagePosition(4, 4), location.range.start)
    assertEquals(LanguagePosition(4, 17), location.range.end)
  }

  /** `result: null` and `result: []` are the same nothing - nothing defines the symbol. */
  @Test
  fun definition_answeredNullOrEmpty_isEmpty() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val languageServer = languageServer { server }

    server.definitionResult = JsonNull
    assertTrue(languageServer.definition(BASE_SPACE, URI, LanguagePosition(0, 0)).isEmpty())
    server.definitionResult = buildJsonArray {}
    assertTrue(languageServer.definition(BASE_SPACE, URI, LanguagePosition(0, 0)).isEmpty())
  }

  /**
   * `definitionProvider` absent is an ask never sent - the same undeclared-capability gate
   * `hoverProvider` and `signatureHelpProvider` already apply.
   */
  @Test
  fun definitionProvider_absent_sendsNoRequest() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.initializeResult = buildJsonObject {
      putJsonObject("capabilities") { put("hoverProvider", true) }
    }
    val languageServer = languageServer { server }

    assertTrue(languageServer.definition(BASE_SPACE, URI, LanguagePosition(0, 0)).isEmpty())
    assertTrue(server.sent("textDocument/definition").isEmpty())
  }

  /** LSP lets a server answer a bare `Location` instead of the array - it parses as one. */
  @Test
  fun definition_answeredASingleLocationObject_parsesAsOne() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.definitionResult = buildJsonObject {
      put("uri", URI)
      putJsonObject("range") {
        putJsonObject("start") {
          put("line", 4)
          put("character", 4)
        }
        putJsonObject("end") {
          put("line", 4)
          put("character", 17)
        }
      }
    }
    val languageServer = languageServer { server }

    val location = languageServer.definition(BASE_SPACE, URI, LanguagePosition(0, 0)).single()

    assertEquals(URI, location.uri)
    assertEquals(LanguagePosition(4, 4), location.range.start)
  }

  /**
   * A `LocationLink` entry carries `targetUri`/`targetRange` rather than `uri`/`range` - it
   * drops out of the parse rather than produce a location the client would jump to wrong.
   */
  @Test
  fun definition_answeredALocationLink_isDropped() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.definitionResult = buildJsonArray { add(locationLink()) }
    val languageServer = languageServer { server }

    assertTrue(languageServer.definition(BASE_SPACE, URI, LanguagePosition(0, 0)).isEmpty())
  }

  /**
   * The ask and its answer both reach `JupydroidSpace`: a definition miss is what
   * `definitionTarget`'s uri filter drops, and only a device shows that - the uris the server
   * answered with are the evidence (`evidence/p5e5-definition/`).
   */
  @Test
  fun definition_reachesTheLifecycleLog_askedAndAnswered() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val log = RecordingSpaceLifecycleLog()
    val languageServer = languageServer(log) { server }

    languageServer.definition(BASE_SPACE, URI, LanguagePosition(line = 2, character = 8))

    val ask = log.fieldsOf("lsp.ask")
    assertEquals("definition", ask["method"])
    assertEquals(URI, ask["uri"])
    assertEquals("2:8", ask["at"])
    val answer = log.fieldsOf("lsp.answer")
    assertEquals("1", answer["found"])
    assertEquals(URI, answer["uris"])
    assertEquals(listOf("lsp.ask", "lsp.answer"), log.stages.filter { it.startsWith("lsp.a") })
  }

  /** A `LocationLink`-shaped entry - `targetUri`/`targetRange` rather than `uri`/`range`. */
  private fun locationLink() = buildJsonObject {
    put("targetUri", URI)
    putJsonObject("targetRange") {
      putJsonObject("start") {
        put("line", 4)
        put("character", 4)
      }
      putJsonObject("end") {
        put("line", 4)
        put("character", 17)
      }
    }
  }

  private val JsonObject.textDocument: JsonObject
    get() = getValue("params").jsonObject.getValue("textDocument").jsonObject

  private companion object {
    const val URI = "file:///home/jupydroid/test.py"
  }
}
