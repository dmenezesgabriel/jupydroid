package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * The kernel-lifecycle calls jupyter_server exposes over REST, as opposed to the traffic
 * that flows over a kernel's WebSocket.
 *
 * Interrupt lives here rather than on the socket because that is where jupyter_server puts
 * it: a busy kernel is not reading its shell channel, so a message asking it to stop would
 * queue behind the work it is meant to stop.
 */
/**
 * What `POST api/sessions` hands back: the kernel to talk to, and the session that owns it.
 *
 * Both, because they address different endpoints - `api/kernels/<kernelId>` for interrupt and
 * restart, `api/sessions/<serverSessionId>` for the shutdown that ends them both. Returning only
 * the kernel id is what P1d had to undo.
 */
data class StartedKernelSession(val kernelId: String, val serverSessionId: String)

interface KernelLifecycle {
  /**
   * Opens a **session** for the document at [documentPath] and returns it with its kernel.
   *
   * A session, not a bare kernel, because that is the only way JupyterLab starts one:
   * `SessionContext({path, type, name, kernelPreference})` (`packages/docregistry/src/context.ts`)
   * over `POST api/sessions` (`packages/services/src/session/restapi.ts`). Two things follow that
   * `POST api/kernels` could not give:
   *
   * - **Reopening a document reattaches to the kernel it already has**, with its namespace intact.
   *   The server decides that, not us: `SessionRootHandler.post` does
   *   `exists = session_exists(path)` and returns `get_session(path)` when it does, creating one
   *   only otherwise (`services/sessions/handlers.py`).
   * - **The session is what a shutdown accounts for** - `DELETE api/sessions/<id>`, which is
   *   [shutdownSession] and why this returns the session's id as well as the kernel's.
   *
   * The path is also what gives the kernel a working directory: `create_session` reaches the same
   * `start_kernel(path=...)`, and `jupyter_server` sets `cwd` only when given one
   * (`services/kernels/kernelmanager.py`). Without it the kernel inherits the server process's cwd,
   * which on the device is a read-only filesystem - `open('data.csv', 'w')` in a cell raised
   * `OSError: [Errno 30]` until X2h. It is the **document's** path, not its directory:
   * `cwd_for_path` walks up to the directory holding it.
   *
   * ```
   * lifecycle.start("reports/Analysis.ipynb")  // -> kernel-abc in session-1, reattaching if open
   * ```
   */
  suspend fun start(documentPath: String): StartedKernelSession

  suspend fun interrupt(kernelId: String)

  /** Replaces the kernel process. The id survives; the interpreter behind it does not. */
  suspend fun restart(kernelId: String)

  /**
   * Ends the session and, with it, its kernel: `DELETE api/sessions/<serverSessionId>`.
   *
   * A session the server no longer has is **not** an error - it is gone, which is the point of
   * asking. That is the reference's rule (`restapi.ts:65-70`, 404 warns and returns) and it is
   * reachable here for a reason of our own: `jupyter_server` culls an idle kernel by itself (P1b).
   */
  suspend fun shutdownSession(serverSessionId: String)
}

/** Raised when jupyter_server refuses a lifecycle call; the message carries the status. */
class KernelLifecycleException(message: String) : IOException(message)

/**
 * ```
 * OkHttpKernelLifecycle(connection).start("Analysis.ipynb")
 * ```
 */
