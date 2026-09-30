package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.security.SecureRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Runs stock `jupyter_server` out of a Space, on loopback.
 *
 * Bound to 127.0.0.1 with an ephemeral port and no token: the server is reachable
 * only from inside this app's sandbox, so there is no auth surface beyond the app
 * itself. The port is not chosen by us — `--ServerApp.port=0` lets the OS pick and
 * the server reports what it bound in its runtime file, which removes the race in
 * picking a free port and hoping it is still free a moment later.
 *
 * ```
 * JupyterServerLauncher(baseSpaceRuntime(context), File(filesDir, "spaces/base/run")).start(space)
 * ```
 */
class JupyterServerLauncher(
  private val runtime: SpaceRuntime,
  private val runtimeDir: File,
  /** Where the server's Contents API is rooted - the same directory the app reads notebooks from. */
  private val rootDir: File,
  /** What terminado spawns in a pseudoterminal; see [jupyterServerArgs]. */
  private val shell: File,
  private val startTimeout: Duration = START_TIMEOUT,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ServerLauncher {
  private var process: Process? = null

  /**
   * Fresh per launch. Loopback already keeps the server inside this app's sandbox, but
   * a token is what jupyter_server offers non-browser clients: without one it rejects
   * every POST with an XSRF error, and disabling that check would be the weaker answer.
   */
  private val token: String = SecureRandom().let { random ->
    ByteArray(TOKEN_BYTES).also(random::nextBytes).joinToString("") { "%02x".format(it) }
  }

  override suspend fun start(space: Space): ServerConnection {
    stop()
    val started = withContext(ioDispatcher) {
      runtimeDir.mkdirs()
      JupyterRuntimeFile.clear(runtimeDir) // a stale file would advertise a dead port
      jupyterServerProcess(runtime, runtimeDir, rootDir, shell, token).start()
    }
    process = started
    return awaitConnection() ?: failStart(started)
  }

  override suspend fun stop() {
    process?.destroy()
    process = null
  }

  override fun isRunning(): Boolean = process?.isAlive == true

  /**
   * Why the server stopped, or null while it is alive. Values above 128 are
   * `128 + signal`, which is how a seccomp kill (SIGSYS, 159) shows up.
   */
  fun exitCode(): Int? = process?.takeIf { !it.isAlive }?.exitValue()

  private suspend fun awaitConnection(): ServerConnection? = withTimeoutOrNull(startTimeout) {
    while (true) {
      val runtimeFile = withContext(ioDispatcher) { JupyterRuntimeFile.read(runtimeDir) }
      if (runtimeFile != null) return@withTimeoutOrNull runtimeFile.toConnection()
      if (process?.isAlive != true) return@withTimeoutOrNull null
      delay(POLL_INTERVAL)
    }
    @Suppress("UNREACHABLE_CODE")
    null
  }

  private suspend fun failStart(started: Process): Nothing {
    stop()
    val log = withContext(ioDispatcher) { logFile().takeIf { it.isFile }?.readText().orEmpty() }
    throw IllegalStateException(
      "jupyter_server did not report a port within $startTimeout " +
        "(alive=${started.isAlive}); log:\n${log.takeLast(LOG_EXCERPT)}",
    )
  }

  private fun logFile(): File = jupyterServerLogFile(runtimeDir)

  private fun JupyterRuntimeFile.toConnection() =
    ServerConnection(baseUrl = url.ifBlank { "http://127.0.0.1:$port/" }, port = port, token = token)

  private companion object {
    const val TOKEN_BYTES = 32
    val START_TIMEOUT = 60.seconds
    val POLL_INTERVAL = 100.milliseconds
    const val LOG_EXCERPT = 2000
  }
}

/**
 * The server process, configured but not started - the launch as a value, the way
 * [jupyterServerArgs] makes the command line one.
 *
 * Separated for the same reason and asserted the same way: starting a real jupyter_server is a
 * tablet gate, so what a JVM test can own is how the process was set up before anyone ran it.
 */
internal fun jupyterServerProcess(
  runtime: SpaceRuntime,
  runtimeDir: File,
  rootDir: File,
  shell: File,
  token: String,
): ProcessBuilder = runtime.processBuilder(
  jupyterServerArgs(rootDir, token, shell),
  mapOf("JUPYTER_RUNTIME_DIR" to runtimeDir.path), // faulthandler comes from SpaceRuntime
)
  // `root_dir` defaults to `os.getcwd()` (`serverapp.py:1842-1848`), so upstream the two are one
  // directory and everything downstream is written against that. A terminal is the client that
  // proves it: `PtyWithClients` inherits this cwd whenever the POST carries none
  // (`terminado/management.py:225-232`). Without this line a shell opens on Android's read-only
  // `/`. See [JupyterServerWorkingDirTest].
  //
  // [rootDir] is guaranteed to exist rather than created here: the only path that starts this
  // launcher is `BaseSpaceServer.startSpace`, which calls `installSpace()` first, and the rootfs
  // tarball carries `home/jupydroid/`. A cwd that does not exist would make `start()` throw.
  .directory(rootDir)
  .redirectErrorStream(true)
  .redirectOutput(jupyterServerLogFile(runtimeDir))

/** One home for the log's path: [jupyterServerProcess] writes it and a failed start reads it. */
internal fun jupyterServerLogFile(runtimeDir: File): File = File(runtimeDir, "jupyter_server.log")

/**
 * The server's command line, as a value rather than a side effect of starting one.
 *
 * Separated so it can be asserted without a Space: starting a real jupyter_server is a tablet
 * gate. [rootDir] is what makes the server and the app agree about where a notebook is - it went
 * unset for eight steps, which left the Contents API rooted at whatever cwd `ProcessBuilder` gave
 * it, and nothing noticed because nothing but the app read the files.
 */
internal fun jupyterServerArgs(rootDir: File, token: String, shell: File): List<String> = listOf(
  "-m", "jupyter_server",
  "--ServerApp.ip=127.0.0.1",
  "--ServerApp.port=0", // the OS picks; the runtime file reports what was bound
  "--ServerApp.root_dir=${rootDir.path}",
  // Unix domain sockets rather than TCP for the kernel transport. Everything here
  // is one app on one device, so kernels need no ports and no network stack at all.
  // It also avoids jupyter_client validating the kernel IP against every local
  // interface, which it does via psutil.net_if_addrs() - that segfaults on Android.
  "--KernelManager.transport=ipc",
  "--IdentityProvider.token=$token", // ServerApp.token is deprecated since jupyter_server 2.0
  // Terminado spawns *this* shell, rather than whatever a PATH search turns up. Unpinned,
  // `jupyter_server_terminals/app.py:initialize_configurables` resolves it as
  //   [os.environ.get("SHELL") or which("sh")]
  // and then appends `-l`, because that append is guarded on `shell_override is None` and this
  // server's stdout is a file rather than a tty (`start` calls `redirectOutput`). Both would be
  // wrong here: SHELL is unset, `which` searches an environment variable for the one binary the
  // Space cannot substitute, and a login shell would source a profile out of the rootfs that
  // nothing has audited.
  //
  // A **list**, never a string: the same function runs `shlex.split` over a string value, so a
  // path would be one quoting rule away from being torn in two, while a list reaches ptyprocess
  // as the argv it already is. Measured against jupyter_server 2.20.0 and
  // jupyter_server_terminals 0.5.4 - the versions in `tools/rootfs/requirements.lock` - rather
  // than assumed to parse: `evidence/p4-terminal/terminado-arg-parses.txt`.
  """--ServerApp.terminado_settings={"shell_command": ["${shell.path}"]}""",
  // The server may stop itself. `shutdown_no_activity_timeout` fires only while **no
  // kernels** exist (serverapp.py `shutdown_no_activity` returns early when `len(km) != 0`),
  // and idle kernels are culled first by `MappingKernelManager.cull_idle_timeout`. Culling
  // skips kernels with a connected websocket (`cull_connected` defaults False), so an open
  // notebook keeps its kernel no matter how long it idles - and once every kernel is gone the
  // server follows within ~a minute. A closed notebook's kernel survives at most ~6 min so the
  // reattach window stays useful; the 60 s cull poll is what makes that measurable.
  "--ServerApp.shutdown_no_activity_timeout=300",
  "--MappingKernelManager.cull_idle_timeout=300",
  "--MappingKernelManager.cull_interval=60",
  "--no-browser",
  // DEBUG until the app is feature-complete, because the server's own account is the half of
  // every kernel investigation we could not see. The execution stall cost six sightings partly
  // for this reason: our log could say "nothing came back" and never which of `Nudge: attempt N`,
  // `No such channel` or `Received message on closed websocket` the server was reporting at the
  // time - three mutually exclusive causes with three different fixes.
  //
  // It costs nothing on screen: `redirectOutput` sends it to `run/jupyter_server.log`, truncated
  // at every server start, so it is one run's worth of file and never touches logcat. Read it with
  //   adb shell run-as com.jupydroid.app cat files/spaces/base/run/jupyter_server.log
  // Drop it to INFO when the app is feature-complete, not before.
  "--ServerApp.log_level=DEBUG",
)
