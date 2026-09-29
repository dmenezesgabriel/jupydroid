package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.LanguageServerException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
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
 * `textDocument/rename` over [StdioLanguageServer] - its own file the way
 * [StdioLanguageServerDefinitionTest] is: the scripted doubles and the `languageServer`
 * scaffold are the same ones [StdioLanguageServerTest] uses, and the 500-line file gate is
 * what keeps them apart.
 */
class StdioLanguageServerRenameTest {
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
  fun rename_asksWithTheNewName_andParsesTheWorkspaceEdit() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val languageServer = languageServer { server }

    val edit = languageServer.rename(BASE_SPACE, URI, LanguagePosition(line = 2, character = 8), "compute_sum")

    val request = server.sent("textDocument/rename").single()
    val params = request.getValue("params").jsonObject
    val position = params.getValue("position").jsonObject
    assertEquals(2, position.getValue("line").jsonPrimitive.intOrNull)
    assertEquals(8, position.getValue("character").jsonPrimitive.intOrNull)
    assertEquals(URI, request.textDocument["uri"]!!.jsonPrimitive.content)
    // `newName` is the one field `RenameParams` adds to `TextDocumentPositionParams`.
    assertEquals("compute_sum", params.getValue("newName").jsonPrimitive.content)
    // The scripted default is the probe's own reply shape: one whole-document TextEdit on the
    // asked uri.
    val edits = edit.changes.getValue(URI)
    assertEquals(1, edits.size)
    assertTrue(edits[0].newText.contains("compute_sum"))
  }

  /** `result: null`, `{}`, and `{"documentChanges":[]}` are the same nothing - no edits to apply. */
  @Test
  fun rename_answeredNullOrEmpty_isEmpty() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val languageServer = languageServer { server }

    server.renameResult = JsonNull
    assertTrue(languageServer.rename(BASE_SPACE, URI, LanguagePosition(0, 0), "b").changes.isEmpty())
    server.renameResult = buildJsonObject {}
    assertTrue(languageServer.rename(BASE_SPACE, URI, LanguagePosition(0, 0), "b").changes.isEmpty())
    server.renameResult = buildJsonObject { putJsonArray("documentChanges") {} }
    assertTrue(languageServer.rename(BASE_SPACE, URI, LanguagePosition(0, 0), "b").changes.isEmpty())
  }

  /**
   * `renameProvider` absent is an ask never sent - the same undeclared-capability gate the
   * providers before it apply. pylsp answers a plain `true` (the P5e-0 probe's capture).
   */
  @Test
  fun renameProvider_absent_sendsNoRequest() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.initializeResult = buildJsonObject {
      putJsonObject("capabilities") { put("hoverProvider", true) }
    }
    val languageServer = languageServer { server }

    assertTrue(languageServer.rename(BASE_SPACE, URI, LanguagePosition(0, 0), "b").changes.isEmpty())
    assertTrue(server.sent("textDocument/rename").isEmpty())
  }

  /**
   * A JSON-RPC `error` reply is not silence and not a crash: the channel raises the typed
   * refusal carrying the server's own code and message, so "Rename symbol" can toast what
   * pylsp refused - VS Code's `rejectReason` path (`rename.ts`'s
   * `notificationService.error("Rename failed to compute edits")` is the other half of it).
   * Still an [IllegalStateException], so every older catcher keeps holding.
   */
  @Test
  fun aServerErrorReply_raisesTheTypedRefusal() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.errors["textDocument/rename"] = buildJsonObject {
      put("code", -32602)
      put("message", "There's no name under the cursor")
    }
    val languageServer = languageServer { server }

    val failure = runCatching {
      languageServer.rename(BASE_SPACE, URI, LanguagePosition(0, 0), "compute_sum")
    }.exceptionOrNull()

    assertTrue("$failure", failure is LanguageServerException)
    val refusal = failure as LanguageServerException
    assertEquals("textDocument/rename", refusal.method)
    assertEquals(-32602, refusal.code)
    assertEquals("There's no name under the cursor", refusal.serverMessage)
  }

  /**
   * The ask and its answer both reach `JupydroidSpace` - the same `lsp.ask`/`lsp.answer`
   * pair definition and rangeFormatting already log, so a device run shows the rename round
   * trip in `evidence/p5e7-rename/`.
   */
  @Test
  fun rename_reachesTheLifecycleLog_askedAndAnswered() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val log = RecordingSpaceLifecycleLog()
    val languageServer = languageServer(log) { server }

    languageServer.rename(BASE_SPACE, URI, LanguagePosition(line = 2, character = 8), "compute_sum")

    val ask = log.fieldsOf("lsp.ask")
    assertEquals("rename", ask["method"])
    assertEquals(URI, ask["uri"])
    assertEquals("2:8", ask["at"])
    val answer = log.fieldsOf("lsp.answer")
    assertEquals("rename", answer["method"])
    assertEquals(listOf("lsp.ask", "lsp.answer"), log.stages.filter { it.startsWith("lsp.a") })
  }

  /**
   * The scripted default keeps `version` out of the reply's `textDocument` handle - pylsp's
   * real reply sends `version: 0`; a server that sends none parses the same either way.
   */
  @Test
  fun aVersionlessDocumentHandle_parses() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.renameResult = documentChangesReply(version = false)
    val languageServer = languageServer { server }

    val edit = languageServer.rename(BASE_SPACE, URI, LanguagePosition(0, 0), "x")

    assertEquals("x\n", edit.changes.getValue(URI).single().newText)
  }

  private val JsonObject.textDocument: JsonObject
    get() = getValue("params").jsonObject.getValue("textDocument").jsonObject

  /** A `documentChanges` reply for [URI], `version` on the handle only when asked for. */
  private fun documentChangesReply(version: Boolean) = buildJsonObject {
    putJsonArray("documentChanges") {
      addJsonObject {
        putJsonObject("textDocument") {
          put("uri", URI)
          if (version) put("version", 0)
        }
        putJsonArray("edits") { addJsonObject { putRenameEdit() } }
      }
    }
  }

  /** One `TextEdit` spanning (0,0) to (1,0) whose `newText` is `x\n`. */
  private fun kotlinx.serialization.json.JsonObjectBuilder.putRenameEdit() {
    putJsonObject("range") {
      putJsonObject("start") {
        put("line", 0)
        put("character", 0)
      }
      putJsonObject("end") {
        put("line", 1)
        put("character", 0)
      }
    }
    put("newText", "x\n")
  }

  private companion object {
    const val URI = "file:///home/jupydroid/test.py"
  }
}
