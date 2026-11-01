package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelSessionModel
import com.jupydroid.app.domain.port.KernelSpecModels
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.SessionType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException

/**
 * The kernel-lifecycle calls jupyter_server exposes over REST, as opposed to the traffic
 * that flows over a kernel's WebSocket.
 *
 * Interrupt lives here rather than on the socket because that is where jupyter_server puts
 * it: a busy kernel is not reading its shell channel, so a message asking it to stop would
 * queue behind the work it is meant to stop.
 */
/**
 * What `POST api/sessions` (and `PATCH api/sessions/<id>`'s kernel change) hands back: the
 * kernel to talk to, and the session that owns it.
 *
 * Both, because they address different endpoints - `api/kernels/<kernelId>` for interrupt and
 * restart, `api/sessions/<serverSessionId>` for the shutdown that ends them both. Returning only
 * the kernel id is what this had to undo. [kernelName] is the kernelspec the kernel answers to -
 * `kernel.name` in the model - which is what a kernel *choice* displays and persists, while
 * [kernelId] is the server's handle on the process.
 */
data class StartedKernelSession(val kernelId: String, val kernelName: String, val serverSessionId: String)

/** A kernel started with no session around it: `POST api/kernels`' model, `{id, name}`. */
data class BareKernel(val kernelId: String, val kernelName: String)

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
   * `OSError: [Errno 30]` before this was passed. It is the **document's** path, not its directory:
   * `cwd_for_path` walks up to the directory holding it.
   *
   * ```
   * lifecycle.start("reports/Analysis.ipynb")  // -> kernel-abc in session-1, reattaching if open
   * ```
   */
  suspend fun start(
    documentPath: String,
    kernel: KernelChoice? = null,
    sessionType: SessionType? = null,
  ): StartedKernelSession

  /**
   * Starts the server's default kernel with no session, at the workspace root: `POST api/kernels`
   * with `{"path": ""}`. A session binds it later by id - what a Space's spare is (ADR-0080).
   *
   * No `name`, so `MainKernelHandler.post` starts `km.default_kernel_name`: the kernel a session
   * asking for none would get. The empty path makes `cwd_for_path` answer `root_dir` outright,
   * rather than leaving the kernel whatever cwd the server process has (jupyter_server 2.20.0
   * `services/kernels/handlers.py`, `services/kernels/kernelmanager.py`).
   *
   * ```
   * lifecycle.startBareKernel()  // -> BareKernel("kernel-abc", "python3")
   * ```
   */
  suspend fun startBareKernel(): BareKernel

  suspend fun interrupt(kernelId: String)

  /** Replaces the kernel process. The id survives; the interpreter behind it does not. */
  suspend fun restart(kernelId: String)

  /**
   * Every session the server is holding: `GET api/sessions` - `listSessions`
   * (`packages/services/src/session/restapi.ts:86-89`), the call the running extension's session
   * manager wraps in its `Poll`. Each model is `Session.IModel` - `{id, name, path, type,
   * kernel}` (`session/session.ts:381-390`) - with `kernel` nullable the way the model's is.
   */
  suspend fun sessions(): List<KernelSessionModel>

  /**
   * Rebinds the session to another kernel: `PATCH api/sessions/<serverSessionId>` with
   * `{"kernel": …}` - `Session.ISessionConnection.changeKernel` (`session/session.ts:169-190`).
   *
   * The **session id is kept**: `SessionHandler.patch` only swaps fields, and when the kernel id
   * changes it shuts the *old* kernel down itself. [KernelChoice.Spec] starts a new kernel from
   * the named spec; [KernelChoice.Running] joins one already running - `kernel id takes
   * precedence over name` is the handler's own comment.
   */
  suspend fun changeKernel(serverSessionId: String, kernel: KernelChoice): StartedKernelSession

  /**
   * Moves the session to its document's new path: `PATCH api/sessions/<serverSessionId>` with
   * `{"path", "name"}`. jupyterlab sends `setPath` and `setName` as two PATCHes
   * (`packages/services/src/session/default.ts`); `SessionHandler.patch` applies both fields from
   * one body (`jupyter_server/services/sessions/handlers.py` in the shipped rootfs), so one
   * request reaches the same end state. The session id and its kernel are kept.
   */
  suspend fun setPath(serverSessionId: String, documentPath: String)

  /**
   * `GET api/kernelspecs` - the `KernelSpec.IManager.specs` feed the picker's "Start Kernel"
   * groups are built from (`sessioncontext.tsx:1657-1663`). The answer is
   * `{default, kernelspecs: {<name>: {name, spec: {display_name, language, …}}}}`
   * (`services/kernelspecs/handlers.py:65-89`); `argv`, `env` and `resources` stay the server's.
   */
  suspend fun kernelSpecs(): KernelSpecModels

  /**
   * Ends the session and, with it, its kernel: `DELETE api/sessions/<serverSessionId>`.
   *
   * A session the server no longer has is **not** an error - it is gone, which is the point of
   * asking. That is the reference's rule (`restapi.ts:65-70`, 404 warns and returns) and it is
   * reachable here for a reason of our own: `jupyter_server` culls an idle kernel by itself.
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
  private val rest = SessionRest(connection, client, ioDispatcher)

  override suspend fun start(
    documentPath: String,
    kernel: KernelChoice?,
    sessionType: SessionType?,
  ): StartedKernelSession {
    val request = SessionStartRequest.forDocument(
      documentPath,
      sessionType ?: SessionType.forPath(documentPath),
      kernel,
    )
    val body = rest.post("api/sessions", Json.encodeToString(SessionStartRequest.serializer(), request))
    return Json.parseToJsonElement(body).jsonObject.toStartedSession(documentPath)
  }

  override suspend fun sessions(): List<KernelSessionModel> {
    val body = rest.get("api/sessions")
    return Json.parseToJsonElement(body).jsonArray.map { it.jsonObject.toSessionModel() }
  }

  override suspend fun changeKernel(serverSessionId: String, kernel: KernelChoice): StartedKernelSession {
    val body = rest.patch(
      "api/sessions/$serverSessionId",
      """{"kernel":${Json.encodeToString(KernelRef.serializer(), kernel.ref())}}""",
    )
    return Json.parseToJsonElement(body).jsonObject.toStartedSession(serverSessionId)
  }

  override suspend fun setPath(serverSessionId: String, documentPath: String) {
    val moved = buildJsonObject {
      put("path", documentPath)
      put("name", documentPath.substringAfterLast('/'))
    }
    rest.patch("api/sessions/$serverSessionId", moved.toString())
  }

  override suspend fun kernelSpecs(): KernelSpecModels {
    val root = Json.parseToJsonElement(rest.get("api/kernelspecs")).jsonObject
    val specs = root["kernelspecs"]?.jsonObject.orEmpty().map { (name, model) ->
      model.jsonObject["spec"]?.jsonObject?.let { kernelSpecOf(name, it) }
        ?: throw KernelLifecycleException("kernelspec '$name' has no display_name; server said: $root")
    }
    return KernelSpecModels(default = root["default"]?.jsonPrimitive?.content.orEmpty(), specs = specs)
  }

  override suspend fun startBareKernel(): BareKernel {
    val model = Json.parseToJsonElement(rest.post("api/kernels", """{"path":""}""")).jsonObject
    fun required(field: String): String = model[field]?.jsonPrimitive?.content
      ?: throw KernelLifecycleException("POST api/kernels answered no '$field' in {id, name}; server said: $model")
    return BareKernel(kernelId = required("id"), kernelName = required("name"))
  }

  override suspend fun interrupt(kernelId: String) {
    rest.post("api/kernels/$kernelId/interrupt")
  }

  override suspend fun restart(kernelId: String) {
    rest.post("api/kernels/$kernelId/restart")
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
    val body = response.body.string()
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

  private companion object {
    fun sessionPath(serverSessionId: String) = "api/sessions/$serverSessionId"

    /** `self.set_status(204)` in `SessionHandler.delete`, and the only status the client accepts. */
    const val SESSION_DELETED = 204
    const val SESSION_ALREADY_GONE = 404

    /** `raise web.HTTPError(410, "Kernel deleted before session")` - the server's own words. */
    const val KERNEL_DELETED_BEFORE_SESSION = 410
  }
}

