package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.Space

data class ServerConnection(val baseUrl: String, val port: Int)

/** Owns the jupyter_server process for a [Space]. Rootfs launcher lands in Step 8. */
interface ServerLauncher {
  suspend fun start(space: Space): ServerConnection

  suspend fun stop()

  fun isRunning(): Boolean
}
