package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.Space

/**
 * A running server. [token] authenticates API calls; jupyter_server rejects POSTs
 * from unauthenticated clients with an XSRF error, and token auth is the path it
 * offers non-browser clients.
 */
data class ServerConnection(val baseUrl: String, val port: Int, val token: String = "")

/** Owns the jupyter_server process for a [Space]. Rootfs launcher lands in Step 8. */
interface ServerLauncher {
  suspend fun start(space: Space): ServerConnection

  suspend fun stop()

  fun isRunning(): Boolean
}
