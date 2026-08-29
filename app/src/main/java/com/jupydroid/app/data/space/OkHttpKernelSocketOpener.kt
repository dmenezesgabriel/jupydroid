package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import okhttp3.OkHttpClient

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
 */
class OkHttpKernelSocketOpener(connection: ServerConnection, client: OkHttpClient = OkHttpClient()) :
  KernelSocketOpener {
  private val sockets = OkHttpJupyterSockets(connection, client)

  override fun open(kernelId: String, sessionId: String, onEvent: (JupyterSocketEvent) -> Unit): JupyterSocket =
    sockets.open("api/kernels/$kernelId/channels?session_id=$sessionId", onEvent)
}
