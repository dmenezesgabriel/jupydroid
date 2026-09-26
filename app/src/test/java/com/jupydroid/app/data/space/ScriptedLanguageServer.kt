package com.jupydroid.app.data.space

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.PipedInputStream
import java.io.PipedOutputStream
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * A named fake [Process] that speaks Content-Length-framed JSON-RPC over real piped streams,
 * so [StdioLanguageServer] is exercised against actual byte I/O - not a stubbed channel - in a
 * plain JVM test. Everything the client sent lands in [received], in wire order, for a test to
 * assert on (`didOpen` before `didChange`, versions that count up, `exit` after `shutdown`).
 *
 * Canned replies are minimal-but-real: `initialize` answers a capability set, `completion`
 * answers [completionItems], `shutdown` answers null, and `exit` closes the server's end the
 * way pylsp does - a read on its stdout then sees a clean EOF.
 */
internal class ScriptedLanguageServer(
  /** What `textDocument/completion` replies carry; tests set their own when they need to. */
  var completionItems: JsonElement = DEFAULT_COMPLETION_ITEMS,
  /** What `textDocument/hover` replies carry - the whole `result`, so a test can send null. */
  var hoverResult: JsonElement = DEFAULT_HOVER,

  /** What `textDocument/signatureHelp` replies carry - the whole `result`, null sendable. */
  var signatureHelpResult: JsonElement = DEFAULT_SIGNATURE_HELP,

  /** The `initialize` reply, replaceable for capability-gate tests (defaults advertise pylsp's). */
  var initializeResult: JsonElement = INITIALIZE_RESULT,
) : Process() {
  /** Every frame the client sent, in arrival order - requests and notifications alike. */
  val received: MutableList<JsonObject> = Collections.synchronizedList(mutableListOf())

  /** A notification to emit just before each response - proves interleaving corrupts nothing. */
  var notificationBeforeReply: JsonObject? = null

  /** Methods that get no reply - a hanging server, for timeout tests. */
  val hangsOn = mutableSetOf<String>()

  /** A server-to-client request to send right after `initialize` replies (id `server-1`). */
  var serverRequestAfterInitialize: JsonObject? = null

  val initializeCount: Int get() = synchronized(received) { received.count { it.method == "initialize" } }

  private val clientWrites = PipedOutputStream()
  private val serverReads = PipedInputStream(clientWrites)
  private val serverWrites = PipedOutputStream()
  private val clientReads = PipedInputStream(serverWrites)
  private val exits = CountDownLatch(1)
  private val json = Json { ignoreUnknownKeys = true }

  private val server = thread(isDaemon = true, name = "scripted-lsp") { serve() }

  override fun getOutputStream() = clientWrites
  override fun getInputStream() = clientReads
  override fun getErrorStream(): InputStream = ByteArrayInputStream(byteArrayOf())
  override fun waitFor(): Int {
    exits.await()
    return 0
  }
  override fun waitFor(timeout: Long, unit: TimeUnit): Boolean = exits.await(timeout, unit)
  override fun exitValue(): Int = if (exits.count == 0L) 0 else throw IllegalThreadStateException("still running")
  override fun destroy() {
    runCatching { clientWrites.close() }
    runCatching { serverWrites.close() }
    exits.countDown()
  }

  /** Frames the client sent for one [method] - `didOpen`s, `didChange`s - in order. */
  fun sent(method: String): List<JsonObject> = synchronized(received) {
    received.filter { it.method == method }
  }

  /** Everything the client sent so far - iterating [received] itself needs this lock's copy. */
  fun snapshot(): List<JsonObject> = synchronized(received) { received.toList() }

  /** A server-pushed notification, the way `publishDiagnostics` arrives unasked. */
  fun pushNotification(notification: JsonObject) = emit(notification)

  private fun serve() {
    try {
      while (true) {
        val body = LspFraming.readFrame(serverReads) ?: break
        val msg = json.parseToJsonElement(body).jsonObject
        received += msg
        respond(msg)
      }
    } catch (_: Exception) {
      // A closed pipe is how destroy() arrives.
    } finally {
      exits.countDown()
    }
  }

  private fun respond(msg: JsonObject) {
    val method = msg.method ?: return
    val id = msg["id"]
    if (id != null && method in hangsOn) return
    when (method) {
      "initialize" -> onInitialize(id)
      "textDocument/completion", "textDocument/hover", "textDocument/signatureHelp" -> onDocumentAsk(method, id)
      "shutdown" -> reply(id, JsonNull)
      "exit" -> destroy()
      else -> if (id != null) reply(id, JsonNull)
    }
  }

  private fun onInitialize(id: JsonElement?) {
    emitNotification()
    reply(id, initializeResult)
    serverRequestAfterInitialize?.let { emit(it) }
  }

  private fun onDocumentAsk(method: String, id: JsonElement?) {
    emitNotification()
    val result = when (method) {
      "textDocument/hover" -> hoverResult
      "textDocument/signatureHelp" -> signatureHelpResult
      else -> completionResult()
    }
    reply(id, result)
  }

  /** The `{isIncomplete, items}` CompletionList shape pylsp itself answers with. */
  private fun completionResult() = buildJsonObject {
    put("isIncomplete", false)
    put("items", completionItems)
  }

  private fun reply(id: JsonElement?, result: JsonElement) {
    if (id == null) return
    emit(
      buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", result)
      },
    )
  }

  private fun emitNotification() {
    notificationBeforeReply?.let(::emit)
  }

  private fun emit(msg: JsonObject) {
    runCatching { LspFraming.writeFrame(serverWrites, msg.toString()) }
  }

  private val JsonObject.method: String? get() = this["method"]?.jsonPrimitive?.content

  private companion object {
    val INITIALIZE_RESULT = buildJsonObject {
      putJsonObject("capabilities") {
        put("completionProvider", true)
        put("hoverProvider", true)
        // pylsp's real reply: an options object, not a boolean - `["(", ",", "="]` measured.
        putJsonObject("signatureHelpProvider") {
          putJsonArray("triggerCharacters") {
            add("(")
            add(",")
            add("=")
          }
        }
        putJsonObject("textDocumentSync") {
          put("openClose", true)
          put("change", 2)
        }
      }
      putJsonObject("serverInfo") {
        put("name", "scripted")
        put("version", "0.1")
      }
    }
  }
}

