package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.LanguagePosition
import com.jupydroid.app.domain.port.PublishDiagnostics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * [StdioLanguageServer]'s own behaviors, beyond what the contract pins for every port:
 * the wire shape (`Content-Length` via [ScriptedLanguageServer]'s own framing read), the
 * document-version counter, lazy per-Space processes, and protocol lifecycle.
 */
class StdioLanguageServerTest {
  private val servers = mutableListOf<ScriptedLanguageServer>()

  private fun languageServer(
    requestTimeout: kotlin.time.Duration = 30.seconds,
    spawn: () -> ScriptedLanguageServer = { ScriptedLanguageServer().also(servers::add) },
  ) = StdioLanguageServer(
    installSpace = { InstalledSpace(File("/dev/null"), 7, false) },
    spawn = { spawn() },
    requestTimeout = requestTimeout,
    log = SpaceLifecycleLog.Silent,
  )

  @Test
  fun firstUse_sendsInitializeWithTheProtocolDefaults() = runBlocking {
    languageServer().capabilities(BASE_SPACE)

    val init = servers.single().sent("initialize").single()
    val params = init.getValue("params").jsonObject
    assertEquals("null", params["processId"].toString())
    assertEquals("null", params["rootUri"].toString())
    assertEquals("null", params["workspaceFolders"].toString())
    // utf-16 is what a Compose text field counts natively; we declare it so the server
    // negotiates it, per `initializeParams` in the reference's connection.ts.
    val encodings = params.getValue("capabilities").jsonObject
      .getValue("general").jsonObject
      .getValue("positionEncodings").jsonArray
    assertEquals("utf-16", encodings.single().jsonPrimitive.content)
    // `completionItem.documentationFormat` tells the server which markup a resolve may answer
    // in - pylsp picks markdown when we name it, its own docstring renderer's output.
    assertEquals(listOf("markdown", "plaintext"), documentationFormatsDeclaredIn(init))
    val server = servers.single()
    waitUntil { server.sent("initialized").isNotEmpty() }
    assertEquals(1, server.sent("initialized").size)
  }

  @Test
  fun capabilities_isAnsweredFromOneHandshakeNotEveryCall() = runBlocking {
    val languageServer = languageServer()
    languageServer.capabilities(BASE_SPACE)
    languageServer.capabilities(BASE_SPACE)

    assertEquals(1, servers.single().initializeCount)
  }

  @Test
  fun changeDocument_toAnUnopenedUri_opensItFirst() = runBlocking {
    languageServer().changeDocument(BASE_SPACE, URI, "x = 1\n")

    val server = servers.single()
    waitUntil { server.sent("textDocument/didOpen").isNotEmpty() }
    val open = server.sent("textDocument/didOpen").single()
    assertEquals(URI, open.textDocument["uri"]!!.jsonPrimitive.content)
    assertEquals("python", open.textDocument["languageId"]!!.jsonPrimitive.content)
    assertEquals("x = 1\n", open.textDocument["text"]!!.jsonPrimitive.content)
    assertTrue(server.sent("textDocument/didChange").isEmpty())
  }

  @Test
  fun didChange_sendsWholeTextWithAVersionThatOnlyCountsUp() = runBlocking {
    val languageServer = languageServer()
    languageServer.openDocument(BASE_SPACE, URI, "python", "x = 1\n")
    languageServer.changeDocument(BASE_SPACE, URI, "x = 2\n")
    languageServer.changeDocument(BASE_SPACE, URI, "x = 3\n")

    val server = servers.single()
    waitUntil { server.sent("textDocument/didChange").size == 2 }
    val changes = server.sent("textDocument/didChange")
    assertEquals(listOf(1, 2), changes.map { it.textDocument["version"]!!.jsonPrimitive.intOrNull })
    assertEquals(
      listOf("x = 2\n", "x = 3\n"),
      changes.map { it.firstChange["text"]!!.jsonPrimitive.content },
    )
  }

  @Test
  fun closeDocument_sendsDidCloseAndReopensFreshly() = runBlocking {
    val languageServer = languageServer()
    languageServer.openDocument(BASE_SPACE, URI, "python", "x = 1\n")
    languageServer.closeDocument(BASE_SPACE, URI)
    languageServer.changeDocument(BASE_SPACE, URI, "y = 1\n")

    val server = servers.single()
    waitUntil { server.sent("textDocument/didOpen").size == 2 }
    assertEquals(1, server.sent("textDocument/didClose").size)
    // After a close the next sync is an open again at version 0, not a ghost change.
    assertEquals(2, server.sent("textDocument/didOpen").size)
    assertEquals(0, server.sent("textDocument/didOpen").last().textDocument["version"]!!.jsonPrimitive.intOrNull)
  }

  @Test
  fun aNotificationBetweenRequestAndReply_corruptsNothing() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.notificationBeforeReply = buildJsonObject {
      put("jsonrpc", "2.0")
      put("method", "textDocument/publishDiagnostics")
      put("params", buildJsonObject {})
    }
    val languageServer = languageServer { server }

    val items = languageServer.completion(BASE_SPACE, URI, LanguagePosition(0, 3))

    assertEquals(2, items.size)
  }

  @Test
  fun aServerToClientRequest_getsANullResultReply() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.serverRequestAfterInitialize = buildJsonObject {
      put("jsonrpc", "2.0")
      put("id", 1001)
      put("method", "workspace/configuration")
      put("params", buildJsonObject {})
    }
    val languageServer = languageServer { server }
    languageServer.capabilities(BASE_SPACE)

    waitUntil { server.snapshot().any { it["id"]?.jsonPrimitive?.intOrNull == 1001 && it["method"] == null } }
    val reply = server.snapshot().first { it["id"]?.jsonPrimitive?.intOrNull == 1001 }
    assertEquals("null", reply["result"].toString())
  }

  @Test
  fun completion_itemCarryingATextEdit_exposesTheEdit() = runBlocking {
    val items = languageServer().completion(BASE_SPACE, URI, LanguagePosition(0, 3))

    val plain = items.first { it.label == "print" }
    assertEquals("print", plain.insertText)
    val edited = items.first { it.label == "os.path.join" }
    assertEquals("path.join", edited.insertText)
    assertEquals("path.join", edited.edit!!.newText)
    assertEquals(3, edited.edit!!.range.start.character)
    assertEquals(9, edited.edit!!.range.end.character)
  }

  /**
   * P5e-2: the row a user reads is the `label` (jedi signature-resolves it - `join(path,
   * paths)`), the row's glyph comes from `kind`, and the list's order is `sortText`'s. A wire
   * that dropped any of the three could only ever show bare insert text.
   */
  @Test
  fun completion_itemCarryingKindAndSortText_exposesBoth() = runBlocking {
    val items = languageServer().completion(BASE_SPACE, URI, LanguagePosition(0, 3))

    val print = items.first { it.label == "print" }
    assertEquals(3, print.kind)
    assertEquals("aprint", print.sortText)
    val edited = items.first { it.label == "os.path.join" }
    assertEquals(null, edited.kind)
    assertEquals(null, edited.sortText)
  }

  @Test
  fun stop_sendsShutdownThenExitAndTheProcessIsGone() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val languageServer = languageServer { server }
    languageServer.capabilities(BASE_SPACE)
    languageServer.stop(BASE_SPACE)

    val methods = server.snapshot().mapNotNull { it["method"]?.jsonPrimitive?.content }
    assertTrue(methods.indexOf("shutdown") < methods.indexOf("exit"))
    assertFalse(server.isAlive)
  }

  @Test
  fun stop_whenNeverStarted_isANoOp() = runBlocking {
    languageServer().stop(BASE_SPACE)
    assertTrue(servers.isEmpty())
  }

  @Test
  fun aServerThatDied_respawnsOnNextUse() = runBlocking {
    val first = ScriptedLanguageServer().also(servers::add)
    var spawnSecond = false
    val languageServer = languageServer {
      if (!spawnSecond) first else ScriptedLanguageServer().also(servers::add)
    }
    languageServer.capabilities(BASE_SPACE)
    spawnSecond = true
    first.destroy()

    val items = languageServer.completion(BASE_SPACE, URI, LanguagePosition(0, 0))

    assertEquals(2, items.size)
    assertEquals(2, servers.size)
    assertEquals(1, servers[1].initializeCount)
  }

  @Test
  fun twoSpaces_getOneServerEach() = runBlocking {
    val other = Space(id = "other", name = "Other", kernels = emptyList())
    val languageServer = languageServer()
    languageServer.capabilities(BASE_SPACE)
    languageServer.capabilities(other)

    assertEquals(2, servers.size)
    languageServer.stop(BASE_SPACE)
    assertFalse(servers[0].isAlive)
    assertTrue(servers[1].isAlive)
  }

  @Test
  fun aRequestTheServerNeverAnswers_timesOut() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.hangsOn += "textDocument/completion"
    val languageServer = languageServer(requestTimeout = 200.milliseconds) { server }

    val failure = runCatching {
      languageServer.completion(BASE_SPACE, URI, LanguagePosition(0, 0))
    }.exceptionOrNull()

    assertTrue("$failure", failure is IllegalStateException)
    waitUntil { server.sent("$/cancelRequest").isNotEmpty() }
  }

  @Test
  fun aRequestCancelledByItsCaller_sendsCancelRequest() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    server.hangsOn += "textDocument/completion"
    val languageServer = languageServer { server }

    // `rememberCellCompletions` cancels the in-flight ask on the next keystroke - the server
    // hears `$/cancelRequest` rather than finishing work nobody waits for (`lsp.late_reply`).
    val ask = launch(Dispatchers.IO) {
      runCatching { languageServer.completion(BASE_SPACE, URI, LanguagePosition(0, 0)) }
    }
    waitUntil { server.sent("textDocument/completion").isNotEmpty() }
    ask.cancelAndJoin()

    waitUntil { server.sent("$/cancelRequest").isNotEmpty() }
  }

  /**
   * A `publishDiagnostics` the server pushes unprompted reaches the port's stream with its
   * fields intact - uri, range, severity, code, source, message. The bridge exists so no
   * consumer ever parses the raw frame again.
   */
  @Test
  fun aPushedPublishDiagnostics_arrivesOnThePortStream() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val languageServer = languageServer { server }
    languageServer.openDocument(BASE_SPACE, URI, "python", "x = 1\n")
    val received = async { languageServer.diagnostics.first { it.uri == URI } }
    yield()

    server.pushNotification(publishDiagnosticsNotification(URI))

    val pushed = withTimeout(5.seconds) { received.await() }
    assertEquals(URI, pushed.uri)
    val diagnostic = pushed.diagnostics.single()
    assertEquals(1, diagnostic.severity)
    assertEquals("F821", diagnostic.code)
    assertEquals("pyflakes", diagnostic.source)
    assertEquals("undefined name 'x'", diagnostic.message)
    assertEquals(LanguagePosition(0, 6), diagnostic.range.start)
    assertEquals(LanguagePosition(0, 7), diagnostic.range.end)
  }

  /** A notification of any other method is not the diagnostics stream's business. */
  @Test
  fun aPushedNotificationOfAnotherMethod_neverReachesTheStream() = runBlocking {
    val server = ScriptedLanguageServer().also(servers::add)
    val languageServer = languageServer { server }
    languageServer.openDocument(BASE_SPACE, URI, "python", "x = 1\n")
    val seen = mutableListOf<PublishDiagnostics>()
    val watch = launch { languageServer.diagnostics.collect { seen += it } }
    yield()

    server.pushNotification(
      buildJsonObject {
        put("jsonrpc", "2.0")
        put("method", "window/logMessage")
        put("params", buildJsonObject { put("message", "hello") })
      },
    )

    Thread.sleep(200)
    watch.cancel()
    assertTrue(seen.isEmpty())
  }

  private val JsonObject.textDocument: JsonObject
    get() = getValue("params").jsonObject.getValue("textDocument").jsonObject

  private val JsonObject.firstChange: JsonObject
    get() = getValue("params").jsonObject.getValue("contentChanges").jsonArray.first().jsonObject

  /** The `completionItem.documentationFormat` list an `initialize` frame declared, in order. */
  private fun documentationFormatsDeclaredIn(init: JsonObject): List<String> =
    init.getValue("params").jsonObject.getValue("capabilities").jsonObject
      .getValue("textDocument").jsonObject
      .getValue("completion").jsonObject
      .getValue("completionItem").jsonObject
      .getValue("documentationFormat").jsonArray
      .map { it.jsonPrimitive.content }

  private fun waitUntil(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + 5_000
    while (!condition()) {
      check(System.currentTimeMillis() < deadline) { "condition never arrived" }
      Thread.sleep(10)
    }
  }

  private companion object {
    const val URI = "file:///home/jupydroid/test.py"
  }
}
