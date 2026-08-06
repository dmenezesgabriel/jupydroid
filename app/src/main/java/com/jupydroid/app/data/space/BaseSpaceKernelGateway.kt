package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
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

  // Two cells run at once would otherwise each find no server and each start one; the second
  // would bind a different port and the first would be orphaned.
  private val startLock = Mutex()

  override suspend fun connect(space: Space): KernelSession =
    running().connect(space).also { log.reached("kernel ${it.kernelId} ready for space ${space.id}") }

  override suspend fun execute(
    session: KernelSession,
    cellId: String,
    code: String,
  ): Flow<KernelEvent> = running().execute(session, cellId, code)

  override suspend fun interrupt(session: KernelSession) = running().interrupt(session)

  override suspend fun complete(
    session: KernelSession,
    code: String,
    cursorOffset: Int,
  ): CompletionResult = running().complete(session, code, cursorOffset)

  override suspend fun disconnect(session: KernelSession) = running().disconnect(session)

  private suspend fun running(): KernelGateway = startLock.withLock {
    started ?: startSpace().also { started = it }
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
