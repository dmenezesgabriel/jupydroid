package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference

/**
 * A live server and which start it was, so a client can tell a replacement from the same one.
 *
 * The count rather than the [connection] is what identifies a server, and that is a measurement
 * rather than a preference: `JupyterServerLauncher` mints its token once per *launcher* and the
 * OS may hand back the same ephemeral port, so two [ServerConnection]s can compare equal across a
 * restart.
 */
data class StartedServer(val connection: ServerConnection, val generation: Int)

/**
 * Brings one Space's server up on first use, then gets out of the way - and does it **once**, for
 * every client of the Space rather than for one of them.
 *
 * `jupyter_server` takes seconds to start and cannot be waited on while an app is being
 * constructed, so this exists to defer that: the Space is installed and the server started the
 * first time anyone actually wants it.
 *
 * **One object owns the start, and that is load-bearing rather than tidy.**
 * `JupyterServerLauncher.start` begins with `stop()`, so a second lazy starter beside the kernel
 * gateway would kill the kernel's server the first time anything else - an export, a terminal, a
 * language server - asked for one.
 *
 * Whether the Space runs goes out through [reportRunning]: true once a server has started, false
 * once its process has exited, which a watcher on [watchScope] waits for. So a server that stops
 * itself when idle is reported exactly like one that was told to stop.
 *
 * Each fresh start also pre-starts one spare kernel through [prestartKernel], on [watchScope] so
 * the start never waits for it, and the first session that suits it claims it with
 * [claimSpareKernel] (ADR-0080). The kernel's own start then runs while the user walks from Run
 * to the first cell, rather than after the cell asks for it.
 *
 * ```
 * val space = SpaceServer(
 *   space = BASE_SPACE,
 *   installSpace = { baseSpaceInstaller(context).installIfNeeded() },
 *   launcher = spaceServerLauncher(context),
 *   watchScope = applicationScope,
 *   reportRunning = { up -> println("base running: $up") },
 * )
 * PerRunningServer(space) { RealKernelGateway(OkHttpKernelSocketOpener(it), OkHttpKernelLifecycle(it)) }
 * ```
 */
class SpaceServer(
  private val space: Space,
  private val installSpace: suspend () -> Unit,
  private val launcher: ServerLauncher,
  private val watchScope: CoroutineScope,
  private val reportRunning: (Boolean) -> Unit,
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
  /** Starts the Space's spare kernel on a server that has just started; null means no spare. */
  private val prestartKernel: (suspend (ServerConnection) -> BareKernel)? = null,
) {
  private var started: StartedServer? = null

  // The running server's spare, null once claimed. Null inside, when its start failed: a spare is
  // an optimisation and never a reason for a start or a session to fail (ADR-0080).
  private val spare = AtomicReference<Deferred<BareKernel?>?>(null)

  // Two cells run at once would otherwise each find no server and each start one; the second
  // would bind a different port and the first would be orphaned.
  private val startLock = Mutex()

  /**
   * The running server, or null when there is none - and it never starts one.
   *
   * The peek runs under [startLock], so a call queued behind a [stop] in flight answers for
   * the settled state rather than for a process still dying
   * (`perRunningServer_liveOrNull_askedDuringAStopAnswersNullAndStartsNothing`).
   */
  suspend fun liveOrNull(): StartedServer? = startLock.withLock {
    started?.takeIf { launcher.isRunning() }
  }

  /**
   * The running server, starting one if there is none.
   *
   * The cached connection is only valid while the process it names is alive. Without that
   * check a stopped server leaves every later call talking to a dead port.
   */
  suspend fun connection(): StartedServer = startLock.withLock {
    val cached = started
    if (cached != null && launcher.isRunning()) return@withLock cached
    StartedServer(startSpace(), (cached?.generation ?: 0) + 1).also {
      started = it
      reportRunning(true)
      watchForExit()
      prestartSpare(it.connection)
    }
  }

  /**
   * The running server's spare kernel, handed out once, when [accepts] it.
   *
   * A spare still starting is waited for: a second kernel would be one nobody uses, and the
   * kernel's socket waits for its first answer anyway. A spare [accepts] refuses stays for a
   * session it suits.
   *
   * ```
   * server.claimSpareKernel { it.kernelName == "python3" }  // -> BareKernel("kernel-abc", "python3"), then null
   * ```
   */
  suspend fun claimSpareKernel(accepts: (BareKernel) -> Boolean = { true }): BareKernel? {
    val pending = spare.get() ?: return null
    return pending.await()?.takeIf { accepts(it) && spare.compareAndSet(pending, null) }
  }

  /** Stops the server, under the start's own lock so a stop never lands halfway through a start. */
  suspend fun stop() = startLock.withLock { launcher.stop() }

  /**
   * Waits for the process just started to exit. The check after the wait is for a launcher whose
   * stop outlived its timeout: the process it waited on is gone only if the launcher says so.
   */
  private fun watchForExit() {
    watchScope.launch {
      launcher.awaitExit()
      if (!launcher.isRunning()) reportRunning(false)
    }
  }

  /** A replaced server's spare is cancelled with it: its kernel went down with that server. */
  private fun prestartSpare(connection: ServerConnection) {
    val prestart = prestartKernel ?: return
    val next = watchScope.async {
      runCatching { prestart(connection) }
        .onSuccess { log.reached(KernelLog.SPARE_STARTED, mapOf("kernelId" to it.kernelId, "name" to it.kernelName)) }
        .onFailure { log.failed(KernelLog.SPARE_FAILED, it) }
        .getOrNull()
    }
    spare.getAndSet(next)?.cancel()
  }

  /**
   * Each stage is logged as it is reached, so a runtime that fails on a device says where.
   * The alternative is what the first real-kernel run looked like: a cell that never produced
   * output, nothing in logcat, and the server log dug out through `run-as`.
   */
  private suspend fun startSpace(): ServerConnection = runCatching {
    log.reached("installing Space ${space.id}")
    installSpace()
    log.reached("starting jupyter_server for Space ${space.id}")
    launcher.start(space).also { log.reached("jupyter_server listening on ${it.baseUrl}") }
  }.onFailure { log.failed("Space ${space.id} failed to come up", it) }.getOrThrow()
}
