package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.StaleKernelSessionException
import kotlinx.coroutines.flow.Flow

/**
 * The kernel gateway for the base Space's server, and the bookkeeping that a server which can be
 * replaced under it forces.
 *
 * Starting the server is [BaseSpaceServer]'s job; [sharedSockets] is the one
 * `PerRunningServer<KernelSockets>` this shares with `BaseSpaceKernelDebugger` - built from that
 * server once, at the composition root - so both read the identical per-session socket rather
 * than each opening their own. What is left here - and it is the whole reason this class still
 * exists - is that a *session* outlives neither.
 *
 * ```
 * val sockets = baseSpaceKernelSockets(server)
 * BaseSpaceKernelGateway(sharedSockets = sockets, gatewayFor = { RealKernelGateway(it) })
 * ```
 */
class BaseSpaceKernelGateway(
  private val sharedSockets: PerRunningServer<KernelSockets>,
  private val gatewayFor: (KernelSockets) -> KernelGateway,
  /**
   * Where minted sessions are announced and ended sessions announced *from* - shared with
   * `BaseSpaceKernelSessions`, which is what lets the Running panel's per-row shutdown reach a
   * notebook holding the session (P8f). Defaulted so a gateway driven alone tracks nothing it
   * did not mint either.
   */
  private val registry: KernelSessionRegistry = KernelSessionRegistry(),
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

  // The last KernelSockets this adapter itself saw, not a count of servers it started: `sockets`
  // is shared with `BaseSpaceKernelDebugger`, either of which can be the one that rebuilds it on a
  // new generation - so this reacts to a change it observes rather than one it necessarily caused.
  private var trackedSockets: KernelSockets? = null

  /**
   * Starts a kernel and binds a session to it. The session id is ours and identifies this
   * client to the server; the kernel id is the server's.
   */
  override suspend fun connect(space: Space, documentPath: String): KernelSession =
    currentGateway().connect(space, documentPath).also {
      issuedSessions += it.id
      registry.register(it, documentPath)
      log.reached("kernel ${it.kernelId} ready for space ${space.id}")
    }

  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> =
    known(session).execute(session, cellId, code)

  override suspend fun interrupt(session: KernelSession) = known(session).interrupt(session)

  override suspend fun restart(session: KernelSession) = known(session).restart(session)

  override suspend fun complete(session: KernelSession, code: String, cursorOffset: Int): CompletionResult =
    known(session).complete(session, code, cursorOffset)

  override suspend fun executionHistory(session: KernelSession, entries: Int): List<String> =
    known(session).executionHistory(session, entries)

  /**
   * Closing a socket the server has already forgotten is not a failure - the session is gone
   * either way, which is what the caller asked for.
   */
  override suspend fun disconnect(session: KernelSession) = endingIfStillLive(session) { it.disconnect(session) }

  /** Ending the session outright, which takes the kernel with it: `notebook:shutdown-kernel`. */
  override suspend fun shutdown(session: KernelSession) {
    endingIfStillLive(session) { it.shutdown(session) }
    // Whether or not the server was still there to ask, this process stopped holding the
    // session - a stopped server's sessions ended with it. `ended` is what lands every other
    // holder (a console sharing it, a panel listing it) on the truth.
    registry.end(session.serverSessionId)
  }

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
    sharedSockets.liveOrNull()?.let { end(gatewayFor(it)) }
  }

  /** The running gateway, for a session it actually knows; [StaleKernelSessionException] if not. */
  private suspend fun known(session: KernelSession): KernelGateway {
    val gateway = currentGateway()
    if (session.id !in issuedSessions) throw StaleKernelSessionException(session.id)
    return gateway
  }

  /** [issuedSessions] is cleared the moment a different [KernelSockets] shows up, from either side. */
  private suspend fun currentGateway(): KernelGateway {
    val sockets = sharedSockets.get()
    if (sockets !== trackedSockets) {
      issuedSessions.clear()
      // The replaced server's sessions went with it, and whoever still holds one lands on
      // No Kernel now rather than at its next use - `runningChanged` over a generation gap.
      registry.endAll()
      trackedSockets = sockets
    }
    return gatewayFor(sockets)
  }
}
