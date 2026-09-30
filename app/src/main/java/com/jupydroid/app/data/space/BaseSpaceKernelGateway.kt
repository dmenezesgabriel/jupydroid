package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelChoice
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
   * notebook holding the session. Defaulted so a gateway driven alone tracks nothing it
   * did not mint either.
   */
  private val registry: KernelSessionRegistry = KernelSessionRegistry(),
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
) : KernelGateway {
  private val issued = IssuedSessions(sharedSockets, gatewayFor, registry)

  /**
   * Starts a kernel and binds a session to it. The session id is ours and identifies this
   * client to the server; the kernel id is the server's.
   */
  override suspend fun connect(space: Space, documentPath: String, kernel: KernelChoice?): KernelSession =
    issued.currentGateway().connect(space, documentPath, kernel).also {
      issued.minted(it.id)
      registry.register(it, documentPath)
      log.reached("kernel ${it.kernelId} ready for space ${space.id}")
    }

  /**
   * The session keeps both its ids and gets a different kernel - `PATCH api/sessions`
   * (`KernelGateway.changeKernel`). The registry is told so the tracked row shows the new kernel
   * and [KernelSessionRegistry.rebound] reaches every other holder of the session - a console
   * sharing it would otherwise keep running against a kernel the server has shut down.
   */
  override suspend fun changeKernel(session: KernelSession, kernel: KernelChoice): KernelSession =
    issued.gatewayKnowing(session).changeKernel(session, kernel).also { registry.rebind(it) }

  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> =
    issued.gatewayKnowing(session).execute(session, cellId, code)

  override suspend fun interrupt(session: KernelSession) = issued.gatewayKnowing(session).interrupt(session)

  override suspend fun restart(session: KernelSession) = issued.gatewayKnowing(session).restart(session)

  override suspend fun complete(session: KernelSession, code: String, cursorOffset: Int): CompletionResult =
    issued.gatewayKnowing(session).complete(session, code, cursorOffset)

  override suspend fun executionHistory(session: KernelSession, entries: Int): List<String> =
    issued.gatewayKnowing(session).executionHistory(session, entries)

  /**
   * Closing a socket the server has already forgotten is not a failure - the session is gone
   * either way, which is what the caller asked for.
   */
  override suspend fun disconnect(session: KernelSession) = issued.endingIfStillLive(session) { it.disconnect(session) }

  /** Ending the session outright, which takes the kernel with it: `notebook:shutdown-kernel`. */
  override suspend fun shutdown(session: KernelSession) {
    issued.endingIfStillLive(session) { it.shutdown(session) }
    // Whether or not the server was still there to ask, this process stopped holding the
    // session - a stopped server's sessions ended with it. `ended` is what lands every other
    // holder (a console sharing it, a panel listing it) on the truth.
    registry.end(session.serverSessionId)
  }
}

/**
 * Which sessions the *currently running* server issued, cleared whenever a new one is started.
 *
 * The lifecycle backstop stops the server while the app lives on, so a session the caller holds
 * can outlive
 * the process that knew about it. The gateway is the only party that knows a server was
 * replaced, so it is the one that can say so - the alternative is what the tablet measured
 * before this existed: a websocket upgrade answered `404 Not Found` for a kernel id the fresh
 * server never issued.
 */
private class IssuedSessions(
  private val sharedSockets: PerRunningServer<KernelSockets>,
  private val gatewayFor: (KernelSockets) -> KernelGateway,
  private val registry: KernelSessionRegistry,
) {
  private val ids = mutableSetOf<String>()

  // The last KernelSockets seen, not a count of servers started: `sockets` is shared with
  // `BaseSpaceKernelDebugger`, either of which can be the one that rebuilds it on a new
  // generation - so this reacts to a change it observes rather than one it necessarily caused.
  private var trackedSockets: KernelSockets? = null

  fun minted(id: String) {
    ids += id
  }

  /** The running gateway, for a session it actually knows; [StaleKernelSessionException] if not. */
  suspend fun gatewayKnowing(session: KernelSession): KernelGateway {
    val gateway = currentGateway()
    if (session.id !in ids) throw StaleKernelSessionException(session.id)
    return gateway
  }

  /**
   * Runs [end] against the live gateway, and does nothing at all when this session is not one the
   * current server issued or that server has since stopped.
   *
   * **It must not *start* a server to end a session.** Leaving a notebook after the backstop
   * stopped one would otherwise boot a whole runtime - seven seconds and ~130 MB - for the sake of
   * closing a websocket that no longer exists, or deleting a session nothing is holding.
   */
  suspend fun endingIfStillLive(session: KernelSession, end: suspend (KernelGateway) -> Unit) {
    if (!ids.remove(session.id)) return
    sharedSockets.liveOrNull()?.let { end(gatewayFor(it)) }
  }

  /** [ids] is cleared the moment a different [KernelSockets] shows up, from either side. */
  suspend fun currentGateway(): KernelGateway {
    val sockets = sharedSockets.get()
    if (sockets !== trackedSockets) {
      ids.clear()
      // The replaced server's sessions went with it, and whoever still holds one lands on
      // No Kernel now rather than at its next use - `runningChanged` over a generation gap.
      registry.endAll()
      trackedSockets = sockets
    }
    return gatewayFor(sockets)
  }
}
