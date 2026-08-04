package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import java.io.File
import java.security.SecureRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
      runtime.processBuilder(serverArgs(), mapOf("JUPYTER_RUNTIME_DIR" to runtimeDir.path))
        .redirectErrorStream(true)
        .redirectOutput(logFile())
        .start()
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

  private suspend fun awaitConnection(): ServerConnection? =
    withTimeoutOrNull(startTimeout) {
      while (true) {
        val runtimeFile = withContext(ioDispatcher) { JupyterRuntimeFile.read(runtimeDir) }
        if (runtimeFile != null) return@withTimeoutOrNull runtimeFile.toConnection()
        if (process?.isAlive != true) return@withTimeoutOrNull null
        delay(POLL_INTERVAL)
      }
      @Suppress("UNREACHABLE_CODE") null
    }

  private suspend fun failStart(started: Process): Nothing {
    stop()
    val log = withContext(ioDispatcher) { logFile().takeIf { it.isFile }?.readText().orEmpty() }
    throw IllegalStateException(
      "jupyter_server did not report a port within $startTimeout " +
        "(alive=${started.isAlive}); log:\n${log.takeLast(LOG_EXCERPT)}",
    )
  }

  private fun logFile(): File = File(runtimeDir, "jupyter_server.log")

  private fun serverArgs(): List<String> = listOf(
    "-m", "jupyter_server",
    "--ServerApp.ip=127.0.0.1",
    "--ServerApp.port=0", // the OS picks; the runtime file reports what was bound
    "--IdentityProvider.token=$token", // ServerApp.token is deprecated since jupyter_server 2.0
    "--no-browser",
  )

  private fun JupyterRuntimeFile.toConnection() =
    ServerConnection(baseUrl = url.ifBlank { "http://127.0.0.1:$port/" }, port = port, token = token)

  private companion object {
    const val TOKEN_BYTES = 32
    val START_TIMEOUT = 60.seconds
    val POLL_INTERVAL = 100.milliseconds
    const val LOG_EXCERPT = 2000
  }
}
