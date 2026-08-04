package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import java.io.IOException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
  /** Starts a kernel and returns its id. */
  suspend fun start(): String

  suspend fun interrupt(kernelId: String)
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
  override suspend fun start(): String {
    val body = post("api/kernels")
    return Json.parseToJsonElement(body).jsonObject["id"]?.jsonPrimitive?.content
      ?: throw KernelLifecycleException("started kernel has no id; server said: $body")
  }

  override suspend fun interrupt(kernelId: String) {
    post("api/kernels/$kernelId/interrupt")
  }

  private suspend fun post(path: String): String = withContext(ioDispatcher) {
    val request = Request.Builder()
      .url(connection.baseUrl + path)
      .header("Authorization", "token ${connection.token}")
      // jupyter_server rejects a POST with no body, so every call sends an empty object.
      .post("{}".toRequestBody(JSON))
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
