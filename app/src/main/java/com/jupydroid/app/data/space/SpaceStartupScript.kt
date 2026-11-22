package com.jupydroid.app.data.space

import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * How long a startup script may run before it is killed and the start fails: SageMaker's own limit
 * for a lifecycle configuration (<https://docs.aws.amazon.com/sagemaker/latest/dg/jl-lcc-debug.html>),
 * long enough for a `pip install` on a tablet.
 */
val STARTUP_SCRIPT_TIMEOUT: Duration = 5.minutes

/** What a script killed at its timeout exits with: `128 + SIGKILL`. */
private const val KILLED_AT_TIMEOUT = 128 + 9

/** How much of a log's end a failed start shows, the startup script's and the server's alike. */
internal const val LOG_EXCERPT = 2000

/**
 * The startup script's command line inside the Space: [script] in the Space's shell, bounded by
 * busybox's `timeout` ([boundedShellArgs]), with [STARTUP_SCRIPT_ARG0] naming it in `ps`.
 */
internal fun startupScriptArgs(script: String, timeout: Duration): List<String> =
  boundedShellArgs(STARTUP_SCRIPT_ARG0, script, timeout)

/** The outer shell's `$0`, which names it in `ps` and in any message the shell prints. */
private const val STARTUP_SCRIPT_ARG0 = "startup-script"

/**
 * The startup script's process, configured but not started - the launch as a value, the way
 * [jupyterServerProcess] is. proot kills whatever the script left running once `timeout` exits,
 * so a backgrounded command can neither hold the start open nor outlive it. Its output replaces
 * `run/startup.log`, one start's worth beside the server's own log.
 */
internal fun startupScriptProcess(runtime: SpaceRuntime, runtimeDir: File, script: String, timeout: Duration) =
  runtime.execProcessBuilder(startupScriptArgs(script, timeout), killOnExit = true)
    .redirectErrorStream(true)
    .redirectOutput(startupScriptLogFile(runtimeDir))

/** One home for the script log's path: the process writes it and a failure reads it. */
internal fun startupScriptLogFile(runtimeDir: File): File = File(runtimeDir, "startup.log")

/** The last [LOG_EXCERPT] characters of [log], or nothing where there is no log. */
internal fun logTail(log: File): String = log.takeIf { it.isFile }?.readText().orEmpty().takeLast(LOG_EXCERPT)

/**
 * Refuses a start whose script exited [status], in the words the home shows: the status, what it
 * means when it is the timeout's, and the end of what the script printed.
 *
 * ```
 * requireStartupScriptSucceeded(3, runtimeDir, STARTUP_SCRIPT_TIMEOUT)
 * ```
 */
internal fun requireStartupScriptSucceeded(status: Int, runtimeDir: File, timeout: Duration) {
  if (status == 0) return
  val cause = if (status == KILLED_AT_TIMEOUT) " - killed at its $timeout timeout" else ""
  throw IllegalStateException(
    "startup script exited with status $status$cause; expected 0. Output:\n" +
      logTail(startupScriptLogFile(runtimeDir)),
  )
}
