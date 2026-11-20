package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * **Can a glibc CPython install PyPI's `manylinux` wheels under the patched tracer?**
 *
 * The shipped runtime measured three dead ends for PyPI's wheels: stock glibc cannot start
 * under it (its `ld.so` reaches `set_robust_list` before any preload can arm a handler),
 * `gcompat` cannot carry the extensions, and the musl interpreter never looks for `-gnu.so`
 * names. The tracer rebuild is the surviving mechanism, and this is its workload: a glibc
 * CPython beside the musl image, run under the same patched proot, with `etc/resolv.conf`
 * carrying the app's nameservers so pip can reach PyPI at all.
 *
 * The root is the Ubuntu base plus python-build-standalone's CPython 3.12.16 - a stock glibc
 * build whose extension modules are compiled into `libpython`, so the only libraries it needs
 * are the base's own. Each package is installed by the interpreter's own pip and then imported:
 *
 * - `duckdb` - PyPI's `manylinux_2_28_aarch64` wheel, `cp312-abi3`;
 * - `scikit-learn` - `cp312` wheels for itself and its numpy/scipy dependencies.
 *
 * The pip log is the wheel-choice evidence: a `Downloading <dist>-<ver>-...manylinux...whl`
 * line is what "picks PyPI's manylinux wheel" looks like from inside the Space. The report is
 * written to `probe/glibc-python-report.txt` and logcat, and the test pins the measured
 * outcome: every step exits 0 and each wheel line names `manylinux`.
 *
 * ```
 * scripts/glibc-python-wheel-driver.sh
 * ```
 *
 * TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and on x86_64 nothing runs a cell.
 */
