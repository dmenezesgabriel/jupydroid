package com.jupydroid.app.data.space

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.port.SpaceShellEvent
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
 * The refusals the SIGSYS handler wrote, minus the ones a Space workload sets off and survives.
 *
 * `mbind` (235 on aarch64) is numpy's on import, and `io_uring_setup` (425) is node's libuv
 * probing at startup - a harness's install runs node, so the log sees it even with the harness
 * later removed. ADR-0004's report-and-return leaves both working: the caller gets ENOSYS and
 * falls back. The log is shared by every Space process and appended to, so any suite that has
 * touched Python or node puts lines there - which is why a caller asserts on what it finds
 * rather than on the file being absent.
 */
fun seccompRefusalsIn(baseDir: File): List<String> =
  File(baseDir, "tmp/sigsys.log").takeIf { it.isFile }?.readText().orEmpty()
    .lines().filter { it.isNotBlank() && TOLERATED_REFUSALS.none { tolerated -> tolerated in it } }

/**
 * Syscall numbers rather than names because the log records numbers and no table maps them per
 * ABI: 235 is `mbind` on aarch64, 425 is `io_uring_setup` on both shipped ABIs.
 */
private val TOLERATED_REFUSALS = setOf("syscall=235", "syscall=425")

/** `adb logcat -s JupydroidSpace`, the filter every Space investigation already uses. */
const val SPACE_LOG_TAG = "JupydroidSpace"

/** The [SpaceShellEvent.Output] lines of a `SpaceShell.run` result - what the script printed. */
fun List<SpaceShellEvent>.outputLines(): List<String> = filterIsInstance<SpaceShellEvent.Output>().map { it.line }
