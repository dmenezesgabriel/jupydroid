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
 * **Does musl numpy's threaded BLAS survive the tracer delivering every post-startup
 * SIGSYS?** The refusal mechanism splits by libc (ADR-0166): a glibc process gets
 * `PROOT_SIGSYS_ENOSYS` - the tracer leaves ENOSYS standing and no signal is delivered,
 * because the measured glibc threaded death was the delivery round trip itself - while a
 * musl process keeps the handler path: `libsigsys_report.so` armed by `LD_PRELOAD`, every
 * refusal delivered, `tmp/sigsys.log` naming what was refused. This is the musl half's
 * regression: the shipped image, its own reporter, a matmul that makes OpenBLAS spawn its
 * pool, under the patched proot the glibc probes stage. A pass says delivered SIGSYS still
 * reaches a working handler on the interpreter that always had one.
 *
 * ```
 * scripts/glibc-python-wheel-driver.sh   # stages the patched proot pair first
 * am instrument -w -e class com.jupydroid.app.data.space.MuslBlasThreadsProbeDeviceTest \
 *   com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 *
 * TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and on x86_64 nothing runs a cell.
 */
@RunWith(AndroidJUnit4::class)
class MuslBlasThreadsProbeDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val probeDir by lazy { File(requireNotNull(context.getExternalFilesDir(null)), "probe") }
  private val root by lazy { PrivateRootUnderTest(context, ROOT_NAME) }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no proot to run a Space",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
  }

  @Test(timeout = 600_000)
  fun muslNumpyThreadsSurviveDeliveredSigsys() {
    root.extract()
    root.prepareForProot()
    SpaceDirs(root.dir).ensure()
    val patchedLibs = patchedProotLibraryDir()
    val runtime = SpaceRuntime(root.dir, patchedLibs, androidDnsServerSource(context))
    val report = StringBuilder("== the probe\n")
      .append("device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} abis=${Build.SUPPORTED_ABIS.joinToString()}\n")
      .append("patched proot=${File(patchedLibs, ProotCommand.PROOT)}\n")

    val banner = runBounded(runtime, listOf(PYTHON, "-c", BANNER), BANNER_TIMEOUT_SECONDS)
    val compute = runBounded(runtime, listOf(PYTHON, "-c", MATMUL), COMPUTE_TIMEOUT_SECONDS, THREADS_ENV)
    if (compute.exitCode != 0) {
      report.append(tracedCompute(runtime, patchedLibs))
    }
    report.append("\n== banner\n-> exit=${banner.exitCode}\n${banner.output.trim()}\n")
    report.append("\n== matmul under OpenBLAS threads\n-> exit=${compute.exitCode}\n${compute.output.trim()}\n")
    val sigsysLog = File(root.dir, "tmp/sigsys.log").takeIf { it.isFile }?.readText().orEmpty()
    report.append("\n== the SIGSYS handler's log (tmp/sigsys.log)\n$sigsysLog")

    File(probeDir, REPORT).writeText(report.toString())
    Log.i(PROBE_TAG, report.toString())
    assertEquals("musl python3 did not start under the patched tracer: ${banner.output}", 0, banner.exitCode)
    assertEquals(
      "musl numpy's threaded BLAS died the same way glibc's did: ${compute.output}",
      0,
      compute.exitCode,
    )
    assertTrue("mbind never reached the handler: $sigsysLog", "syscall=235" in sigsysLog)
  }

  /**
   * The failed matmul once more under proot's own verbosity, for the same reason the wheel
   * probe traces its failure: a signal death names no syscall until the tracer's log does.
   */
  private fun tracedCompute(runtime: SpaceRuntime, patchedLibs: File): String {
    val proot = ProotCommand(patchedLibs, root.dir, SpaceDirs(root.dir).inRoot(SpaceDirs(root.dir).workspace))
    val argv = proot.argv(listOf(PYTHON, "-c", MATMUL)).toMutableList().apply {
      add(1, "-v")
      add(2, "1")
    }
    val process = ProcessBuilder(argv).apply {
      environment().clear()
      environment().putAll(proot.environment() + runtime.environment() + THREADS_ENV)
    }.redirectErrorStream(true).start()
    val exited = process.waitFor(COMPUTE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    if (!exited) process.destroyForcibly()
    val lines = process.inputStream.bufferedReader().use { it.readText() }.lines()
    val events = lines.filter { "delivering" in it || "terminated" in it }
    return buildString {
      append("\n== the failing matmul under proot -v 1\n")
      append(events.joinToString("\n", postfix = "\n"))
      append("== the trace's tail\n")
      append(lines.takeLast(TRACED_TAIL_LINES).joinToString("\n", postfix = "\n"))
    }
  }

  private companion object {
    const val PROBE_TAG = "JupydroidMuslBlas"
    const val ROOT_NAME = "musl-blas-threads-probe"
    const val REPORT = "musl-blas-report.txt"
    const val PYTHON = "/usr/bin/python3"
    const val BANNER = "import sys;print(sys.version.split()[0])"
    const val MATMUL =
      "import numpy as np,time;" +
        "print('numpy',np.__version__);" +
        "a=np.random.rand(1200,1200);t=time.time();b=a@a;" +
        "print('matmul',round(time.time()-t,2),float(b[0,0]))"

    val THREADS_ENV = mapOf("OPENBLAS_VERBOSE" to "2")

    const val BANNER_TIMEOUT_SECONDS = 120L
    const val COMPUTE_TIMEOUT_SECONDS = 300L
    const val TRACED_TAIL_LINES = 20
  }
}