class OkHttpKernelLifecycle(
  private val connection: ServerConnection,
  private val client: OkHttpClient = OkHttpClient(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  // A shutdown the server had already done is worth a line and not an exception; see
  // [shutdownSession]. Silent by default so the tests that do not read the log need not pass one.
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
) : KernelLifecycle {
  override suspend fun start(documentPath: String): StartedKernelSession {
    val request = SessionStartRequest.forDocument(documentPath)
    val body = post("api/sessions", Json.encodeToString(SessionStartRequest.serializer(), request))
    val session = Json.parseToJsonElement(body).jsonObject
    return StartedKernelSession(
      kernelId = session["kernel"]?.jsonObject?.get("id")?.jsonPrimitive?.content
        ?: throw KernelLifecycleException("session for '$documentPath' has no kernel id; server said: $body"),
      serverSessionId = session["id"]?.jsonPrimitive?.content
        ?: throw KernelLifecycleException("session for '$documentPath' has no id of its own; server said: $body"),
    )
  }

  override suspend fun interrupt(kernelId: String) {
    post("api/kernels/$kernelId/interrupt")
  }

  override suspend fun restart(kernelId: String) {
    post("api/kernels/$kernelId/restart")
  }

  /**
   * The three statuses the reference distinguishes, and nothing else counts as done.
   *
   * 404 means somebody got there first - `jupyter_server`'s own cull, most likely - and the caller
   * wanted the session gone, so that is success with a line in the log. 410 is the one shape worth
   * its own sentence: the kernel went and the session did not, which leaves the server holding a
   * record of a process that is not there. Anything else, **200 included**, is a refusal.
   */
  override suspend fun shutdownSession(serverSessionId: String) = withContext(ioDispatcher) {
    val request = Request.Builder()
      .url(connection.baseUrl + sessionPath(serverSessionId))
      .header("Authorization", "token ${connection.token}")
      .delete()
      .build()

    client.newCall(request).execute().use { requireSessionGone(it, serverSessionId) }
  }

  /**
   * The three statuses the reference distinguishes, and nothing else counts as done.
   *
   * 404 means somebody got there first - `jupyter_server`'s own cull, most likely - and the caller
   * wanted the session gone, so that is success with a line in the log. 410 is the one shape worth
   * its own sentence: the kernel went and the session did not, which leaves the server holding a
   * record of a process that is not there. Anything else, **200 included**, is a refusal.
   */
  private fun requireSessionGone(response: Response, serverSessionId: String) {
    val body = response.body?.string().orEmpty()
    when (response.code) {
      SESSION_DELETED -> Unit
      SESSION_ALREADY_GONE -> log.reached(KernelLog.SESSION_ALREADY_GONE, mapOf("sessionId" to serverSessionId))
      KERNEL_DELETED_BEFORE_SESSION -> throw KernelLifecycleException(
        "session '$serverSessionId' outlived its kernel: HTTP ${response.code} $body; expected $SESSION_DELETED",
      )
      else -> throw KernelLifecycleException(
        "DELETE ${sessionPath(serverSessionId)} failed: HTTP ${response.code} $body; expected $SESSION_DELETED",
      )
    }
  }

  private suspend fun post(path: String, body: String = "{}"): String = withContext(ioDispatcher) {
    val request = Request.Builder()
      .url(connection.baseUrl + path)
      .header("Authorization", "token ${connection.token}")
      // jupyter_server rejects a POST with no body, so every call sends at least an empty object.
      .post(body.toRequestBody(JSON))
      .build()

    client.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) {
        throw KernelLifecycleException("POST $path failed: HTTP ${response.code} $body")
      }
      body
    }
  }

  private companion object {
    val JSON = "application/json".toMediaType()

    fun sessionPath(serverSessionId: String) = "api/sessions/$serverSessionId"

    /** `self.set_status(204)` in `SessionHandler.delete`, and the only status the client accepts. */
    const val SESSION_DELETED = 204
    const val SESSION_ALREADY_GONE = 404

    /** `raise web.HTTPError(410, "Kernel deleted before session")` - the server's own words. */
    const val KERNEL_DELETED_BEFORE_SESSION = 410
  }
}

/**
 * What `POST /api/sessions` is given. `kernel` is left out so the server picks its default.
 *
 * `path` and `type` are required and `name` is "not technically required, but is often assumed to
 * be nonempty, so we require it too" (`packages/services/src/session/session.ts:392-404`).
 */
@Serializable
private data class SessionStartRequest(val path: String, val type: String, val name: String) {
  companion object {
    /**
     * Every field derived from the document's path, the way the reference's `Context` derives them.
     *
     * Spelled out rather than left to constructor defaults: kotlinx serialization omits a value
     * equal to its default, so defaulted fields would simply not be sent - and `type` is required.
     */
    fun forDocument(path: String) = SessionStartRequest(
      path = path,
      // `type: ext === '.ipynb' ? 'notebook' : 'file'` (packages/docregistry/src/context.ts:86)
      type = if (path.endsWith(".ipynb")) "notebook" else "file",
      // `PathExt.basename(localPath)` - the document, without the folders above it.
      name = path.substringAfterLast('/'),
    )
  }
}
