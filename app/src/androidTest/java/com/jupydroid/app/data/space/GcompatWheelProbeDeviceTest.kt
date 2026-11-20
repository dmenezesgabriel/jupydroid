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
 * **Can a musl Python load PyPI's `manylinux` wheels through Alpine's `gcompat`?**
 *
 * T1 measured that stock glibc cannot start under the shipped runtime at all: `ld.so` issues
 * `set_robust_list` before any preload constructor, so no handler can arm it. `gcompat` is the
 * other survivor: it supplies `libc.so.6`, `libm.so.6`, `libpthread.so.0`, `librt.so.1` and
 * `libcrypt.so.1` as symlinks to `libgcompat.so.0`, a musl-backed glibc ABI, so a manylinux
 * extension's `DT_NEEDED` names resolve without glibc's loader ever running.
 *
 * The probe installs `gcompat` into a private copy of base, then asks the Space's own `python3` to
 * import two wheels PyPI publishes for `manylinux_2_28_aarch64`:
 *
 * - `duckdb`'s `_duckdb.abi3.so` has no ABI tag in its name, so the musl interpreter does try to
 *   load it - and fails relocating `__res_init`, a glibc symbol `gcompat` does not provide.
 * - `scikit-learn`'s extensions are named `.cpython-312-aarch64-linux-gnu.so`, while this
 *   interpreter looks for `.cpython-312-aarch64-linux-musl.so` (`EXT_SUFFIX`, printed in the
 *   report). Python never tries to load them: the mismatch is the wheel's platform tag, not a
 *   `gcompat` gap.
 *
 * `joblib` and `threadpoolctl` are installed first as ordinary `py3` wheels, so a scikit-learn
 * failure is the extension's and not a missing pure-Python import. `apk`'s own database write
 * fails with `EACCES` in a private root, which the report records with proot's last syscalls; the
 * package's files land, which is what the import needs.
 *
 * The report is written to `probe/gcompat-report.txt` and logcat. The test pins what it measured:
 * `duckdb` fails on `__res_init`, `scikit-learn` fails on the `-gnu.so` name, and the interpreter's
 * suffix is the musl one.
 *
 * ```
 * scripts/gcompat-wheel-driver.sh
 * ```
 *
 * TABLET ONLY (SM-X216B): only arm64 ships a rootfs.
 */
