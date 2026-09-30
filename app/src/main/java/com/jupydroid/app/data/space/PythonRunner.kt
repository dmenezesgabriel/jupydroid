package com.jupydroid.app.data.space

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** What a finished Space process left behind. */
data class ProcessOutcome(val exitCode: Int, val output: String) {
  val succeeded: Boolean get() = exitCode == 0
}

/**
 * Runs a Space's Python to completion and collects its output.
 *
 * For short commands only - `jupyter_server` is long-lived and has `JupyterServerLauncher` of its
 * own. This exists to answer the question the whole rootfs design rests on:
 * can the app execve the Space interpreter at all?
 *
 * ```
 * PythonRunner(runtime).run("-c", "import sys; print(sys.version)")
 * ```
 */
class PythonRunner(
  private val runtime: SpaceRuntime,
  private val workingDir: File? = null,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  suspend fun run(vararg args: String): ProcessOutcome = withContext(ioDispatcher) {
    val process = processBuilder(args).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    ProcessOutcome(exitCode = process.waitFor(), output = output.trim())
  }

  private fun processBuilder(args: Array<out String>): ProcessBuilder = runtime.processBuilder(args.toList())
    .directory(workingDir)
    .redirectErrorStream(true) // one ordered stream: a traceback belongs with the output
}
