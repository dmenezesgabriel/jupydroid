package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The OkHttp implementation of [KernelSocketOpener], and the only place OkHttp appears
 * above the HTTP client itself.
 *
 * No subprotocol is requested, so jupyter_server uses its original JSON protocol - one JSON
 * text frame per message, which is what [JupyterFrame] reads.
 *
 * ```
 * OkHttpKernelSocketOpener(connection).open(kernelId, sessionId) { event -> … }
 * ```
 */
class OkHttpKernelSocketOpener(
  private val connection: ServerConnection,
  private val client: OkHttpClient = OkHttpClient(),
) : KernelSocketOpener {
  override fun open(kernelId: String, sessionId: String, onEvent: (KernelSocketEvent) -> Unit): KernelSocket =
    OkHttpKernelSocket(client.newWebSocket(request(kernelId, sessionId), listener(onEvent)))

  private fun listener(onEvent: (KernelSocketEvent) -> Unit) = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) = onEvent(KernelSocketEvent.Opened)

    override fun onMessage(webSocket: WebSocket, text: String) = onEvent(KernelSocketEvent.Received(text))

    // The HTTP status matters: a refused upgrade is a 404 for a kernel that is gone, and
    // reporting that as a bare close leaves a stuck cell with nothing to debug.
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onEvent(
      KernelSocketEvent.Ended(
        reason = "${t::class.simpleName}: ${t.message} (http ${response?.code})",
        failed = true,
      ),
    )

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
      onEvent(KernelSocketEvent.Ended(reason = "closed: $code $reason", failed = false))
  }

  private fun request(kernelId: String, sessionId: String): Request = Request.Builder()
    .url("${connection.baseUrl}api/kernels/$kernelId/channels?session_id=$sessionId")
    .header("Authorization", "token ${connection.token}")
    .build()

  private class OkHttpKernelSocket(private val socket: WebSocket) : KernelSocket {
    override fun send(frame: String): Boolean = socket.send(frame)

    // close(), not cancel(): the server counts this connection against the kernel until the
    // handshake completes, and an abandoned socket keeps that count up.
    override fun close() {
      socket.close(NORMAL_CLOSURE, null)
    }
  }

  private companion object {
    const val NORMAL_CLOSURE = 1000
  }
}