@RunWith(AndroidJUnit4::class)
class GlibcPythonWheelProbeDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val probeDir by lazy { File(requireNotNull(context.getExternalFilesDir(null)), "probe") }
  private val root by lazy { File(spacesDir(context), ROOT_NAME) }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no proot to run a glibc root",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
  }

  @Test(timeout = 1_200_000)
  fun aGlibcCpythonInstallsAndImportsManylinuxWheels() {
    val ubuntu = stagedVerified(UBUNTU_TARBALL, UBUNTU_SHA256)
    val cpython = stagedVerified(CPYTHON_TARBALL, CPYTHON_SHA256)
    val patchedLibs = patchedProotLibraryDir()
    root.deleteRecursively()
    root.mkdirs()
    extractTarGz(ubuntu, root)
    extractTarGz(cpython, root)
    val dns = androidDnsServerSource(context)
    val nameservers = dns.current()
    require(nameservers.isNotEmpty()) { "the device reported no DNS servers; pip cannot reach PyPI" }
    writeResolvConf(nameservers)
    SpaceDirs(root).ensure()
    val runtime = SpaceRuntime(root, patchedLibs, dns)
    val report = probeHeader(ubuntu, cpython, patchedLibs, nameservers)

    val banner = runIn(runtime, PYTHON + listOf("-c", BANNER), BANNER_TIMEOUT_SECONDS, report)
    val duckdbInstall = runIn(runtime, pipInstall("duckdb"), PIP_TIMEOUT_SECONDS, report)
    val duckdbImport = runIn(runtime, PYTHON + listOf("-c", DUCKDB_IMPORT), IMPORT_TIMEOUT_SECONDS, report)
    val sklearnInstall = runIn(runtime, pipInstall("scikit-learn"), PIP_TIMEOUT_SECONDS, report)
    val sklearnImport = runIn(runtime, PYTHON + listOf("-c", SKLEARN_IMPORT), IMPORT_TIMEOUT_SECONDS, report)
    if (sklearnImport.exitCode != 0) report.append(tracedImport(runtime, patchedLibs))

    File(probeDir, REPORT).writeText(report.toString())
    Log.i(PROBE_TAG, report.toString())
    assertInterpreterStarted(banner)
    assertWheelInstalled(duckdbInstall, "duckdb")
    assertEquals("import duckdb did not exit 0: ${duckdbImport.output}", 0, duckdbImport.exitCode)
    assertWheelInstalled(sklearnInstall, "scikit_learn")
    assertEquals("import scikit-learn did not exit 0: ${sklearnImport.output}", 0, sklearnImport.exitCode)
  }

  /**
   * The failed import once more under proot's own verbosity, because the plain run's empty
   * output and exit 255 is a signal death and only the tracer's log names it. `-v 1` is enough -
   * the SIGSYS lines are level 1 - and `PROOT_NO_SECCOMP` stays unset because the Space runs
   * proot's fast path; a SIGSYS stop is a signal stop either way.
   */
  private fun tracedImport(runtime: SpaceRuntime, patchedLibs: File): String {
    val dirs = SpaceDirs(root)
    val proot = ProotCommand(patchedLibs, root, dirs.inRoot(dirs.workspace))
    val argv = proot.argv(PYTHON + listOf("-c", SKLEARN_IMPORT)).toMutableList().apply {
      add(1, "-v")
      add(2, "1")
    }
    val process = ProcessBuilder(argv).apply {
      environment().clear()
      environment().putAll(proot.environment() + runtime.environment())
    }.redirectErrorStream(true).start()
    val exited = process.waitFor(IMPORT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    if (!exited) process.destroyForcibly()
    val lines = process.inputStream.bufferedReader().use { it.readText() }.lines()
    val sigsys = lines.filter { "SIGSYS" in it }.takeLast(TRACED_SIGSYS_LINES)
    return buildString {
      append("\n== the failing import under proot -v 1\n")
      append(sigsys.joinToString("\n", postfix = "\n"))
      append("== the trace's tail\n")
      append(lines.takeLast(TRACED_TAIL_LINES).joinToString("\n", postfix = "\n"))
    }
  }

  private fun stagedVerified(name: String, sha256: String): File {
    val file = staged(name)
    assertEquals(
      "the staged $name is not the pinned one; rerun scripts/glibc-python-wheel-driver.sh",
      sha256,
      sha256Hex(file),
    )
    return file
  }

  /** glibc's own resolver reads this file; the app's nameservers are what a Space's DNS is. */
  private fun writeResolvConf(nameservers: List<String>) {
    File(root, "etc/resolv.conf").writeText(nameservers.joinToString("\n", postfix = "\n") { "nameserver $it" })
  }

  private fun pipInstall(packageName: String): List<String> =
    PYTHON + listOf("-m", "pip", "install", "--no-cache-dir", packageName)

  private fun runIn(
    runtime: SpaceRuntime,
    command: List<String>,
    timeout: Long,
    report: StringBuilder,
  ): SpaceProcessRun {
    val run = runBounded(runtime, command, timeout)
    report.append("\n== ${run.command}\n-> exit=${run.exitCode}\n${run.output.trim()}\n")
    return run
  }

  private fun probeHeader(ubuntu: File, cpython: File, patchedLibs: File, nameservers: List<String>): StringBuilder =
    StringBuilder("== the probe\n")
      .append("device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} abis=${Build.SUPPORTED_ABIS.joinToString()}\n")
      .append("ubuntu=${ubuntu.name} bytes=${ubuntu.length()} sha256=${sha256Hex(ubuntu)}\n")
      .append("cpython=${cpython.name} bytes=${cpython.length()} sha256=${sha256Hex(cpython)}\n")
      .append("patched proot=${File(patchedLibs, ProotCommand.PROOT)}\n")
      .append("nameservers=${nameservers.joinToString(",")}\n")

  private fun staged(name: String): File = File(probeDir, name).also {
    require(it.isFile) { "missing '$it'; run scripts/glibc-python-wheel-driver.sh before this class" }
  }

  private fun assertInterpreterStarted(banner: SpaceProcessRun) {
    assertEquals("python3.12 did not start under the patched tracer: ${banner.output}", 0, banner.exitCode)
    assertTrue("the interpreter is not glibc's: ${banner.output}", "glibc" in banner.output)
  }

  /** pip's own log names the wheel it chose; `manylinux` in that line is the measurement. */
  private fun assertWheelInstalled(install: SpaceProcessRun, wheelPrefix: String) {
    assertEquals("pip install did not exit 0: ${install.output}", 0, install.exitCode)
    val wheel = install.output.lines().firstOrNull { wheelPrefix + "-" in it && ".whl" in it }
    assertTrue("pip named no $wheelPrefix wheel: ${install.output}", wheel != null && "manylinux" in wheel)
  }

  private companion object {
    const val PROBE_TAG = "JupydroidGlibcPython"
    const val ROOT_NAME = "glibc-python-probe"
    const val REPORT = "glibc-python-report.txt"

    /** Ubuntu's own `SHA256SUMS` for the arm64 base image, the same pin the other glibc probes use. */
    const val UBUNTU_TARBALL = "ubuntu-base-24.04.4-base-arm64.tar.gz"
    const val UBUNTU_SHA256 = "04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2"

    /**
     * python-build-standalone's CPython 3.12.16, release 20261110 - the sha256 is the release
     * asset's own digest. A stock glibc build; its modules are inside libpython, so the Ubuntu
     * base's libc is all it links against.
     */
    const val CPYTHON_TARBALL = "cpython-3.12.16+20261110-aarch64-unknown-linux-gnu-install_only.tar.gz"
    const val CPYTHON_SHA256 = "4792f81750282597fb2a3389fe18b945ed2ba6aad5e45ad406d122c58cb33081"

    val PYTHON = listOf("/python/bin/python3.12")
    const val BANNER = "import sys,platform;print(sys.version.split()[0]);print(platform.libc_ver())"
    const val DUCKDB_IMPORT = "import duckdb; print('duckdb', duckdb.__version__)"
    const val SKLEARN_IMPORT = "import sklearn; print('sklearn', sklearn.__version__)"

    const val BANNER_TIMEOUT_SECONDS = 120L
    const val PIP_TIMEOUT_SECONDS = 600L
    const val IMPORT_TIMEOUT_SECONDS = 300L
    const val TRACED_SIGSYS_LINES = 40
    const val TRACED_TAIL_LINES = 20
  }
}
