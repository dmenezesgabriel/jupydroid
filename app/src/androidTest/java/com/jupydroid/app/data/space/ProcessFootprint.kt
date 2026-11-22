package com.jupydroid.app.data.space

import android.system.Os
import android.system.OsConstants
import java.io.File

/**
 * **What a set of processes holds while it waits**: the memory and CPU time of every process whose
 * arguments [matches] accepts, read from `/proc` as Android counts an app's own.
 *
 * `ServerBootProbeDeviceTest` weighs a pre-started server this way, which is two processes:
 * proot and the server it traces.
 *
 * ```
 * val server = ProcessFootprint { "jupyter_server" in it }
 * server.proportionalSetKb()  // -> 61234
 * ```
 */
class ProcessFootprint(private val matches: (List<String>) -> Boolean) {
  /** The processes [matches] accepts, each as its `/proc/<pid>` directory; there must be one at least. */
  fun processes(): List<File> = File("/proc").listFiles { it.name.all(Char::isDigit) }.orEmpty()
    .filter { matches(arguments(it)) }
    .also { check(it.isNotEmpty()) { "expected a process whose arguments match, found none" } }

  /**
   * `Pss:` from each process's `smaps_rollup`, summed: its own pages plus its share of those it maps
   * with others.
   */
  fun proportionalSetKb(): Long = processes().sumOf { process ->
    File(process, "smaps_rollup").readLines().first { it.startsWith("Pss:") }.split(Regex("\\s+"))[1].toLong()
  }

  /** User plus system CPU time of the processes, in milliseconds. */
  fun cpuMs(): Long = processes().sumOf { cpuTicksOf(File(it, "stat").readText()) } * MS_PER_SECOND /
    Os.sysconf(OsConstants._SC_CLK_TCK)

  /**
   * The file name of each process's program, its first argument. Only the name: proot's path holds
   * the install's random directory.
   */
  fun programs(): List<String> = processes().map { arguments(it).first().substringAfterLast('/') }

  private companion object {
    const val MS_PER_SECOND = 1_000L
  }
}

/** A process's arguments, or none for one that exited or is not this app's to read. */
private fun arguments(pid: File): List<String> = runCatching { File(pid, "cmdline").readText().split('\u0000') }
  .getOrDefault(emptyList())

/**
 * `utime` plus `stime`, fields 14 and 15 of a `/proc/<pid>/stat` line, in clock ticks (proc(5)).
 * The fields are counted after the last `)`, because the command name before it may hold spaces
 * and parentheses of its own.
 */
fun cpuTicksOf(stat: String): Long {
  val afterCommand = stat.substringAfterLast(')').trim().split(' ')
  // afterCommand[0] is field 3, `state`.
  return afterCommand[UTIME_FIELD - FIRST_FIELD_AFTER_COMMAND].toLong() +
    afterCommand[STIME_FIELD - FIRST_FIELD_AFTER_COMMAND].toLong()
}

private const val FIRST_FIELD_AFTER_COMMAND = 3
private const val UTIME_FIELD = 14
private const val STIME_FIELD = 15
