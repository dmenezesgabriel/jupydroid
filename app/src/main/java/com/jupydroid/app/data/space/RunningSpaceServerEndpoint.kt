package com.jupydroid.app.data.space

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extension.api.SpaceServerEndpoint

/**
 * The entered Space's `jupyter_server` as an extension reaches it (ADR-0104): [SpaceServer]'s
 * connection, started on first use, in the address type `:extension-api` owns - an extension cannot
 * see `:app`'s `ServerConnection`.
 *
 * Asked per request rather than held, so a server that stopped and started again is asked at its
 * new port with its new token, which is the rule `PerRunningServer` keeps for `:app`'s own clients.
 *
 * ```
 * SidePanelHost(browsedDirectory, server = RunningSpaceServerEndpoint(spaceServer))
 * ```
 */
class RunningSpaceServerEndpoint(private val server: SpaceServer) : SpaceServerEndpoint {
  override suspend fun address(): ServerAddress {
    val connection = server.connection().connection
    return ServerAddress(baseUrl = connection.baseUrl, token = connection.token)
  }
}
