package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
) {
  private var started: StartedServer? = null

  // Two cells run at once would otherwise each find no server and each start one; the second
  // would bind a different port and the first would be orphaned.
  private val startLock = Mutex()

  /** Whether the server process is alive - asked of the launcher, never of a flag set here. */
  fun isRunning(): Boolean = launcher.isRunning()

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
    }
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
