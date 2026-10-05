package com.jupydroid.app.data.space

import java.io.File

/** One live process, as `/proc` reports it: its pid, parent, argv and resident set. */
data class LiveProcess(val pid: Int, val parentPid: Int, val command: String, val residentKb: Long)

/**
 * Every process started under one Space's root: the proot tracer whose `-r` names [spaceDir], and
 * all of its descendants.
 *
 * Asked by ancestry, not by the processes' own arguments. Inside a translated root every Space's
 * server reads the same `/usr/bin/python3 -m jupyter_server`, so only the tracer's `-r` tells two
 * Spaces apart (`ProotCommand.argv`). The answer comes from `/proc`, read in this app's process,
 * which sees its own uid's processes and so every child the app started.
 *
 * ```
 * spaceProcessTree(spaceDirIn(spacesDir(context), "base")).sumOf { it.residentKb }
 * ```
 */
fun spaceProcessTree(spaceDir: File): List<LiveProcess> {
  val live = liveProcesses()
  val roots = live.filter { it.isProotFor(spaceDir) }.map { it.pid }.toMutableSet()
  // Parents before children is not guaranteed by pid order, so grow the set until it stops growing.
  var grown = true
  while (grown) {
    grown = live.filter { it.parentPid in roots && it.pid !in roots }.map { it.pid }.let(roots::addAll)
  }
  return live.filter { it.pid in roots }
}

private fun LiveProcess.isProotFor(spaceDir: File): Boolean =
  "proot" in command && " -r ${spaceDir.path} " in "$command "

private fun liveProcesses(): List<LiveProcess> = File("/proc").listFiles().orEmpty()
  .mapNotNull { it.name.toIntOrNull() }
  .mapNotNull(::readProcess)

/** Null for a process that exited mid-read, or one this uid may not see. */
private fun readProcess(pid: Int): LiveProcess? = runCatching {
  val status = File("/proc/$pid/status").readLines().associate { line ->
    line.substringBefore(':') to line.substringAfter(':').trim()
  }
  val command = File("/proc/$pid/cmdline").readText().replace('\u0000', ' ').trim()
  LiveProcess(
    pid = pid,
    parentPid = status.getValue("PPid").toInt(),
    command = command,
    // A zombie has no VmRSS line: it holds no memory, which is what 0 says.
    residentKb = status["VmRSS"]?.substringBefore(' ')?.toLong() ?: 0L,
  )
}.getOrNull()
