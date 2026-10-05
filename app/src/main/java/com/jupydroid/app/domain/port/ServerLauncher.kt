package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.Space

/**
 * A running server. [token] authenticates API calls; jupyter_server rejects POSTs
 * from unauthenticated clients with an XSRF error, and token auth is the path it
 * offers non-browser clients.
 */
data class ServerConnection(val baseUrl: String, val port: Int, val token: String = "")

/** Owns the jupyter_server process for a [Space]. `JupyterServerLauncher` is the real one. */
interface ServerLauncher {
  suspend fun start(space: Space): ServerConnection

  suspend fun stop()

  fun isRunning(): Boolean

  /**
   * Suspends until the process [start] last launched has exited, and returns at once when none was
   * ever launched. A server that stops itself when idle is seen here, which no [isRunning] call
   * made on a schedule could see without polling.
   */
  suspend fun awaitExit()
}