/**
 * The verbs every lifecycle call shares: an authenticated JSON request against the server
 * [connection] names, answered as a body string or a [KernelLifecycleException].
 * [OkHttpKernelLifecycle.shutdownSession] keeps its own request because it reads the status
 * code itself rather than treating "not successful" as an exception.
 */
private class SessionRest(
  private val connection: ServerConnection,
  private val client: OkHttpClient,
  private val ioDispatcher: CoroutineDispatcher,
) {
  suspend fun get(path: String): String = withContext(ioDispatcher) {
    val request = Request.Builder()
      .url(connection.baseUrl + path)
      .header("Authorization", "token ${connection.token}")
      .get()
      .build()

    client.newCall(request).execute().use { response ->
      val body = response.body.string()
      if (!response.isSuccessful) {
        throw KernelLifecycleException("GET $path failed: HTTP ${response.code} $body")
      }
      body
    }
  }

  suspend fun post(path: String, body: String = "{}"): String = withContext(ioDispatcher) {
    request(path, "POST", body)
  }

  suspend fun patch(path: String, body: String): String = withContext(ioDispatcher) {
    request(path, "PATCH", body)
  }

  private fun request(path: String, method: String, body: String): String {
    val request = Request.Builder()
      .url(connection.baseUrl + path)
      .header("Authorization", "token ${connection.token}")
      // jupyter_server rejects a POST with no body, so every call sends at least an empty object.
      .method(method, body.toRequestBody(JSON))
      .build()

    return client.newCall(request).execute().use { response ->
      val answer = response.body.string()
      if (!response.isSuccessful) {
        throw KernelLifecycleException("$method $path failed: HTTP ${response.code} $answer")
      }
      answer
    }
  }

  private companion object {
    val JSON = "application/json".toMediaType()
  }
}