/** A realistic `textDocument/hover` payload: markdown `MarkupContent` and the range it covers. */
internal val DEFAULT_HOVER: JsonObject = buildJsonObject {
  putJsonObject("contents") {
    put("kind", "markdown")
    put("value", "```python\ncompute_total(values)\n```\n\nSum the values.")
  }
  putJsonObject("range") {
    putJsonObject("start") {
      put("line", 1)
      put("character", 0)
    }
    putJsonObject("end") {
      put("line", 1)
      put("character", 13)
    }
  }
}

/**
 * The `textDocument/signatureHelp` payload the P5e-0 probe measured off real `pylsp`
 * (`evidence/p5e-0-lsp-surface/`): one signature, string param labels, both actives 0.
 */
internal val DEFAULT_SIGNATURE_HELP: JsonObject = buildJsonObject {
  putJsonArray("signatures") {
    addJsonObject {
      put("label", "join(path: LiteralString, /, *paths: LiteralString) -> LiteralString")
      putJsonObject("documentation") {
        put("kind", "markdown")
        put("value", "")
      }
      putJsonArray("parameters") {
        addJsonObject {
          put("label", "path")
          putJsonObject("documentation") {
            put("kind", "markdown")
            put("value", "")
          }
        }
        addJsonObject {
          put("label", "paths")
          putJsonObject("documentation") {
            put("kind", "markdown")
            put("value", "")
          }
        }
      }
    }
  }
  put("activeSignature", 0)
  put("activeParameter", 0)
}

/** A realistic `textDocument/completion` payload: a plain item and one carrying a `textEdit`. */
internal val DEFAULT_COMPLETION_ITEMS: JsonArray = buildJsonArray {
  add(
    buildJsonObject {
      put("label", "print")
      put("insertText", "print")
      put("kind", 3)
      put("sortText", "aprint")
      put("detail", "built-in function")
    },
  )
  add(
    buildJsonObject {
      put("label", "os.path.join")
      putJsonObject("textEdit") {
        putJsonObject("range") {
          putJsonObject("start") {
            put("line", 0)
            put("character", 3)
          }
          putJsonObject("end") {
            put("line", 0)
            put("character", 9)
          }
        }
        put("newText", "path.join")
      }
    },
  )
}
