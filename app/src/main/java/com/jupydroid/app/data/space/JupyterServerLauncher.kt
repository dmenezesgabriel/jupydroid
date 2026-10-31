package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Runs stock `jupyter_server` out of a Space, on loopback.
 *
 * Bound to 127.0.0.1 with an ephemeral port: loopback keeps the server inside this
 * app's sandbox. It still runs token auth — see [token]: rejecting unauthenticated
 * POSTs is what jupyter_server offers non-browser clients. The port is not chosen
 * by us — `--ServerApp.port=0` lets the OS pick and the server reports what it
 * bound in its runtime file, which removes the race in picking a free port and
 * hoping it is still free a moment later.
 *
 * Every per-Space input - the environment, the runtime dir, the Contents API root - is derived
 * from the Space [start] is handed, so one launcher serves whichever Space it is asked for. Its
 * settings are read at every start, never kept: a Space configured on the home after its last run
 * starts with what was saved, whether the home's Run or a cell's restart is what starts it. Its
 * startup script, if it has one, runs to completion before the server and must exit 0.
 *
 * [stop] asks the server to stop with `POST api/shutdown`, JupyterLab's own stop
 * (`jupyterlab@7f0e690 packages/mainmenu-extension/src/index.ts`, `CommandIDs.shutdown`). No signal
 * can do it. The process is proot, which ignores SIGTERM (what `Process.destroy()` sends), and
 * SIGKILL to proot orphans the server and kernel it traces. `TwoSpacesAtOnceDeviceTest` measures
 * all three.
 *
 * ```
 * spaceServerLauncher(context).start(space)
 * ```
 */