/**
 * One element of `GET api/sessions`' array. `id` is required - the row cannot be shut down
 * without it - and `kernel` is read defensively because `Session.IModel` declares it nullable
 * (`session/session.ts:389`): `jsonObject` would throw on `JsonNull`, which is a state the
 * server is entitled to send.
 */
private fun JsonObject.toSessionModel(): KernelSessionModel {
  val kernel = this["kernel"] as? JsonObject
  fun required(field: String): String = this[field]?.jsonPrimitive?.content
    ?: throw KernelLifecycleException("session row in GET api/sessions has no '$field'; row was: $this")
  return KernelSessionModel(
    serverSessionId = required("id"),
    kernelId = kernel?.get("id")?.jsonPrimitive?.content,
    kernelName = kernel?.get("name")?.jsonPrimitive?.content,
    path = required("path"),
    name = required("name"),
    type = required("type"),
  )
}

/**
 * A session model into what a *start or change* needs to keep. Unlike [toSessionModel] this is
 * strict about the kernel: a session that was asked to start one and reports none is a failure
 * here, where the same null in a *list* row is just information (`session/session.ts:389` makes
 * `kernel` nullable either way).
 */
private fun JsonObject.toStartedSession(context: String): StartedKernelSession {
  val kernel = this["kernel"] as? JsonObject
  fun required(field: String, of: JsonObject? = this): String = of?.get(field)?.jsonPrimitive?.content
    ?: throw KernelLifecycleException(
      "session for '$context' has no '$field' at the shape Session.IModel promises; server said: $this",
    )
  return StartedKernelSession(
    kernelId = required("id", kernel),
    kernelName = required("name", kernel),
    serverSessionId = required("id"),
  )
}

/** The `kernel` field of a session POST/PATCH: `{name}` for a spec, `{id}` for a running kernel. */
@Serializable
private data class KernelRef(val name: String? = null, val id: String? = null)

private fun KernelChoice.ref(): KernelRef = when (this) {
  is KernelChoice.Spec -> KernelRef(name = name)
  is KernelChoice.Running -> KernelRef(id = kernelId)
}

/**
 * What `POST /api/sessions` is given. `kernel` is left out so the server picks its default -
 * `No kernel specified, using default kernel` is the handler's own comment.
 *
 * `path` and `type` are required and `name` is "not technically required, but is often assumed to
 * be nonempty, so we require it too" (`packages/services/src/session/session.ts:392-404`).
 */
@Serializable
private data class SessionStartRequest(
  val path: String,
  val type: String,
  val name: String,
  val kernel: KernelRef? = null,
) {
  companion object {
    /**
     * Every field derived from the document's path, the way the reference's `Context` derives
     * them - except [type], which is `SessionContext.IOptions.type` and the caller's to declare:
     * `ConsolePanel` writes `console` over a path that would derive `file`
     * (`packages/console/src/panel.ts:71`).
     *
     * Spelled out rather than left to constructor defaults: kotlinx serialization omits a value
     * equal to its default, so defaulted fields would simply not be sent - and `type` is required.
     */
    fun forDocument(path: String, type: SessionType, kernel: KernelChoice? = null) = SessionStartRequest(
      path = path,
      type = type.wire,
      // `PathExt.basename(localPath)` - the document, without the folders above it.
      name = path.substringAfterLast('/'),
      kernel = kernel?.ref(),
    )
  }
}
