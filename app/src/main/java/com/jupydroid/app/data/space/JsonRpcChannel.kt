package com.jupydroid.app.data.space

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.time.Duration

/**
 * One framed JSON-RPC conversation over a pair of streams - the transport under
 * [StdioLanguageServer], and the same shape a `jupyter-lsp` websocket route would grow into.
 *
 * A single daemon reader drains every frame and routes it three ways, because LSP interleaves
 * them freely: `id` + `result`/`error` completes the pending request, `method` + no `id` is a
 * server notification (published on [notifications] - `publishDiagnostics` is what P5d will
 * collect there), and `method` + `id` is a *server-to-client* request, answered `result: null`
 * so a `workspace/configuration` probe can never stall the reader awaiting a reply we owe.
 *
 * Writes serialize through one mutex - two frames may never interleave on the wire. A close
 * or a truncated frame ends the reader and fails every pending request with `EOFException`.
 */
internal class JsonRpcChannel(
  private val input: InputStream,
  private val output: OutputStream,
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
) : Closeable {
  private val write = Mutex()
  private val nextId = AtomicInteger(1)
  private val pending = ConcurrentHashMap<Int, CompletableDeferred<JsonObject>>()
  private val closed = AtomicBoolean(false)
  private val _notifications = MutableSharedFlow<JsonObject>(extraBufferCapacity = 64)
  val notifications: SharedFlow<JsonObject> = _notifications

  private val reader = thread(isDaemon = true, name = "jsonrpc-reader") { drain() }
  private val json = Json { ignoreUnknownKeys = true }

  /**
   * One `method` call and its `result`, correlated by a fresh id until a reply, [timeout], or
   * EOF. A timeout sends `$/cancelRequest` - the LSP cancellation convention - and throws
   * `TimeoutCancellationException`; a server error reply throws `IllegalStateException`.
   */
  suspend fun request(method: String, params: JsonObject, timeout: Duration = Duration.INFINITE): JsonObject {
    val id = nextId.getAndIncrement()
    val reply = CompletableDeferred<JsonObject>()
    pending[id] = reply
    try {
      send(rpc(id, method, params))
      val body = withTimeout(timeout) { reply.await() }
      body["error"]?.let { throw IllegalStateException("$method returned error: $it") }
      return body
    } catch (e: TimeoutCancellationException) {
      runCatching { cancel(id) }
      throw e
    } finally {
      pending.remove(id)
    }
  }

  /** A `method` with no `id` - LSP notifications like `initialized`, `didOpen`, `exit`. */
  suspend fun notify(method: String, params: JsonObject) = send(rpc(null, method, params))

  /**
   * `$/cancelRequest` for [id] - whether a server honours it is its business, but a timed-out
   * call owes it this courtesy before walking away.
   */
  private suspend fun cancel(id: Int) = send(rpc(null, "$/cancelRequest", buildJsonObject { put("id", id) }))

  override fun close() {
    if (closed.getAndSet(true)) return
    runCatching { output.close() }
    runCatching { input.close() }
    failPending(EOFException("language server closed"))
  }

  private suspend fun send(body: JsonObject) = write.withLock {
    LspFraming.writeFrame(output, body.toString())
  }

  private fun rpc(id: Int?, method: String, params: JsonObject) = buildJsonObject {
    put("jsonrpc", "2.0")
    id?.let { put("id", it) }
    put("method", method)
    put("params", params)
  }

  private fun drain() {
    try {
      while (true) {
        route(LspFraming.readFrame(input) ?: break)
      }
    } catch (e: IOException) {
      log.failed("lsp.read", e)
    } catch (e: IllegalStateException) {
      // A truncated frame - `LspFraming` fails the check rather than hangs.
      log.failed("lsp.read", e)
    } finally {
      close()
    }
  }

  private fun route(body: String) {
    val msg = runCatching { json.parseToJsonElement(body).jsonObject }
      .getOrElse {
        log.failed("lsp.parse", it)
        return
      }
    val id = msg["id"]?.jsonPrimitive?.intOrNull
    when {
      msg.containsKey("method") && id != null -> answerServerRequest(id)
      id != null -> pending[id]?.complete(msg) ?: log.reached("lsp.late_reply", mapOf("id" to "$id"))
      else -> _notifications.tryEmit(msg)
    }
  }

  /**
   * Server-to-client requests get `result: null` - LSP lets a client decline anything it does
   * not implement, and silence would leave the server's own pending map stuck forever.
   */
  private fun answerServerRequest(id: Int) = runBlocking {
    send(
      buildJsonObject {
        put("jsonrpc", "2.0")
        put("id", id)
        put("result", kotlinx.serialization.json.JsonNull)
      },
    )
  }

  private fun failPending(cause: Throwable) {
    pending.values.forEach { it.completeExceptionally(cause) }
    pending.clear()
  }
}
