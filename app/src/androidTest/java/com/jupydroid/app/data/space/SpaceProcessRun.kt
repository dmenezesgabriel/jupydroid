package com.jupydroid.app.data.space

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

/** What a Space process did: the command, how it exited, and everything it printed. */
data class SpaceProcessRun(val command: String, val exitCode: Int, val output: String)

/**
 * Runs a command **in this app's process**, which is the only place the question means anything.
 *
 * **This is the gate the first attempt was missing.** That one proved the shell through
 * `adb shell run-as`, which is not zygote-spawned and so carries no app seccomp filter. Under
 * the filter every applet died, having printed nothing - which `run-as` could never show.
 *
 * So this is a plain [ProcessBuilder] started from an instrumented test, which runs *inside* the
 * app process: the child is a sibling of the one `jupyter_server` forks, under the same filter,
 * with the same environment. **Nothing that uses this may shell out through `run-as`** - that is
 * the whole defect.
 *
 * [runtime] is whichever Space the command runs in - `base` unless a caller names another, so a
 * second Space's process gets that Space's `HOME`, `TMPDIR` and user base rather than base's.
 *
 * ```
 * val run = runInSpace(SPACE_SHELL, "-c", "python3 --version")
 * ```
 */
fun runInSpace(
  vararg command: String,
  runtime: SpaceRuntime = baseSpaceRuntime(InstrumentationRegistry.getInstrumentation().targetContext),
): SpaceProcessRun {
  // execProcessBuilder replaces Android's own PATH and linker variables with the Space's, the same
  // way it does for the language server.
  val process = runtime.execProcessBuilder(command.toList())
    .redirectErrorStream(true)
    .start()
  val output = process.inputStream.bufferedReader().use { it.readText() }
  return SpaceProcessRun(command.joinToString(" "), process.waitFor(), output)
    .also { Log.i(SPACE_LOG_TAG, "space run: $it") }
}

/**
 * The refusals the SIGSYS handler wrote, minus the one this runtime tolerates by design.
 *
 * `mbind` is excluded by number rather than the check being dropped: numpy issues it on import and
 * the handler returns rather than dying (ADR-0004). The log is shared by every Space process and appended
 * to, so any suite that has touched Python puts lines there - which is why a caller asserts on
 * what it finds rather than on the file being absent.
 */
fun seccompRefusalsIn(baseDir: File): List<String> =
  File(baseDir, "tmp/sigsys.log").takeIf { it.isFile }?.readText().orEmpty()
    .lines().filter { it.isNotBlank() && TOLERATED_REFUSAL !in it }

/** `mbind` on aarch64: numpy issues it on import, and ADR-0004 is why that is tolerated. */
private const val TOLERATED_REFUSAL = "syscall=235"

/** `adb logcat -s JupydroidSpace`, the filter every Space investigation already uses. */
const val SPACE_LOG_TAG = "JupydroidSpace"
