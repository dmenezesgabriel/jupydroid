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
 * busybox's `timeout`. The bound is enforced inside the Space because its SIGKILL lands on the
 * script, a tracee, while the app's only handle is proot, which a kill would orphan the script from.
 *
 * **An outer shell runs `timeout` and exits with its status**, because proot passes on a tracee's
 * exit status but reports one killed by a signal as `0` - so a script killed at its bound would read
 * as a success (`SpaceStartupScriptDeviceTest.aScriptPastItsTimeoutIsKilledWithWhatItStartedAndExits137`).
 * The outer shell is never signalled; it sees `137` as an ordinary status. The trailing `exit $?`
 * is what keeps it there: busybox `ash` execs the last command of a `-c` string in place of itself,
 * which would make `timeout` proot's tracee again. [script] reaches the inner shell as `$1`, so no
 * quoting of it is needed.
 */
internal fun startupScriptArgs(script: String, timeout: Duration): List<String> = listOf(
  SPACE_SHELL,
  "-c",
  "$SPACE_TIMEOUT -s KILL ${timeout.inWholeSeconds} $SPACE_SHELL -c \"\$1\"; exit \$?",
  STARTUP_SCRIPT_ARG0,
  script,
)

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
