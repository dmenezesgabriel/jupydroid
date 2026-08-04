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
 *
 * [extraEnv] adds to the Space environment for one runner. Note that setting
 * `LD_PRELOAD` through it is not the `termux-exec` interceptor docs/PLAN.md rejects:
 * that one hooks `/system/bin/linker64` for processes we do not own. This is an
 * ordinary env var on our own [ProcessBuilder].
 */
class PythonRunner(
  private val runtime: SpaceRuntime,
  private val workingDir: File? = null,
  private val extraEnv: Map<String, String> = emptyMap(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  suspend fun run(vararg args: String): ProcessOutcome = withContext(ioDispatcher) {
    val process = processBuilder(args).start()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    ProcessOutcome(exitCode = process.waitFor(), output = output.trim())
  }

  private fun processBuilder(args: Array<out String>): ProcessBuilder =
    runtime.processBuilder(args.toList(), extraEnv)
      .directory(workingDir)
      .redirectErrorStream(true) // one ordered stream: a traceback belongs with the output
}
