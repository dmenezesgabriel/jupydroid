package com.jupydroid.app.data.space

import java.io.File
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a finished Space process left behind. */
data class ProcessOutcome(val exitCode: Int, val output: String) {
  val succeeded: Boolean get() = exitCode == 0
}

/**
 * Runs a Space's Python to completion and collects its output.
 *
 * For short commands only - `jupyter_server` is long-lived and gets its own launcher
 * in Step 8.4. This exists to answer the question the whole rootfs design rests on:
 * can the app execve a glibc interpreter at all?
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

  private fun processBuilder(args: Array<out String>): ProcessBuilder =
    ProcessBuilder(runtime.command(*args))
      .directory(workingDir)
      .redirectErrorStream(true) // one ordered stream: a traceback belongs with the output
      .apply {
        // Android's own environment would leak its PATH and linker vars into the Space.
        environment().clear()
        environment().putAll(runtime.environment())
      }
}
