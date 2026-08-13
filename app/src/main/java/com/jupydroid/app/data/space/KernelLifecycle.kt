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

/**
 * The kernel-lifecycle calls jupyter_server exposes over REST, as opposed to the traffic
 * that flows over a kernel's WebSocket.
 *
 * Interrupt lives here rather than on the socket because that is where jupyter_server puts
 * it: a busy kernel is not reading its shell channel, so a message asking it to stop would
 * queue behind the work it is meant to stop.
 */
interface KernelLifecycle {
  /**
   * Starts a kernel **at [documentPath]** and returns its id.
   *
   * The path is what gives the kernel a working directory, and it is not optional in practice:
   * `jupyter_server` sets `cwd` only when it is given one -
   * `if path is not None: kwargs["cwd"] = self.cwd_for_path(path, ...)`
   * (`services/kernels/kernelmanager.py`) - and otherwise the kernel inherits the server process's
   * cwd, which on the device is a read-only filesystem. That is why `open('data.csv', 'w')` in a
   * cell raised `OSError: [Errno 30]` until X2h.
   *
   * The **document's** path, not its directory: `cwd_for_path` walks up to the directory holding
   * it, and up to `root_dir` if it has to.
   *
   * JupyterLab reaches the same server code through a *session*, whose path it sets from the
   * document (`SessionContext({path, type: 'notebook'})`); `POST /api/kernels` accepts the path
   * directly (`services/kernels/handlers.py`), which is parity for the working directory without
   * claiming to be the session lifecycle Steps 17/18 own.
   */
  suspend fun start(documentPath: String): String

  suspend fun interrupt(kernelId: String)

  /** Replaces the kernel process. The id survives; the interpreter behind it does not. */
  suspend fun restart(kernelId: String)
}

/** Raised when jupyter_server refuses a lifecycle call; the message carries the status. */
class KernelLifecycleException(message: String) : IOException(message)

/**
 * ```
 * OkHttpKernelLifecycle(connection).start()
 * ```
 */
class OkHttpKernelLifecycle(
  private val connection: ServerConnection,
  private val client: OkHttpClient = OkHttpClient(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : KernelLifecycle {
  override suspend fun start(documentPath: String): String {
    val request = KernelStartRequest(documentPath)
    val body = post("api/kernels", Json.encodeToString(KernelStartRequest.serializer(), request))
    return Json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.content
      ?: throw KernelLifecycleException("started kernel has no id; server said: $body")
  }

  override suspend fun interrupt(kernelId: String) {
    post("api/kernels/$kernelId/interrupt")
  }

  override suspend fun restart(kernelId: String) {
    post("api/kernels/$kernelId/restart")
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
  }
}

/** What `POST /api/kernels` is given; `name` is left out so the server picks its default. */
@Serializable
private data class KernelStartRequest(val path: String)
