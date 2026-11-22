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
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * **Every package on the must-list installs from PyPI's wheels and runs its own quickstart** -
 * the demo the glibc kernel exists for, all thirty-six entries of it.
 *
 * A private root carries the shipped musl image plus the gnu overlay, seeded the way a Space's
 * start leaves it; the server runs through the same [spaceServerLauncher] composition the app
 * uses, on the **shipped** proot pair in the app's `nativeLibraryDir` - the patched build
 * `stage-app-artifacts.sh` now stages, not a test-injected copy. Each package gets its own kernel
 * session - its own group of cells, `%pip install` then the docs' snippet - because the tablet's
 * 3.5 GB of RAM cannot hold torch and TensorFlow in one interpreter. A package whose docs example
 * cannot work in a Space (no JVM, no system library) carries [MustListPackage.limitation] and the
 * run asserts the failure's own signature; anything else must match [MustListPackage.success].
 * After the first group the server is killed and started again, and numpy and pandas must still
 * import with no reinstall - the relaunch the demo promises.
 *
 * The report lands in the probe directory for `scripts/must-list-demo-driver.sh` to pull.
 *
 * TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and pip needs the tablet online.
 */
@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class MustListDemoDeviceTest {
  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no Space to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    check(spaceReady) { "the demo Space's image did not land" }
  }

  @Test(timeout = 3_600_000)
  fun g1_foundations() = runGroup(MustListDemoManifest.GROUPS[0])

  /** Kill and relaunch after the first group: what it installed must still import, unreinstalled. */
  @Test(timeout = 600_000)
  fun g1b_relaunchKeepsTheInstalls() = runBlocking {
    restartServer()
    val out = withSession("relaunch") { gateway, session ->
      execute(gateway, session, "import numpy, pandas; print(numpy.__version__, pandas.__version__)", SNIPPET_TIMEOUT)
    }
    report("== relaunch\n$out")
    assertTrue("installed packages did not survive the relaunch:\n$out", out.contains(Regex("[0-9]+\\.[0-9]+")))
  }

  @Test(timeout = 4_800_000)
  fun g2_visualiseAndModel() = runGroup(MustListDemoManifest.GROUPS[1])

  @Test(timeout = 7_200_000)
  fun g3_deepLearning() = runGroup(MustListDemoManifest.GROUPS[2])

  /** TensorFlow and Keras in a root of their own: torch's CUDA stack leaves no room to share. */
  @Test(timeout = 3_600_000)
  fun g3b_deepLearningFrameworks() = runGroup(MustListDemoManifest.GROUPS[3])

  @Test(timeout = 5_400_000)
  fun g4_languageAndVision() = runGroup(MustListDemoManifest.GROUPS[4])

  @Test(timeout = 7_200_000)
  fun g5_platformAndServing() = runGroup(MustListDemoManifest.GROUPS[5])

  /** One group: every package installed then snippet'd in its own kernel, failures named and collected. */
  private fun runGroup(group: MustListGroup) {
    val failures = mutableListOf<String>()
    group.packages.forEach { pkg ->
      // A package that hangs or crashes its kernel must not hide the rest of the group's findings.
      val outcome = runCatching { runPackage(pkg) }
        .getOrElse { "ERROR ${describe(it)}\nFAIL\n" }
      report("== ${group.name}/${pkg.pipName}\n$outcome")
      if (!outcome.trimEnd().endsWith("PASS")) {
        failures += "${pkg.pipName}: ${outcome.lineSequence().last()}"
      }
    }
    assertTrue("${group.name} failures:\n${failures.joinToString("\n")}", failures.isEmpty())
  }

  /** An exception's class, message and first frames - enough to tell a missing JVM from a bad wheel. */
  private fun describe(failure: Throwable): String {
    val frames = failure.stackTraceToString().lineSequence().take(STACK_FRAMES).joinToString("\n")
    return "${failure::class.simpleName}: ${failure.message}\n$frames"
  }

  /** `%pip install`, then the snippet, in one kernel; the section ends with PASS or the miss. */
  private fun runPackage(pkg: MustListPackage): String = runBlocking {
    val section = StringBuilder()
    withSession(pkg.pipName) { gateway, session ->
      section.append("-- %pip install ${pkg.pipName}\n")
      val install = execute(gateway, session, "%pip install ${pkg.pipName}", INSTALL_TIMEOUT)
      section.append(install).append("\n-- snippet\n")
      val snippet = execute(gateway, session, pkg.snippet, SNIPPET_TIMEOUT)
      section.append(snippet).append('\n')
      // A limitation's signature can live in either cell - "No matching distribution" is pip's,
      // "JAVA_HOME is not set" is the snippet's - so it is matched against both.
      val haystack = if (pkg.limitation == null) snippet else install + snippet
      val expected = pkg.limitation ?: pkg.success
      section.append(if (expected.containsMatchIn(haystack)) "PASS\n" else "FAIL: expected $expected\n")
    }
    section.toString()
  }

  /** Runs [block] against one kernel session for [notebook]; the kernel is shut down after. */
  private suspend fun <T> withSession(notebook: String, block: suspend (KernelGateway, KernelSession) -> T): T {
    val gateway = RealKernelGateway(
      KernelSockets(OkHttpKernelSocketOpener(server()), OkHttpKernelLifecycle(server())),
    )
    val session = connectWithRetry(gateway, notebook)
    try {
      return block(gateway, session)
    } finally {
      gateway.shutdown(session)
    }
  }

  /**
   * The first kernel a fresh root boots can outrun a REST client's read timeout while proot and
   * the glibc interpreter come up cold; the kernel itself keeps booting server-side, so asking
   * again lands on it already running. Three tries cover the coldest start the tablet produces.
   */
  private suspend fun connectWithRetry(gateway: KernelGateway, notebook: String): KernelSession {
    var last: Exception? = null
    repeat(CONNECT_ATTEMPTS) {
      try {
        return gateway.connect(SPACE, "$notebook.ipynb")
      } catch (e: java.io.IOException) {
        last = e
        delay(RETRY_DELAY)
      }
    }
    throw last ?: IllegalStateException("connect to $notebook failed without an exception")
  }

  /** A cell's combined output - stream text, errors, and which display_data mimetypes arrived. */
  private suspend fun execute(
    gateway: KernelGateway,
    session: KernelSession,
    code: String,
    timeout: Duration,
  ): String = withTimeout(timeout) {
    gateway.execute(session, "cell", code).takeWhile { it !is KernelEvent.Idle }
      .filterIsInstance<KernelEvent.Output>().map { it.output }.toList()
  }.joinToString("") { output ->
    when (output) {
      is CellOutput.Stream -> output.text
      is CellOutput.Error -> "${output.name}: ${output.value}\n"
      is CellOutput.DisplayData -> output.mimeBundle.keys.joinToString(",", "[display ", "]\n")
      else -> ""
    }
  }

  private fun report(text: String) {
    File(probeDir().apply { mkdirs() }, REPORT).appendText("$text\n")
    Log.i(PROBE_TAG, text)
  }

  private companion object {
    const val PROBE_TAG = "JupydroidMustList"
    const val REPORT = "must-list-report.txt"

    /** The Space the demo runs in: a name no user Space carries, so nothing else can be mistaken for it. */
    val SPACE = Space(id = "must-list-demo", name = "must list demo", kernels = BASE_SPACE.kernels)

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val root by lazy { PrivateRootUnderTest(context, SPACE.id) }

    fun probeDir(): File = File(requireNotNull(context.getExternalFilesDir(null)), "probe")

    /** The musl image plus what `installOnStartOf` lays over it - the same seed a real Space gets. */
    val spaceReady: Boolean by lazy {
      File(probeDir(), REPORT).delete() // a fresh report per instrumentation run
      root.extract()
      root.prepareForProot()
      runBlocking {
        gnuSpaceImageInstaller(context).ensureInstalled(root.dir)
        GnuPipConfig.ensure(root.dir)
        SpaceKernelSpec.install(root.dir)
        // pip's wheel cache would hold every download a second time beside its install - on a
        // tablet whose 48 GB is mostly the person's data, torch's CUDA stack alone fills it.
        // The probe root's pip keeps no cache; the installs themselves are unchanged.
        File(root.dir, "opt/gnu/python/pip.conf").appendText("[global]\nno-cache-dir = true\n")
      }
      true
    }

    /** One launcher for the class: `JupyterServerLauncher.start` begins with `stop()`. */
    val launcher by lazy {
      spaceServerLauncher(
        context,
        settingsFor = { SpaceSettings() },
        defaultKernelFor = { DEFAULT_KERNEL_ID },
      )
    }

    /** The server, replaceable: the foundations group kills and restarts it mid-run. */
    var serverConnection: ServerConnection? = null
      private set

    fun server(): ServerConnection = serverConnection
      ?: run {
        check(spaceReady) { "the demo Space's image did not land" }
        runBlocking { launcher.start(SPACE) }.also { serverConnection = it }
      }

    /** `JupyterServerLauncher.start` begins with `stop()`, so this is the whole relaunch. */
    fun restartServer() {
      serverConnection = runBlocking { launcher.start(SPACE) }
    }

    val INSTALL_TIMEOUT = 45.minutes
    val SNIPPET_TIMEOUT = 20.minutes
    const val CONNECT_ATTEMPTS = 3
    const val STACK_FRAMES = 4
    val RETRY_DELAY = 5.seconds

    @JvmStatic
    @AfterClass
    fun stopTheServerAndRemoveTheProbeRoot() {
      runBlocking { launcher.stop() }
      root.delete()
    }
  }
}
