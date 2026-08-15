package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import com.jupydroid.app.domain.port.StaleKernelSessionException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Brings a Space up on first use, then gets out of the way.
 *
 * `jupyter_server` takes seconds to start and cannot be waited on while an app is being
 * constructed, so this exists to defer that: the Space is installed and the server started
 * the first time anyone actually wants a kernel, and every call after that goes straight to
 * the real gateway.
 *
 * ```
 * BaseSpaceKernelGateway(
 *   installSpace = { baseSpaceInstaller(context).installIfNeeded() },
 *   launcher = baseSpaceServerLauncher(context),
 *   gatewayFor = { RealKernelGateway(OkHttpKernelSocketOpener(it), OkHttpKernelLifecycle(it)) },
 * )
 * ```
 */
class BaseSpaceKernelGateway(
  private val installSpace: suspend () -> Unit,
  private val launcher: ServerLauncher,
  private val gatewayFor: (ServerConnection) -> KernelGateway,
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
) : KernelGateway {
  private var started: KernelGateway? = null

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

  // Two cells run at once would otherwise each find no server and each start one; the second
  // would bind a different port and the first would be orphaned.
  private val startLock = Mutex()

  override suspend fun connect(space: Space, documentPath: String): KernelSession =
    running().connect(space, documentPath).also {
      issuedSessions += it.id
      log.reached("kernel ${it.kernelId} ready for space ${space.id}")
    }

  override suspend fun execute(
    session: KernelSession,
    cellId: String,
    code: String,
  ): Flow<KernelEvent> = live(session).execute(session, cellId, code)

  override suspend fun interrupt(session: KernelSession) = live(session).interrupt(session)

  override suspend fun restart(session: KernelSession) = live(session).restart(session)

  override suspend fun complete(
    session: KernelSession,
    code: String,
    cursorOffset: Int,
  ): CompletionResult = live(session).complete(session, code, cursorOffset)

  /**
   * Closing a socket the server has already forgotten is not a failure - the session is gone
   * either way, which is what the caller asked for - and it must not *start* a server to say so.
   * Leaving a notebook after the backstop stopped one would otherwise boot a whole runtime for
   * the sake of closing a websocket that no longer exists.
   */
  override suspend fun disconnect(session: KernelSession) {
    if (!issuedSessions.remove(session.id) || !launcher.isRunning()) return
    running().disconnect(session)
  }

  /** The running gateway, for a session it actually knows; [StaleKernelSessionException] if not. */
  private suspend fun live(session: KernelSession): KernelGateway {
    val gateway = running()
    if (session.id !in issuedSessions) throw StaleKernelSessionException(session.id)
    return gateway
  }

  private suspend fun running(): KernelGateway = startLock.withLock {
    // P1a: the cached gateway is only valid while the server it talks to is alive.
    // Without this check a stopped server leaves every later call talking to a dead port.
    val cached = started
    if (cached != null && launcher.isRunning()) {
      cached
    } else {
      // Whatever the previous server issued died with it, so nothing carries over.
      issuedSessions.clear()
      startSpace().also { started = it }
    }
  }

  /**
   * Each stage is logged as it is reached, so a runtime that fails on a device says where.
   * The alternative is what the first real-kernel run looked like: a cell that never
   * produced output, nothing in logcat, and the server log dug out through `run-as`.
   */
  private suspend fun startSpace(): KernelGateway = runCatching {
    log.reached("installing base Space")
    installSpace()
    log.reached("starting jupyter_server")
    val connection = launcher.start(BASE_SPACE)
    log.reached("jupyter_server listening on ${connection.baseUrl}")
    gatewayFor(connection)
  }.onFailure { log.failed("base Space failed to come up", it) }.getOrThrow()

  private companion object {
    /** Step 17 introduces real Spaces; until then there is exactly one and it is this. */
    val BASE_SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
