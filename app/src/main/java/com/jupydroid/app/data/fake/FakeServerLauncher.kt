package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.Environment
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher

/** Stub [ServerLauncher]; the real ProcessBuilder/rootfs launcher lands in Step 8. */
class FakeServerLauncher : ServerLauncher {
  private var running = false

  override suspend fun start(environment: Environment): ServerConnection {
    running = true
    return ServerConnection(baseUrl = "http://127.0.0.1:8888", port = 8888)
  }

  override suspend fun stop() {
    running = false
  }

  override fun isRunning(): Boolean = running
}
