package com.jupydroid.app.domain.port

import kotlinx.coroutines.flow.StateFlow

/**
 * Every Space's `jupyter_server`, several at once: which are up, and Run and Stop for each.
 * SageMaker Studio's shape, where a space is run and stopped by its own control (ADR-0074).
 *
 * [running] follows the processes rather than the calls, so a server that stops itself when idle
 * leaves it exactly as a [stop] does. `JupyterSpaceServers` is the real one.
 *
 * ```
 * spaceServers.run("space-1")
 * spaceServers.running.value   // {"space-1"}
 * ```
 */
interface SpaceServers {
  /** The ids of the Spaces whose server is alive. */
  val running: StateFlow<Set<String>>

  /** Starts [spaceId]'s server if it is not already running, and returns once it serves. */
  suspend fun run(spaceId: String)

  /** Stops [spaceId]'s server, keeping its files; a Space that is not running is left alone. */
  suspend fun stop(spaceId: String)

  suspend fun stopAll()
}