@RunWith(AndroidJUnit4::class)
class GcompatWheelProbeDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val probeDir by lazy { File(requireNotNull(context.getExternalFilesDir(null)), "probe") }
  private val privateRoot by lazy { PrivateRootUnderTest(context, ROOT_NAME) }
  private val root by lazy { privateRoot.dir }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no Space to install gcompat into",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
  }

  @Test(timeout = 900_000)
  fun aManylinuxExtensionLoadsThroughGcompat() {
    privateRoot.extract()
    privateRoot.prepareForProot()
    SpaceDirs(root).ensure()
    val runtime = SpaceRuntime(root, File(context.applicationInfo.nativeLibraryDir), androidDnsServerSource(context))
    val report = StringBuilder("== the probe\n")
      .append("device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} abis=${Build.SUPPORTED_ABIS.joinToString()}\n")
    stageWheels()
    val install = installGcompat(runtime, report)
    val joblib = installPurePythonDeps(runtime, report)
    val suffix = extensionSuffix(runtime, report)
    val duckdb = importDuckdb(runtime, report)
    val sklearn = importSklearn(runtime, report)

    File(probeDir, REPORT).writeText(report.toString())
    Log.i(PROBE_TAG, report.toString())
    assertTrue("gcompat's files did not land: ${install.output}", File(root, "lib/libgcompat.so.0").isFile)
    assertEquals("the pure-Python deps did not install: ${joblib.output}", 0, joblib.exitCode)
    assertTrue("the Space's EXT_SUFFIX is not the musl one: ${suffix.output}", "-musl.so" in suffix.output)
    assertDuckdb(duckdb)
    assertSklearnMissingItsAbiTag(sklearn)
  }

  private fun installGcompat(runtime: SpaceRuntime, report: StringBuilder): SpaceProcessRun {
    val run = runBounded(runtime, listOf("/sbin/apk", "add", "--no-cache", "gcompat"), APK_TIMEOUT_SECONDS)
    report.append("\n== apk add --no-cache gcompat\n").append(detail(run))
    report.append(apkTrace(runtime))
    return run
  }

  /** The database write fails with EACCES in a private root; the last syscalls say where. */
  private fun apkTrace(runtime: SpaceRuntime): String {
    val proot = ProotCommand(File(context.applicationInfo.nativeLibraryDir), root, "/home/jupydroid")
    val argv = proot.argv(listOf("/sbin/apk", "add", "--no-cache", "gcompat")).toMutableList().apply {
      add(1, "-v")
      add(2, "9")
    }
    val process = ProcessBuilder(argv).apply {
      environment().clear()
      environment().putAll(
        proot.environment() + runtime.environment() + mapOf("PROOT_NO_SECCOMP" to "1"),
      )
    }.redirectErrorStream(true).start()
    process.waitFor(APK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    val trace = process.inputStream.bufferedReader().use { it.readText() }.lines()
    return "== the same apk under proot -v 9\n" +
      trace.filter { "proot info: vpid" in it }.takeLast(SYSCALL_LINES).joinToString("\n") + "\n"
  }

  private fun installPurePythonDeps(runtime: SpaceRuntime, report: StringBuilder): SpaceProcessRun {
    val command = listOf("python3", "-m", "pip", "install", "--no-cache-dir", "joblib", "threadpoolctl")
    val run = runBounded(runtime, command, PIP_TIMEOUT_SECONDS)
    report.append("\n== the pure-Python deps scikit-learn imports\n").append(detail(run))
    return run
  }

  /** What the interpreter looks for when it imports an extension, and what the wheel actually ships. */
  private fun extensionSuffix(runtime: SpaceRuntime, report: StringBuilder): SpaceProcessRun {
    val command = listOf("python3", "-c", "import sysconfig; print(sysconfig.get_config_var('EXT_SUFFIX'))")
    val run = runBounded(runtime, command, IMPORT_TIMEOUT_SECONDS)
    report.append("\n== the Space interpreter's extension suffix\n").append(detail(run))
    return run
  }

  /** As shipped first, then with the libdl alias glibc 2.34 made unnecessary but wheels still name. */
  private fun importDuckdb(runtime: SpaceRuntime, report: StringBuilder): DuckdbOutcome {
    runBounded(runtime, listOf("python3", "-m", "zipfile", "-e", "/wheels/$DUCKDB_WHEEL", "/wheels/duckdb"), UNZIP)
    val first = runBounded(runtime, listOf("python3", "-c", DUCKDB_IMPORT), IMPORT_TIMEOUT_SECONDS)
    report.append("\n== duckdb, gcompat as shipped\n").append(detail(first))
    if (first.exitCode == 0 || "libdl.so.2" !in first.output) return DuckdbOutcome(first, null)
    File(root, "lib/libdl.so.2").replaceWithSymbolicLink("libc.musl-aarch64.so.1")
    val retry = runBounded(runtime, listOf("python3", "-c", DUCKDB_IMPORT), IMPORT_TIMEOUT_SECONDS)
    report.append("\n== the same, with /lib/libdl.so.2 -> libc.musl-aarch64.so.1\n").append(detail(retry))
    return DuckdbOutcome(first, retry)
  }

  private fun importSklearn(runtime: SpaceRuntime, report: StringBuilder): SpaceProcessRun {
    runBounded(runtime, listOf("python3", "-m", "zipfile", "-e", "/wheels/$SKLEARN_WHEEL", "/wheels/sklearn"), UNZIP)
    val run = runBounded(runtime, listOf("python3", "-c", SKLEARN_IMPORT), IMPORT_TIMEOUT_SECONDS)
    report.append("\n== scikit-learn, gcompat as shipped\n").append(detail(run))
    return run
  }

  private fun assertDuckdb(outcome: DuckdbOutcome) {
    if (outcome.retry == null) {
      assertTrue(
        "duckdb did not fail on the symbol T2 measured: ${outcome.first.output}",
        "__res_init" in outcome.first.output,
      )
      return
    }
    assertTrue(
      "the first duckdb attempt did not fail on libdl.so.2: ${outcome.first.output}",
      "libdl.so.2" in outcome.first.output,
    )
    assertEquals("duckdb did not import through the libdl alias: ${outcome.retry.output}", 0, outcome.retry.exitCode)
  }

  /** A `cp312` manylinux wheel's extensions are named for glibc; a musl interpreter never looks for them. */
  private fun assertSklearnMissingItsAbiTag(sklearn: SpaceProcessRun) {
    assertTrue(
      "scikit-learn did not fail on its extension's name: ${sklearn.output}",
      "No module named 'sklearn.__check_build._check_build'" in sklearn.output,
    )
    assertTrue(
      "the wheel's extension is not named for glibc: ${sklearn.output}",
      "cpython-312-aarch64-linux-gnu.so" in sklearn.output,
    )
  }

  private fun stageWheels() {
    val wheels = File(root, "wheels").apply { mkdirs() }
    listOf(DUCKDB_WHEEL, SKLEARN_WHEEL).forEach { name ->
      val staged = File(probeDir, name)
      require(staged.isFile) { "missing '$staged'; run scripts/gcompat-wheel-driver.sh before this class" }
      staged.copyTo(File(wheels, name), overwrite = true)
    }
  }

  private fun detail(run: SpaceProcessRun): String = "${run.command} -> exit=${run.exitCode}\n${run.output.trim()}\n"

  private data class DuckdbOutcome(val first: SpaceProcessRun, val retry: SpaceProcessRun?)

  private companion object {
    const val PROBE_TAG = "JupydroidGcompat"
    const val ROOT_NAME = "gcompat-probe"
    const val REPORT = "gcompat-report.txt"

    /** The two wheels PyPI publishes for this Space's interpreter and architecture. */
    const val DUCKDB_WHEEL = "duckdb-2.0.1-cp312-abi3-manylinux_2_26_aarch64.manylinux_2_28_aarch64.whl"
    const val SKLEARN_WHEEL = "scikit_learn-1.9.1-cp312-cp312-manylinux_2_27_aarch64.manylinux_2_28_aarch64.whl"

    const val DUCKDB_IMPORT =
      "import sys; sys.path.insert(0, '/wheels/duckdb'); import duckdb; print('duckdb', duckdb.__version__, duckdb.__file__)"
    const val SKLEARN_IMPORT =
      "import sys; sys.path.insert(0, '/wheels/sklearn'); import sklearn; print('sklearn', sklearn.__version__)"

    const val APK_TIMEOUT_SECONDS = 300L
    const val PIP_TIMEOUT_SECONDS = 300L
    const val UNZIP = 300L
    const val IMPORT_TIMEOUT_SECONDS = 300L
    const val SYSCALL_LINES = 30
  }
}
