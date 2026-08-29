package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * What happens on a WebSocket to `jupyter_server`, in the only three kinds that matter to a caller.
 *
 * There is no separate "closed" and "failed": both end the socket, and what a stuck caller
 * needs is the reason, not the category. [Ended.failed] keeps the distinction for callers
 * that report differently, without letting anyone emit a bare, undebuggable "closed".
 */
sealed interface JupyterSocketEvent {
  data object Opened : JupyterSocketEvent

  data class Received(val frame: String) : JupyterSocketEvent

  /** Terminal. [reason] always says why. */
  data class Ended(val reason: String, val failed: Boolean) : JupyterSocketEvent
}

/**
 * A live WebSocket to `jupyter_server`, for as long as the caller wants it.
 *
 * Owned by this project rather than exposing OkHttp, so what sits above it can be tested against a
 * fake and the transport can change without touching it.
 *
 * **Transport, not protocol**, which is why this is no longer named for the kernel. What travels
 * over it is one text frame at a time over one long-lived connection; that a kernel's frames are
 * signed multi-channel Jupyter messages is [RealKernelGateway]'s business and not this file's.
 */
interface JupyterSocket {
  /**
   * Queues a frame. May be called before [JupyterSocketEvent.Opened] arrives - frames wait
   * for the connection rather than being lost, which is what lets a caller send its request
   * straight after opening instead of racing the listener for the socket reference.
   *
   * False when the frame could not be queued - the socket is already ending.
   */
  fun send(frame: String): Boolean

  fun close()
}

/**
 * Opens sockets to a kernel's `channels` endpoint.
 *
 * ```
 * val socket = opener.open(kernelId, sessionId) { event -> … }
 * ```
 */
fun interface KernelSocketOpener {
  fun open(kernelId: String, sessionId: String, onEvent: (JupyterSocketEvent) -> Unit): JupyterSocket
}

/**
 * The OkHttp half of a socket: the listener, the failure reporting and the close code.
 *
 * Separated from the URL because that is the whole of what distinguishes one endpoint from
 * another, and this is where the behaviour is. A rule like "a refused upgrade must carry its HTTP
 * status" has one place to be got right rather than one per endpoint.
 *
 * No subprotocol is requested, so jupyter_server uses its original JSON protocol - one JSON text
 * frame per message, which is what [JupyterFrame] reads.
 */
internal class OkHttpJupyterSockets(
  private val connection: ServerConnection,
  private val client: OkHttpClient = OkHttpClient(),
) {
  fun open(path: String, onEvent: (JupyterSocketEvent) -> Unit): JupyterSocket =
    OkHttpSocket(client.newWebSocket(request(path), listener(onEvent)))

  private fun listener(onEvent: (JupyterSocketEvent) -> Unit) = object : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) = onEvent(JupyterSocketEvent.Opened)

    override fun onMessage(webSocket: WebSocket, text: String) = onEvent(JupyterSocketEvent.Received(text))

    // The HTTP status matters: a refused upgrade is a 404 for a kernel that is gone or a terminal
    // the server never named, and reporting that as a bare close leaves the caller nothing to
    // debug. `TermSocket.get` raises exactly that 404 when the name is not in `term_manager`.
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = onEvent(
      JupyterSocketEvent.Ended(
        reason = "${t::class.simpleName}: ${t.message} (http ${response?.code})",
        failed = true,
      ),
    )

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) =
      onEvent(JupyterSocketEvent.Ended(reason = "closed: $code $reason", failed = false))
  }

  private fun request(path: String): Request = Request.Builder()
    .url(connection.baseUrl + path)
    // Token in a header rather than the query string the reference uses: a browser `WebSocket`
    // cannot set headers and OkHttp can, and `JupyterHandler` reads either.
    .header("Authorization", "token ${connection.token}")
    .build()

  private class OkHttpSocket(private val socket: WebSocket) : JupyterSocket {
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
