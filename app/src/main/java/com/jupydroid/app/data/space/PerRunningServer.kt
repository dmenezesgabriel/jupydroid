package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A client built from a [ServerConnection], rebuilt whenever the server behind it is replaced.
 *
 * Every client of a [BaseSpaceServer] needs the same two things - one instance while the server
 * lives, a fresh one when a new server takes over - and each of them needs it for its own reason:
 * a kernel gateway holds sockets to a process, an exporter holds a base URL and a token. Written
 * once here so the second client does not copy the rule, and so [build] is the single place a
 * client can learn that its server was replaced.
 *
 * ```
 * val kernels = PerRunningServer(space) { RealKernelGateway(OkHttpKernelSocketOpener(it), …) }
 * kernels.get().connect(space, "Analysis.ipynb")
 * ```
 */
class PerRunningServer<T : Any>(
  private val server: BaseSpaceServer,
  /** Called once per server. Anything a client must forget when one is replaced belongs here. */
  private val build: (ServerConnection) -> T,
) {
  private var current: T? = null
  private var generation = 0
  private val buildLock = Mutex()

  /** The client for the running server, starting one if there is none. */
  suspend fun get(): T {
    val live = server.connection()
    return buildLock.withLock {
      val cached = current
      if (cached != null && live.generation == generation) return@withLock cached
      generation = live.generation
      build(live.connection).also { current = it }
    }
  }

  /**
   * The client for the server that is *already* running, or null when none is.
   *
   * **It must not start one.** Ending a session after the backstop stopped a server would
   * otherwise boot a whole runtime - seven seconds and ~130 MB - to close a websocket that no
   * longer exists (`evidence/p1c-app-death-backstop/`).
   */
  suspend fun liveOrNull(): T? = if (server.isRunning()) get() else null
}
