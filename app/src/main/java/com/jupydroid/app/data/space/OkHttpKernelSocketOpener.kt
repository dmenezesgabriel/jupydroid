package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import okhttp3.OkHttpClient
import kotlin.time.Duration.Companion.seconds
import kotlin.time.toJavaDuration

/**
 * Opens kernel sockets over [OkHttpJupyterSockets], which is where the OkHttp actually lives.
 *
 * What is left here is the URL, because that is the whole of what makes a socket a *kernel's*:
 * `api/kernels/<id>/channels`, with the client's session id on the query string so the server can
 * attribute traffic to it.
 *
 * ```
 * OkHttpKernelSocketOpener(connection).open(kernelId, sessionId) { event -> … }
 * ```
 *
 * **The upgrade can be slower than OkHttp's default 10 s read timeout.** jupyter_server answers it
 * only once the kernel has replied to `kernel_info`, and waits up to `kernel_info_timeout` (60 s)
 * for that (jupyter_server 2.20.0 `services/kernels/connection/base.py`). A cold kernel beside a
 * second running Space misses 10 s, so the default client waits [KERNEL_UPGRADE_TIMEOUT]: as long
 * as the server does. Only the handshake is bounded by it, because OkHttp clears the socket's read
 * timeout once the upgrade completes (okhttp 5.4.0 `RealConnection.useAsSocket`).
 */
class OkHttpKernelSocketOpener(
  connection: ServerConnection,
  client: OkHttpClient = OkHttpClient.Builder().readTimeout(KERNEL_UPGRADE_TIMEOUT.toJavaDuration()).build(),
) : KernelSocketOpener {
  private val sockets = OkHttpJupyterSockets(connection, client)

  override fun open(kernelId: String, sessionId: String, onEvent: (JupyterSocketEvent) -> Unit): JupyterSocket =
    sockets.open("api/kernels/$kernelId/channels?session_id=$sessionId", onEvent)
}

/** jupyter_server's `kernel_info_timeout`: the longest it holds a kernel socket's upgrade. */
private val KERNEL_UPGRADE_TIMEOUT = 60.seconds
