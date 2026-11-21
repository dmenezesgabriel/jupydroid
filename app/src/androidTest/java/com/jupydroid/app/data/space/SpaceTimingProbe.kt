package com.jupydroid.app.data.space

import android.os.Build
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import kotlin.time.TimeSource

/** How many times a probe repeats each part it measures. */
const val PROBE_RUNS = 3

/** Every run is kept in order, because the first of a kind is the one a cold page cache would slow. */
fun runsOf(measure: () -> Long): List<Long> = List(PROBE_RUNS) { measure() }

fun List<Long>.median(): Long = sorted()[size / 2]

/** One report line: [label], every run in order, and their median. */
fun row(label: String, runs: List<Long>, unit: String = "ms"): String =
  "${label.padEnd(LABEL_WIDTH)}: ${runs.joinToString(" / ")} $unit, median ${runs.median()} $unit\n"

private const val LABEL_WIDTH = 30

/** Skips a probe where no rootfs ships for the device's ABI, and installs base where one does. */
fun installBaseWhereProotShips() {
  assumeTrue(
    "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}",
    runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
  )
  val context = InstrumentationRegistry.getInstrumentation().targetContext
  runBlocking {
    baseSpaceInstaller(context).installIfNeeded()
    gnuSpaceImageInstaller(context).ensureInstalled(baseSpaceDir(context))
  }
}

/**
 * **What a device probe times inside a Space:** its interpreter, an import's slowest modules, any
 * part measured again after the image's Python has been dropped from the page cache, and any part
 * measured with an import running beside it.
 *
 * `ServerBootProbeDeviceTest` splits a stage this way, on the installed Space and under the same
 * proot the app runs.
 *
 * ```
 * val probe = SpaceTimingProbe(spaceRuntime(context, BASE_SPACE))
 * val cold = probe.evicted { probe.timedPython("-c", "import jupyter_server.serverapp") }
 * ```
 */
class SpaceTimingProbe(private val runtime: SpaceRuntime) {
  /** Wall time of the Space's interpreter run with [args], and [extraEnv] beside its own, to its exit, which must be 0. */
  fun timedPython(vararg args: String, extraEnv: Map<String, String> = emptyMap()): Long {
    val begin = TimeSource.Monotonic.markNow()
    val process = runtime.processBuilder(args.toList(), extraEnv).redirectErrorStream(true).start()
    val output = process.inputStream.bufferedReader().readText()
    check(process.waitFor() == 0) { "python3 ${args.joinToString(" ")} exited ${process.exitValue()}: $output" }
    return begin.elapsedNow().inWholeMilliseconds
  }

  /**
   * [module]'s [count] slowest imports at any depth, from `-X importtime`, in import order and
   * indented as CPython indents them - one space plus two per level of nesting
   * (`Python/import.c`, `import_find_and_load`) - so a row's parents are the less indented rows
   * above it.
   */
  fun slowestImports(module: String, count: Int = SLOWEST_IMPORTS): String {
    val process = runtime.processBuilder(listOf("-X", "importtime", "-c", "import $module")).start()
    val rows = process.errorStream.bufferedReader().readLines().mapNotNull(::importRow)
    process.waitFor()
    val slowest = rows.sortedByDescending { it.second }.take(count).toSet()
    return rows.filter { it in slowest }
      .joinToString("") { (name, micros) -> "  ${name.padEnd(IMPORT_WIDTH)} : ${micros / 1000}ms cumulative\n" }
  }

  /**
   * [measure], after the clean pages of every file under the image's `/usr/lib/python3.12` are
   * dropped from the page cache, as memory pressure does between two launches of the app.
   * `POSIX_FADV_DONTNEED` needs no root, only a descriptor (posix_fadvise(2)).
   */
  fun <T> evicted(measure: () -> T): T {
    timedPython("-c", EVICT_PYTHON)
    return measure()
  }

  /**
   * [measure] while `import [module]` runs beside it, as a pre-read would if **Run** were tapped the
   * moment it began; the import is waited for after, and must exit 0.
   */
  fun <T> besideImport(module: String, measure: () -> T): T {
    val import = runtime.processBuilder(listOf("-c", "import $module")).redirectErrorStream(true).start()
    return measure().also {
      val output = import.inputStream.bufferedReader().readText()
      check(import.waitFor() == 0) { "import $module beside exited ${import.exitValue()}, expected 0: $output" }
    }
  }

  /** `import time: self | cumulative | name` -> (name with its indent, cumulative µs). */
  private fun importRow(row: String): Pair<String, Long>? {
    val columns = row.removePrefix("import time:").split("|")
    if (columns.size != 3) return null
    return columns[1].trim().toLongOrNull()?.let { columns[2].trimEnd() to it }
  }

  private companion object {
    const val SLOWEST_IMPORTS = 30
    const val IMPORT_WIDTH = 48
    val EVICT_PYTHON = """
      import os
      for top, _, names in os.walk("/usr/lib/python3.12"):
          for name in names:
              try:
                  fd = os.open(os.path.join(top, name), os.O_RDONLY)
              except OSError:
                  continue
              os.posix_fadvise(fd, 0, 0, os.POSIX_FADV_DONTNEED)
              os.close(fd)
    """.trimIndent()
  }
}
