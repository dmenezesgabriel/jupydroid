package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.StaleKernelSessionException
import kotlinx.coroutines.flow.Flow

/**
 * The kernel gateway for the base Space's server, and the bookkeeping that a server which can be
 * replaced under it forces.
 *
 * Starting the server is [BaseSpaceServer]'s job and building the gateway per server is
 * [PerRunningServer]'s; what is left here - and it is the whole reason this class still exists -
 * is that a *session* outlives neither.
 *
 * ```
 * BaseSpaceKernelGateway(
 *   server = baseSpaceServer(context, baseSpaceServerLauncher(context))!!,
 *   gatewayFor = { RealKernelGateway(OkHttpKernelSocketOpener(it), OkHttpKernelLifecycle(it)) },
 * )
 * ```
 */
class BaseSpaceKernelGateway(
  server: BaseSpaceServer,
  gatewayFor: (ServerConnection) -> KernelGateway,
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
) : KernelGateway {
  /**
   * The sessions the *currently running* server issued, cleared whenever a new one is started.
   *
   * P1c stops the server while the app lives on, so a session the caller still holds can outlive
   * the process that knew about it. This gateway is the only party that knows a server was
   * replaced, so it is the one that can say so - the alternative is what the tablet measured
   * before this existed: a websocket upgrade answered `404 Not Found` for a kernel id the fresh
   * server never issued (`evidence/p1c-app-death-backstop/stale-session-404.txt`).
   */
  private val issuedSessions = mutableSetOf<String>()

  // Clearing inside `build` rather than beside it: `PerRunningServer` calls it exactly once per
  // server, so "a new gateway was built" and "nothing the old server issued carries over" cannot
  // drift apart.
  private val live = PerRunningServer(server) { connection ->
    issuedSessions.clear()
    gatewayFor(connection)
  }

  /**
   * Starts a kernel and binds a session to it. The session id is ours and identifies this
   * client to the server; the kernel id is the server's.
   */
  override suspend fun connect(space: Space, documentPath: String): KernelSession =
    live.get().connect(space, documentPath).also {
      issuedSessions += it.id
      log.reached("kernel ${it.kernelId} ready for space ${space.id}")
    }

  override suspend fun execute(
    session: KernelSession,
    cellId: String,
    code: String,
  ): Flow<KernelEvent> = known(session).execute(session, cellId, code)

  override suspend fun interrupt(session: KernelSession) = known(session).interrupt(session)

  override suspend fun restart(session: KernelSession) = known(session).restart(session)

  override suspend fun complete(
    session: KernelSession,
    code: String,
    cursorOffset: Int,
  ): CompletionResult = known(session).complete(session, code, cursorOffset)

  /**
   * Closing a socket the server has already forgotten is not a failure - the session is gone
   * either way, which is what the caller asked for.
   */
  override suspend fun disconnect(session: KernelSession) = endingIfStillLive(session) { it.disconnect(session) }

  /** Ending the session outright, which takes the kernel with it: `notebook:shutdown-kernel`. */
  override suspend fun shutdown(session: KernelSession) = endingIfStillLive(session) { it.shutdown(session) }

  /**
   * Runs [end] against the live gateway, and does nothing at all when this session is not one this
   * gateway issued or the server that issued it has since stopped.
   *
   * **It must not *start* a server to end a session.** Leaving a notebook after the backstop
   * stopped one would otherwise boot a whole runtime - seven seconds and ~130 MB - for the sake of
   * closing a websocket that no longer exists, or deleting a session nothing is holding.
   */
  private suspend fun endingIfStillLive(session: KernelSession, end: suspend (KernelGateway) -> Unit) {
    if (!issuedSessions.remove(session.id)) return
    live.liveOrNull()?.let { end(it) }
  }

  /** The running gateway, for a session it actually knows; [StaleKernelSessionException] if not. */
  private suspend fun known(session: KernelSession): KernelGateway {
    val gateway = live.get()
    if (session.id !in issuedSessions) throw StaleKernelSessionException(session.id)
    return gateway
  }
}
