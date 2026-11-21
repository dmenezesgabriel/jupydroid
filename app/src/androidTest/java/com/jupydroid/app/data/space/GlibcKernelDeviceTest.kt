package com.jupydroid.app.data.space

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * **The Space's kernel is the gnu interpreter, and it installs PyPI's released wheels** - the
 * run the glibc kernel exists for.
 *
 * A private root carries the shipped musl image plus the gnu overlay, seeded the way a Space's
 * start leaves it ([installOnStartOf]'s pieces, called by hand because this root is not one the
 * app's `SpaceServer` owns): the payload, its pip.conf, and the kernelspec naming
 * [GNU_INTERPRETER]. The server then runs through the same [spaceServerLauncher] composition the
 * app uses, on the patched proot a kernel's `PROOT_SIGSYS_ENOSYS` needs, and a real kernel is
 * driven through [RealKernelGateway]: where it is, what pip installs, and whether the import
 * resolves. The musl arm then runs the image's own `/usr/bin/python3` under the same patched
 * tracer: its user base is still `/pkgs`, and OpenBLAS's refused `mbind` still reaches the SIGSYS
 * handler - the split ADR-0166 requires, on one device in one run.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.GlibcKernelDeviceTest
 * ```
 *
 * TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and pip needs the tablet online.
 */
@RunWith(AndroidJUnit4::class)
class GlibcKernelDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val probeDir by lazy { File(requireNotNull(context.getExternalFilesDir(null)), "probe") }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no Space to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    check(spaceReady) { "the probe Space's image did not land" }
  }

  /** The kernel's own account: glibc, its user base, the proot marker, and no musl preload. */
  @Test(timeout = 300_000)
  fun theKernelIsTheGnuInterpreterWithItsOwnEnvironment() {
    val placed = Json.parseToJsonElement(runInKernel(ENV_PROBE)).jsonObject
    Log.i(PROBE_TAG, "glibc kernel env: $placed")

    val expected = mapOf(
      "executable" to GNU_INTERPRETER,
      "user_base" to "/pkgs-gnu",
      "sigsys_enosys" to "1",
      "ld_preload" to "",
    )
    expected.forEach { (key, value) ->
      assertEquals("$key", value, placed.getValue(key).jsonPrimitive.content)
    }
  }

  /** `%pip install duckdb` picks a manylinux wheel, and `import duckdb` resolves out of `/pkgs-gnu`. */
  @Test(timeout = 900_000)
  fun theKernelInstallsAndImportsAPublishedWheel() {
    val report = StringBuilder("== %pip install duckdb\n")
      .append(runInKernel("%pip install duckdb", PIP_TIMEOUT))
      .append("\n== import duckdb\n")
      .append(runInKernel(IMPORT_DUCKDB))
    Log.i(PROBE_TAG, report.toString())
    File(probeDir.apply { mkdirs() }, REPORT).writeText(report.toString())

    assertTrue("pip did not report a completed install:\n$report", "Successfully installed" in report)
    val version = Regex("""duckdb ([0-9]+\.[0-9]+\.[0-9]+)""").find(report)?.groupValues?.get(1)
    assertTrue("duckdb's version never printed:\n$report", version != null)
    assertTrue("duckdb resolved outside the gnu user base:\n$report", "/pkgs-gnu/" in report)
  }

  /**
   * The musl side of the split, unchanged: the image's own interpreter still answers, its user
   * base is still `/pkgs`, and a refused `mbind` under OpenBLAS threads still reaches the SIGSYS
   * handler - which the glibc kernel, marked `PROOT_SIGSYS_ENOSYS`, never sees.
   */
  @Test(timeout = 600_000)
  fun theMuslInterpreterKeepsItsSigsysHandler() {
    val runtime = SpaceRuntime(root.dir, patchedProotLibraryDir(), androidDnsServerSource(context))
    val banner = runBounded(runtime, listOf(MUSL_PYTHON, "-c", MUSL_BANNER), BANNER_TIMEOUT_SECONDS)
    val compute = runBounded(
      runtime,
      listOf(MUSL_PYTHON, "-c", MUSL_MATMUL),
      COMPUTE_TIMEOUT_SECONDS,
      MUSL_THREADS_ENV,
    )
    val sigsysLog = File(root.dir, "tmp/sigsys.log").takeIf { it.isFile }?.readText().orEmpty()
    val report = StringBuilder("== musl python3\n-> exit=${banner.exitCode}\n${banner.output.trim()}\n")
      .append("\n== musl numpy under OpenBLAS threads\n-> exit=${compute.exitCode}\n${compute.output.trim()}\n")
      .append("\n== tmp/sigsys.log\n$sigsysLog")
    File(probeDir.apply { mkdirs() }, REPORT).appendText("\n== the musl arm\n$report")
    Log.i(PROBE_TAG, report.toString())

    assertEquals("musl python3 did not start under the patched tracer: ${banner.output}", 0, banner.exitCode)
    assertTrue("/pkgs missing from the musl interpreter's answer: ${banner.output}", "/pkgs" in banner.output)
    assertEquals("musl numpy's threaded BLAS died: ${compute.output}", 0, compute.exitCode)
    assertTrue("mbind never reached the handler: $sigsysLog", "syscall=235" in sigsysLog)
  }

  /** Stdout of [code] run in a fresh kernel on the probe Space's server; the kernel is shut down after. */
  private fun runInKernel(code: String, timeout: Duration = KERNEL_TIMEOUT): String = runBlocking {
    val gateway = RealKernelGateway(
      KernelSockets(OkHttpKernelSocketOpener(server), OkHttpKernelLifecycle(server)),
    )
    val session = gateway.connect(SPACE, "glibc-kernel-probe.ipynb")
    try {
      withTimeout(timeout) {
        gateway.execute(session, "probe", code).takeWhile { it !is KernelEvent.Idle }
          .filterIsInstance<KernelEvent.Output>().map { it.output }.toList()
      }.joinToString("") { output ->
        when (output) {
          is CellOutput.Stream -> output.text
          is CellOutput.Error -> "${output.name}: ${output.value}\n"
          else -> ""
        }
      }
    } finally {
      gateway.shutdown(session)
    }
  }

  private companion object {
    const val PROBE_TAG = "JupydroidGlibcKernel"
    const val REPORT = "glibc-kernel-report.txt"

    /** A Space nothing but this test knows of, so no other suite's state can be mistaken for it. */
    val SPACE = Space(id = "glibc-kernel-probe", name = "glibc kernel probe", kernels = BASE_SPACE.kernels)

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val root by lazy { PrivateRootUnderTest(context, SPACE.id) }

    /**
     * The musl image plus what [installOnStartOf] lays over it, extracted once for the class:
     * the gnu payload, its pip.conf, and the kernelspec that names the gnu interpreter.
     */
    val spaceReady: Boolean by lazy {
      root.extract()
      root.prepareForProot()
      runBlocking {
        gnuSpaceImageInstaller(context).ensureInstalled(root.dir)
        GnuPipConfig.ensure(root.dir)
        SpaceKernelSpec.install(root.dir)
      }
      true
    }

    /** One launcher for the class: `JupyterServerLauncher.start` begins with `stop()`. */
    val launcher by lazy {
      spaceServerLauncher(
        context,
        settingsFor = { SpaceSettings() },
        defaultKernelFor = { DEFAULT_KERNEL_ID },
        nativeLibraryDir = patchedProotLibraryDir(),
      )
    }

    val server: ServerConnection by lazy {
      check(spaceReady) { "the probe Space's image did not land" }
      runBlocking { launcher.start(SPACE) }
    }

    /** Where the kernel says it is, as JSON on stdout. */
    val ENV_PROBE = """
      import json, os, site, sys
      print(json.dumps({
          "executable": sys.executable, "user_base": site.USER_BASE,
          "sigsys_enosys": os.environ.get("PROOT_SIGSYS_ENOSYS", "<missing>"),
          "ld_preload": os.environ.get("LD_PRELOAD", "<missing>"),
      }))
    """.trimIndent()

    val IMPORT_DUCKDB = "import duckdb; print('duckdb', duckdb.__version__, duckdb.__file__)"

    const val MUSL_PYTHON = "/usr/bin/python3"
    val MUSL_BANNER = "import site, sys; print(sys.version.split()[0], site.USER_BASE)"
    val MUSL_MATMUL =
      "import numpy as np;" +
        "a=np.random.rand(1200,1200);b=a@a;" +
        "print('matmul',float(b[0,0]))"
    val MUSL_THREADS_ENV = mapOf("OPENBLAS_VERBOSE" to "2")

    val KERNEL_TIMEOUT = 300.seconds
    val PIP_TIMEOUT = 600.seconds
    const val BANNER_TIMEOUT_SECONDS = 120L
    const val COMPUTE_TIMEOUT_SECONDS = 300L

    @JvmStatic
    @AfterClass
    fun stopTheServerAndRemoveTheProbeRoot() {
      runBlocking { launcher.stop() }
      root.delete()
    }
  }
}
