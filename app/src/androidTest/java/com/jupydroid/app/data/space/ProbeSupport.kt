package com.jupydroid.app.data.space

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

/** What a probe's process returned when it never finished: no exit status exists. */
const val PROBE_TIMED_OUT = -1

/**
 * Runs [command] in [runtime] with a bound, so a probe records a hang instead of waiting on it.
 *
 * A probe's process is a Space process under proot: it can outlive its command when the command
 * leaves children behind, so the bound is what makes "did not terminate" a result rather than a
 * test timeout.
 *
 * ```
 * val run = runBounded(runtime, listOf("/usr/bin/true"), 120)
 * ```
 */
fun runBounded(runtime: SpaceRuntime, command: List<String>, timeoutSeconds: Long): SpaceProcessRun {
  val process = runtime.execProcessBuilder(command).redirectErrorStream(true).start()
  val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
  if (!exited) process.destroyForcibly()
  val output = process.inputStream.bufferedReader().use { it.readText() }
  return SpaceProcessRun(command.joinToString(" "), if (exited) process.exitValue() else PROBE_TIMED_OUT, output)
}

/**
 * Replaces [this] with a symlink to [target], as an image's own entry is written.
 *
 * `delete()` first because a re-extract may find the path already there, and `createSymbolicLink`
 * refuses an existing one.
 *
 * ```
 * destination.replaceWithSymbolicLink("usr/bin")
 * ```
 */
fun File.replaceWithSymbolicLink(target: String) {
  parentFile?.mkdirs()
  delete()
  Files.createSymbolicLink(toPath(), File(target).toPath())
}
