package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * The terminal calls `jupyter_server` exposes over REST, as opposed to the bytes that flow over a
 * terminal's WebSocket.
 *
 * The split is the server's rather than ours: `jupyter_server_terminals` registers
 * `r"/api/terminals"` and `r"/api/terminals/(\w+)"` for these three
 * (`api_handlers.py:default_handlers`) and a socket route of its own for everything else.
 */
interface TerminalLifecycle {
  /**
   * Starts a shell and returns the name the server gave it.
   *
   * [cwd] is **relative to the server's root**, or null to leave it to the server. The handler
   * resolves a relative path against `server_root_dir` (`api_handlers.py:41-49`) and, since the
   * server's own cwd *is* that root (`docs/DECISIONS.md`), so does the pty when none is sent.
   *
   * ```
   * lifecycle.create(cwd = "reports")   // -> "1"
   * ```
   */
  suspend fun create(cwd: String?): String

  /** Every terminal the server is holding, by name. A restarted server answers with none. */
  suspend fun list(): List<String>

  /**
   * Ends the terminal and kills its shell.
   *
   * A terminal the server no longer has is **not** an error - it is gone, which is what the caller
   * asked for. That is the reference's rule (`restapi.ts:107-113`, 404 warns and returns) and it is
   * reachable here for a reason of our own: a shell that exits takes its terminal with it, because
   * `NamedTermManager.on_eof` pops it from the map.
   */
  suspend fun terminate(name: String)
}

/** Raised when jupyter_server refuses a terminal call; the message carries the status. */
class TerminalLifecycleException(message: String) : IOException(message)

/**
 * ```
 * OkHttpTerminalLifecycle(connection).create(cwd = null)
 * ```
 */
class OkHttpTerminalLifecycle(
  private val connection: ServerConnection,
  private val client: OkHttpClient = OkHttpClient(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  // A terminal the server had already dropped is worth a line and not an exception; see
  // [terminate]. Silent by default so the tests that do not read the log need not pass one.
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
) : TerminalLifecycle {
  override suspend fun create(cwd: String?): String {
    val body = post(TERMINALS, createRequest(cwd))
    return Json.parseToJsonElement(body).jsonObject["name"]?.jsonPrimitive?.content
      ?: throw TerminalLifecycleException("POST $TERMINALS returned a terminal with no name; server said: $body")
  }

  override suspend fun list(): List<String> {
    val body = get(TERMINALS)
    return Json.parseToJsonElement(body).jsonArray.map {
      it.jsonObject["name"]?.jsonPrimitive?.content
        ?: throw TerminalLifecycleException("GET $TERMINALS listed a terminal with no name; server said: $body")
    }
  }

  override suspend fun terminate(name: String) = withContext(ioDispatcher) {
    val request = authorized("$TERMINALS/$name").delete().build()
    client.newCall(request).execute().use { requireTerminalGone(it, name) }
  }

  /**
   * The two statuses that mean the terminal is not there any more, and nothing else counts.
   *
   * 404 means somebody got there first - most likely the shell exiting - and the caller wanted it
   * gone, so that is success with a line in the log. Anything else, **200 included**, is a refusal:
   * the same rule `OkHttpKernelLifecycle.requireSessionGone` applies to a session.
   */
  private fun requireTerminalGone(response: Response, name: String) {
    val body = response.body.string()
    when (response.code) {
      TERMINAL_DELETED -> Unit
      TERMINAL_ALREADY_GONE -> log.reached(TERMINAL_GONE_STAGE, mapOf("terminal" to name))
      else -> throw TerminalLifecycleException(
        "DELETE $TERMINALS/$name failed: HTTP ${response.code} $body; expected $TERMINAL_DELETED",
      )
    }
  }

  /**
   * `{}` when no directory was asked for, never `{"cwd": null}`.
   *
   * The handler branches on `if "cwd" in data` and then calls `Path(data["cwd"])`
   * (`api_handlers.py:41-42`), so a JSON null would reach `Path(None)` and raise. The reference
   * cannot send one either - `JSON.stringify` drops an undefined value.
   */
  private fun createRequest(cwd: String?): String =
    Json.encodeToString(JsonObject.serializer(), JsonObject(cwd?.let { mapOf("cwd" to JsonPrimitive(it)) }.orEmpty()))

  private suspend fun post(path: String, body: String): String = withContext(ioDispatcher) {
    // jupyter_server rejects a POST with no body, so every call sends at least an empty object.
    call(authorized(path).post(body.toRequestBody(JSON)).build(), "POST $path")
  }

  private suspend fun get(path: String): String = withContext(ioDispatcher) {
    call(authorized(path).get().build(), "GET $path")
  }

  private fun call(request: Request, describe: String): String = client.newCall(request).execute().use { response ->
    val body = response.body.string()
    // 200 exactly, not `isSuccessful`: `restapi.ts` errors on `status !== 200`, and the model is
    // only in the body of a 200. A 201 belongs to the sessions endpoint and not to this one.
    if (response.code != OK) throw TerminalLifecycleException("$describe failed: HTTP ${response.code} $body")
    body
  }

  private fun authorized(path: String): Request.Builder = Request.Builder()
    .url(connection.baseUrl + path)
    .header("Authorization", "token ${connection.token}")

  private companion object {
    val JSON = "application/json".toMediaType()

    const val TERMINALS = "api/terminals"

    /** `TerminalRootHandler.post` finishes the model without setting a status, so Tornado sends 200. */
    const val OK = 200

    /** `self.set_status(204)` in `TerminalHandler.delete`. */
    const val TERMINAL_DELETED = 204
    const val TERMINAL_ALREADY_GONE = 404

    const val TERMINAL_GONE_STAGE = "terminal.already.gone"
  }
}
