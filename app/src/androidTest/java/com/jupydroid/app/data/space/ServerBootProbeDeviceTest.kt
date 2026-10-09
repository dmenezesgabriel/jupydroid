package com.jupydroid.app.data.space

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.SpaceSettings
import kotlinx.coroutines.runBlocking
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.TimeSource

/**
 * **Where `jupyter_server`'s boot goes**: the span from `starting jupyter_server` to
 * `jupyter_server listening` in `SpaceServer`'s log, which grew by about a second and a half once
 * every Space process ran under proot (ADR-0073).
 *
 * This probe runs base's interpreter and its server through the shipped launcher, on the installed
 * Space, and splits the boot into these parts:
 *
 * - the interpreter alone, `python3 -c pass`
 * - `import jupyter_server.serverapp`, with its slowest modules, then the same import from an evicted
 *   page cache
 * - the whole start, from a stopped server to a reported port, and inside it the parts
 *   [ServerBootSplit] reads from the server's own log, warm and evicted
 * - the interpreter and the import with `PROOT_NO_SECCOMP=1`: what they cost when proot stops at
 *   every syscall rather than only at those it translates. With it unset, proot 5.4.0 lets a
 *   seccomp filter pass the syscalls it need not translate (`strings libproot.so`: "ptrace
 *   acceleration (seccomp mode 2) enabled"). The server's start is not timed that way, because it
 *   cannot run: it dies of SIGSEGV right after its extension links, while the Space's
 *   `tmp/sigsys.log` shows the app's filter refusing syscall -1
 *
 * Then it measures the three repairs that would take the import, or its read from storage, out of
 * the boot, each from an evicted page cache, which is how the app's warm start finds the image:
 *
 * - a pre-read: the import run once and thrown away, then the start
 * - the same pre-read begun beside the start, as a **Run** tapped the moment it began would find it
 * - importing less: the import with jsonschema's optional format checkers taken away, as
 *   uninstalling them would, without touching the installed image. `sys.modules[name] = None` makes
 *   every `import name` raise ImportError (the import system's reference, "The module cache"), and
 *   `jsonschema/_format.py` imports each checker under `suppress(ImportError)`
 * - a pre-started server: what it holds while nobody uses it, alone and with the spare kernel every
 *   start brings (ADR-0080), as its proportional set size and the CPU time it spends idle
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.ServerBootProbeDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ServerBootProbeDeviceTest {
  @Before
  fun onlyWhereProotShips() = installBaseWhereProotShips()

  @Test(timeout = 1_800_000)
  fun whereTheServerBootGoes() {
    val report = StringBuilder("== jupyter_server boot split (base, $PROBE_RUNS runs each, in run order)\n")
    val interpreter = runsOf { probe.timedPython("-c", "pass") }
    report.append(row("python3 -c pass", interpreter))
    report.append(row("python3 -c pass, no seccomp", runsOf { probe.timedPython("-c", "pass", extraEnv = NO_SECCOMP) }))
    report.append(row("import $SERVER_MODULE", runsOf { importServer() }))
    report.append(probe.slowestImports(SERVER_MODULE))
    report.append(row("import, no seccomp", runsOf { importServer(NO_SECCOMP) }))
    report.append(row("import, evicted", runsOf { probe.evicted { importServer() } }))
    report.append(bootRows("start, warm", List(PROBE_RUNS) { timedBoot() }))
    val evicted = List(PROBE_RUNS) { probe.evicted { timedBoot() } }
    report.append(bootRows("start, evicted", evicted)).append(evicted.last().widestGaps(WIDEST_GAPS))
    report.append(repairRows())
    File(context.getExternalFilesDir(null), "server-boot-probe.txt").writeText(report.toString())
    Log.i(SPACE_LOG_TAG, report.toString())

    // The probe's job is to print the split; fail only if it measured nothing.
    assertTrue(report.toString(), interpreter.median() > 0 && evicted.all { it.toRunningMs > 0 })
  }

  /** The repairs' rows, each start from an evicted page cache. */
  private fun repairRows(): String {
    val starts = listOf(
      "start, evicted, pre-read first" to { probe.evicted { importServer() }.let { timedBoot() } },
      "start, evicted, pre-read beside" to { probe.evicted { probe.besideImport(SERVER_MODULE) { timedBoot() } } },
    )
    return starts.joinToString("") { (label, measure) -> bootRows(label, List(PROBE_RUNS) { measure() }) } +
      row("import, checkers blocked", runsOf { importWithoutCheckers() }) +
      row("import, evicted, blocked", runsOf { probe.evicted { importWithoutCheckers() } }) +
      idleRows("idle server", withSpare = false) + idleRows("idle server and spare", withSpare = true)
  }

  private companion object {
    const val SERVER_MODULE = "jupyter_server.serverapp"

    /** jsonschema's optional format checkers, each imported under `suppress(ImportError)` in `jsonschema/_format.py`. */
    val FORMAT_CHECKERS = listOf(
      "fqdn",
      "idna",
      "rfc3986_validator",
      "rfc3339_validator",
      "webcolors",
      "jsonpointer",
      "uri_template",
      "isoduration",
    )

    /**
     * How long a pre-started server waits before it is weighed: the user's walk from Run to the first
     * cell, which the cold-path flow measures at about 13 s, and the spare kernel's start inside it.
     */
    const val SETTLE_MS = 13_000L

    /** How long a pre-started server's idle CPU time is counted over. */
    const val IDLE_MS = 30_000L

    const val WIDEST_GAPS = 10

    /** proot's own switch, read from its environment. */
    val NO_SECCOMP = mapOf("PROOT_NO_SECCOMP" to "1")

    val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val probe get() = SpaceTimingProbe(spaceRuntime(context, BASE_SPACE))

    /** One launcher for the class: `JupyterServerLauncher.start` begins with `stop()`. */
    val launcher by lazy {
      spaceServerLauncher(context, settingsFor = { SpaceSettings() }, defaultKernelFor = { DEFAULT_KERNEL_ID })
    }

    fun importServer(extraEnv: Map<String, String> = emptyMap()): Long =
      probe.timedPython("-c", "import $SERVER_MODULE", extraEnv = extraEnv)

    /** The server's import with every one of [FORMAT_CHECKERS] unimportable, as if uninstalled. */
    fun importWithoutCheckers(): Long {
      val blocked = FORMAT_CHECKERS.joinToString(", ") { "'$it'" }
      return probe.timedPython("-c", "import sys; sys.modules.update(dict.fromkeys([$blocked])); import $SERVER_MODULE")
    }

    /** [label]'s PSS and idle CPU rows, from [PROBE_RUNS] servers each started with nothing asking for it. */
    fun idleRows(label: String, withSpare: Boolean): String {
      val idle = List(PROBE_RUNS) { idleServer(withSpare) }
      return row("$label, PSS", idle.map { it.first }, "kB") +
        row("$label, CPU in ${IDLE_MS / 1000} s", idle.map { it.second }) +
        "  counted: ${idle.last().third.joinToString(", ")}\n"
    }

    /**
     * A server started afresh, and with [withSpare] a spare kernel as `SpaceServer` starts one,
     * then weighed [SETTLE_MS] later: its PSS, its CPU time over [IDLE_MS], and the programs counted.
     */
    fun idleServer(withSpare: Boolean): Triple<Long, Long, List<String>> {
      val connection = runBlocking { launcher.start(BASE_SPACE) }
      val spare = if (withSpare) runBlocking { OkHttpKernelLifecycle(connection).startBareKernel() }.kernelId else null
      val footprint = ProcessFootprint { arguments ->
        "jupyter_server" in arguments || (spare != null && arguments.any { it.endsWith("kernel-$spare.json") })
      }
      Thread.sleep(SETTLE_MS)
      val before = footprint.cpuMs()
      Thread.sleep(IDLE_MS)
      return Triple(footprint.proportionalSetKb(), footprint.cpuMs() - before, footprint.programs())
    }

    /** A stopped server started, timed to its reported port and split by its own log. */
    fun timedBoot(): ServerBootSplit {
      runBlocking { launcher.stop() }
      val spawnedAt = System.currentTimeMillis()
      val begin = TimeSource.Monotonic.markNow()
      runBlocking { launcher.start(BASE_SPACE) }
      val total = begin.elapsedNow().inWholeMilliseconds
      val log = jupyterServerLogFile(spaceDirs(context, BASE_SPACE).runtime).readLines()
      return ServerBootSplit.of(spawnedAt, total, serverLogLines(log))
    }

    /** The whole start's row, then a row for each part of it. */
    fun bootRows(label: String, boots: List<ServerBootSplit>): String = row(label, boots.map { it.totalMs }) +
      row("  spawn -> first log line", boots.map { it.toFirstLineMs }) +
      row("  first line -> running at", boots.map { it.toRunningMs }) +
      row("  running at -> port seen", boots.map { it.toReadyMs })

    @JvmStatic
    @AfterClass
    fun stopTheServer() {
      runBlocking { launcher.stop() }
    }
  }
}