class JupyterServerLauncher(
  /** How a process runs in the Space under the settings it starts with - its variables among them. */
  private val runtimeFor: (Space, SpaceSettings) -> SpaceRuntime,
  /** Where a Space's server writes its runtime files and roots its Contents API. */
  private val dirsFor: (Space) -> SpaceDirs,
  /** The Space's settings as stored now - asked once per [start]. */
  private val settingsFor: suspend (Space) -> SpaceSettings,
  /**
   * The kernel the Space's server starts by default - asked once per [start], like [settingsFor], and
   * already checked against the Space's catalogue (`data/disk/DiskKernelSpecs.kt`).
   */
  private val defaultKernelFor: suspend (Space) -> String = { DEFAULT_KERNEL_ID },
  /**
   * A free loopback port for the Space's MCP server - asked once per [start], like [settingsFor], and
   * as late as it can be, just before the server starts, so another process has the least time to take it.
   */
  private val mcpPortFor: () -> Int = ::freeLoopbackPort,
  private val startTimeout: Duration = START_TIMEOUT,
  private val startupScriptTimeout: Duration = STARTUP_SCRIPT_TIMEOUT,
  private val stopTimeout: Duration = STOP_TIMEOUT,
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
  private val client: OkHttpClient = OkHttpClient(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ServerLauncher {
  private var process: Process? = null

  /** What the running server reported, so [stop] knows where to ask it to stop. */
  private var connection: ServerConnection? = null

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
    val dirs = dirsFor(space)
    val settings = settingsFor(space)
    val defaultKernel = defaultKernelFor(space)
    val runtime = runtimeFor(space, settings)
    withContext(ioDispatcher) {
      dirs.ensure()
      SpacePipConfig.ensure(dirs.spaceDir)
      dirs.clearRuntime() // a stale jpserver-<pid>.json would advertise a dead port
    }
    runStartupScript(runtime, dirs.runtime, settings.startupScript)
    val started = withContext(ioDispatcher) {
      val rootDir = dirs.inRoot(dirs.workspace)
      jupyterServerProcess(runtime, dirs.runtime, rootDir, token, settings, defaultKernel, mcpPortFor()).start()
    }
    process = started
    val reported = awaitConnection(dirs.runtime) ?: failStart(started, dirs.runtime)
    return reported.also { connection = it }
  }

  /**
   * Runs [script], when there is one, and waits for it: blocking a thread as [awaitExit] does, and
   * interruptible, so a cancelled start frees it. The bound is the script's own ([startupScriptArgs]).
   */
  private suspend fun runStartupScript(runtime: SpaceRuntime, runtimeDir: File, script: String) {
    if (script.isBlank()) return
    val status = runInterruptible(ioDispatcher) {
      startupScriptProcess(runtime, runtimeDir, script, startupScriptTimeout).start().waitFor()
    }
    requireStartupScriptSucceeded(status, runtimeDir, startupScriptTimeout)
  }

  /**
   * Asks the server to stop and waits up to [stopTimeout] for its process to exit.
   *
   * A server that outlives the wait, or one that never reported a port to ask, is logged and kept
   * rather than thrown about: [isRunning] stays true, which is what the process actually is, and
   * the idle backstop that calls this runs on a scope with no handler to catch a throw.
   */
  override suspend fun stop() {
    val running = process?.takeIf { it.isAlive }
    connection?.takeIf { running != null }?.let { postShutdown(it) }
    if (running != null && !exitsWithinStopTimeout(running)) {
      val cause =
        IllegalStateException("$running still alive $stopTimeout after api/shutdown; expected exit")
      return log.failed(OUTLIVED_SHUTDOWN, cause)
    }
    process = null
    connection = null
  }

  override fun isRunning(): Boolean = process?.isAlive == true

  /**
   * Blocks a thread in `waitFor()` rather than polling: Android's `Process` has no `onExit()`, and
   * a poll would wake a backgrounded app for nothing. Interruptible, so cancelling the waiter frees
   * the thread.
   */
  override suspend fun awaitExit() {
    val running = process ?: return
    runInterruptible(ioDispatcher) { running.waitFor() }
  }

  /**
   * Why the server stopped, or null while it is alive. Values above 128 are
   * `128 + signal`, which is how a seccomp kill (SIGSYS, 159) shows up.
   */
  fun exitCode(): Int? = process?.takeIf { !it.isAlive }?.exitValue()

  /**
   * `POST api/shutdown` with the server's token. Its answer is not read, and a failure is not
   * raised: a server that exits mid-answer has answered, and [exitsWithinStopTimeout] is what decides.
   */
  private suspend fun postShutdown(server: ServerConnection) {
    val request = Request.Builder()
      .url(server.baseUrl + "api/shutdown")
      .header("Authorization", "token ${server.token}")
      .post(ByteArray(0).toRequestBody())
      .build()
    withContext(ioDispatcher) { runCatching { client.newCall(request).execute().close() } }
  }

  private suspend fun exitsWithinStopTimeout(running: Process): Boolean = withContext(ioDispatcher) {
    running.waitFor(stopTimeout.inWholeMilliseconds, TimeUnit.MILLISECONDS)
  }

  private suspend fun awaitConnection(runtimeDir: File): ServerConnection? = withTimeoutOrNull(startTimeout) {
    while (true) {
      val runtimeFile = withContext(ioDispatcher) { JupyterRuntimeFile.read(runtimeDir) }
      if (runtimeFile != null) return@withTimeoutOrNull runtimeFile.toConnection()
      if (process?.isAlive != true) return@withTimeoutOrNull null
      delay(POLL_INTERVAL)
    }
    @Suppress("UNREACHABLE_CODE")
    null
  }

  private suspend fun failStart(started: Process, runtimeDir: File): Nothing {
    stop()
    val log = withContext(ioDispatcher) { logTail(jupyterServerLogFile(runtimeDir)) }
    throw IllegalStateException(
      "jupyter_server did not report a port within $startTimeout (alive=${started.isAlive}); log:\n$log",
    )
  }

  private fun JupyterRuntimeFile.toConnection() =
    ServerConnection(baseUrl = url.ifBlank { "http://127.0.0.1:$port/" }, port = port, token = token)

  private companion object {
    const val TOKEN_BYTES = 32
    val START_TIMEOUT = 60.seconds

    /**
     * The whole tree went 772-991 ms after `api/shutdown` in `TwoSpacesAtOnceDeviceTest`, so ten
     * times that is slack.
     */
    val STOP_TIMEOUT = 10.seconds
    const val OUTLIVED_SHUTDOWN = "jupyter_server outlived api/shutdown"
    val POLL_INTERVAL = 100.milliseconds
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
  rootDir: String,
  token: String,
  settings: SpaceSettings,
  defaultKernel: String,
  mcpPort: Int,
): ProcessBuilder = runtime.processBuilder(
  // JUPYTER_RUNTIME_DIR and the faulthandler both come from SpaceRuntime.environment(),
  // so every Space process - not just the server - agrees on where runtime files live.
  //
  // The server's cwd is [rootDir] too, set by the runtime's proot `-w`. `root_dir` defaults to
  // `os.getcwd()` (`serverapp.py:1842-1848`), so upstream the two are one directory and everything
  // downstream is written against that. A terminal is the client that proves it: `PtyWithClients`
  // inherits this cwd whenever the POST carries none (`terminado/management.py:225-232`). See
  // [JupyterServerWorkingDirTest].
  jupyterServerArgs(rootDir, token, settings.idleShutdownMinutes, defaultKernel, mcpPort),
)
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
internal fun jupyterServerArgs(
  rootDir: String,
  token: String,
  idleShutdownMinutes: Int,
  defaultKernel: String = DEFAULT_KERNEL_ID,
  mcpPort: Int = UPSTREAM_MCP_PORT,
): List<String> = listOf(
  "-m", "jupyter_server",
  "--ServerApp.ip=$LOOPBACK_HOST",
  "--ServerApp.port=0", // the OS picks; the runtime file reports what was bound
  "--ServerApp.root_dir=$rootDir",
  // proot's `-0` makes every process see uid 0, which `apk add` needs, and jupyter_server refuses
  // to start as root without this flag (jupyter_server 2.20.0 `serverapp.py` `ServerApp.start_app`).
  "--allow-root",
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
  // server's stdout is a file rather than a tty (`start` calls `redirectOutput`). The login shell
  // is the wrong half: it would source a profile out of the image that nothing has audited.
  //
  // A **list**, never a string: the same function runs `shlex.split` over a string value, so a
  // path would be one quoting rule away from being torn in two, while a list reaches ptyprocess
  // as the argv it already is. Measured against jupyter_server 2.20.0 and
  // jupyter_server_terminals 0.5.4 - the versions in `tools/rootfs/requirements.lock` - rather
  // than assumed to parse.
  """--ServerApp.terminado_settings={"shell_command": ["$SPACE_SHELL"]}""",
  // The server may stop itself. `shutdown_no_activity_timeout` fires only while **no
  // kernels** exist (serverapp.py `shutdown_no_activity` returns early when `len(km) != 0`),
  // and idle kernels are culled first by `MappingKernelManager.cull_idle_timeout`. Culling
  // skips kernels with a connected websocket (`cull_connected` defaults False), so an open
  // notebook keeps its kernel no matter how long it idles - and once every kernel is gone the
  // server follows within ~a minute. Both timers take the Space's one idle setting, since
  // neither fires without the other, and both read `0` as never; a closed notebook's kernel
  // survives at most the setting plus the 60 s cull poll, which is what makes it measurable.
  "--ServerApp.shutdown_no_activity_timeout=${idleShutdownMinutes.minutes.inWholeSeconds}",
  "--MappingKernelManager.cull_idle_timeout=${idleShutdownMinutes.minutes.inWholeSeconds}",
  "--MappingKernelManager.cull_interval=60",
  // What a session asked for no kernel starts, and what `GET api/kernelspecs` answers as `default`
  // (`usr/lib/python3.12/site-packages/jupyter_client/multikernelmanager.py` `default_kernel_name`;
  // `jupyter_server/services/kernelspecs/handlers.py`) - so the kernel a new notebook records.
  "--MappingKernelManager.default_kernel_name=$defaultKernel",
  // A file deleted through the Contents API is gone, as one the explorer deletes is. Left on, the
  // default, `send2trash` (in the rootfs) moves it into a hidden `~/.local/share/Trash` nothing empties
  // (`usr/lib/python3.12/site-packages/jupyter_server/services/contents/filemanager.py`, `delete_file`).
  "--FileContentsManager.delete_to_trash=False",
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
) + mcpServerArgs(token, mcpPort)

/**
 * jupyter-server-mcp's port, and the entry that points the agent at it.
 *
 * The port is picked per launch because upstream's fixed default, [UPSTREAM_MCP_PORT], is one port
 * for every Space, and a second Space running at once fails to bind it. The host is pinned because
 * `MCPServer.host` defaults to `localhost`, for which uvicorn binds IPv4 and IPv6 separately
 * (`usr/lib/python3.12/site-packages/jupyter_server_mcp/mcp_server.py` `_capture_bound_port`); on
 * [LOOPBACK_HOST] it binds the port [freeLoopbackPort] found free.
 *
 * The persona manager's own entry is `http://localhost:<mcp_port>/mcp` with no headers
 * (`usr/lib/python3.12/site-packages/jupyter_ai_persona_manager/persona_manager.py`
 * `_default_builtin_mcp_servers` in the shipped rootfs), and the image's MCP middleware refuses any
 * caller without the server's token (`usr/lib/python3.12/site-packages/jupydroid_mcp_bearer.py`).
 * So the entry is set whole, header included. traitlets 5.16.1 reads a list of dicts from the command
 * line only as a Python literal (`Container.from_string_list`, `literal_eval`); the token is hex, so
 * it needs no quoting.
 */
private fun mcpServerArgs(token: String, mcpPort: Int): List<String> = listOf(
  "--MCPExtensionApp.mcp_port=$mcpPort",
  "--MCPServer.host=$LOOPBACK_HOST",
  "--PersonaManager.builtin_mcp_servers=[{'type': 'http', 'name': '$MCP_SERVER_NAME', " +
    "'url': 'http://$LOOPBACK_HOST:$mcpPort/mcp', " +
    "'headers': [{'name': 'Authorization', 'value': 'Bearer $token'}]}]",
)

/** `MCPExtensionApp.mcp_port`'s default (`usr/lib/python3.12/site-packages/jupyter_server_mcp/extension.py`). */
private const val UPSTREAM_MCP_PORT = 3001

/** `MCPExtensionApp.mcp_name`'s default, which the persona manager's own entry also uses. */
private const val MCP_SERVER_NAME = "Jupyter MCP Server"
