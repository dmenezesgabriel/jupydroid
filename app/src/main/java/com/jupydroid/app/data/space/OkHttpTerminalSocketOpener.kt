package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import okhttp3.OkHttpClient

/**
 * Opens terminal sockets over [OkHttpJupyterSockets], which is where the OkHttp actually lives.
 *
 * What is left here is the URL, and it is the one thing about a terminal's socket that is not
 * shaped like a kernel's: the route is registered **outside** `api/` -
 * `r"/terminals/websocket/(\w+)"` in `jupyter_server_terminals/app.py:initialize_handlers`, while
 * that same extension's REST handlers are `r"/api/terminals"`.
 *
 * ```
 * OkHttpTerminalSocketOpener(connection).open("1") { event -> … }
 * ```
 */
class OkHttpTerminalSocketOpener(connection: ServerConnection, client: OkHttpClient = OkHttpClient()) :
  TerminalSocketOpener {
  private val sockets = OkHttpJupyterSockets(connection, client)

  override fun open(name: String, onEvent: (JupyterSocketEvent) -> Unit): JupyterSocket =
    sockets.open("terminals/websocket/$name", onEvent)
}
