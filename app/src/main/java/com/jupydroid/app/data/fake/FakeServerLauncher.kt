package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import kotlinx.coroutines.CompletableDeferred

/** Stub [ServerLauncher]; `JupyterServerLauncher` is the real ProcessBuilder/rootfs one. */
class FakeServerLauncher : ServerLauncher {
  /** Completes when the stub's server "exits", which only [stop] does. Null before any start. */
  private var exited: CompletableDeferred<Unit>? = null

  override suspend fun start(space: Space): ServerConnection {
    exited?.complete(Unit)
    exited = CompletableDeferred()
    return ServerConnection(baseUrl = "http://127.0.0.1:8888", port = 8888)
  }

  override suspend fun stop() {
    exited?.complete(Unit)
  }

  override fun isRunning(): Boolean = exited?.isCompleted == false

  override suspend fun awaitExit() {
    exited?.await()
  }
}
