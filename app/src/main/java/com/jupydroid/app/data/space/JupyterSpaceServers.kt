package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.SpaceServers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * One [SpaceServer] per Space, each with a launcher of its own, built the first time the Space is
 * asked for and kept for the life of the process.
 *
 * A launcher each, because `JupyterServerLauncher` holds one process and its `start` begins with
 * `stop()`: shared, running a second Space would stop the first. `TwoSpacesAtOnceDeviceTest`
 * measures two side by side on distinct ports, each behind its own token.
 *
 * [serverFor] is handed the id and what to call when that Space starts or its process exits, and
 * that call is the only writer of [running].
 *
 * ```
 * val servers = JupyterSpaceServers { id, reportRunning -> SpaceServer(spaceWithId(id), …, reportRunning) }
 * servers.server("base")   // the entry base's kernel gateway, exporter and terminals hold
 * ```
 */
class JupyterSpaceServers(private val serverFor: (spaceId: String, reportRunning: (Boolean) -> Unit) -> SpaceServer) :
  SpaceServers {
  private val runningIds = MutableStateFlow(emptySet<String>())
  private val servers = mutableMapOf<String, SpaceServer>()

  override val running: StateFlow<Set<String>> = runningIds.asStateFlow()

  /** [spaceId]'s entry - the one every client of that Space must share. */
  fun server(spaceId: String): SpaceServer = synchronized(servers) {
    servers.getOrPut(spaceId) { serverFor(spaceId) { up -> report(spaceId, up) } }
  }

  override suspend fun run(spaceId: String) {
    server(spaceId).connection()
  }

  override suspend fun stop(spaceId: String) {
    synchronized(servers) { servers[spaceId] }?.stop()
  }

  override suspend fun stopAll() = synchronized(servers) { servers.values.toList() }.forEach { it.stop() }

  private fun report(spaceId: String, up: Boolean) = runningIds.update { if (up) it + spaceId else it - spaceId }
}
