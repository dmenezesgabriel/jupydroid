package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * **Does an existing Space gain the gnu interpreter additively?** The payload ships as its
 * own asset (`spaces/gnu/`), and [SpaceGnuImageInstaller.ensureInstalled] is what a Space's
 * start runs after its image is in place (ADR-0166). This probe builds the "existing Space"
 * the way one actually exists - the shipped musl rootfs extracted, its own `pkgs/` and home
 * already holding the user's state - then runs the real installer against the real asset and
 * measures what landed: the payload's interpreter starting as glibc under the patched tracer
 * with `PROOT_SIGSYS_ENOSYS` (the ENOSYS-only answer, since delivering `SIGSYS` into glibc is
 * fatal under threads), the kernel stack it carries importing, the user site on `sys.path`,
 * the musl interpreter still answering beside it, and a second install being a no-op.
 *
 * ```
 * scripts/gnu-overlay-driver.sh
 * ```
 *
 * TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and on x86_64 nothing runs a cell.
 */
@RunWith(AndroidJUnit4::class)
class GnuOverlayProbeDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val probeDir by lazy { File(requireNotNull(context.getExternalFilesDir(null)), "probe") }
  private val dns = androidDnsServerSource(context)
  private val root = PrivateRootUnderTest(context, ROOT_NAME)

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no Space to overlay",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
  }

  @Test(timeout = 900_000)
  fun anExistingSpaceGainsTheGnuInterpreterAdditively() = runBlocking {
    val patchedLibs = patchedProotLibraryDir()
    root.extract()
    root.prepareForProot()
    val spaceDir = root.dir
    // The state an existing Space actually has: packages the user installed and files they wrote.
    File(spaceDir, "pkgs/user-site-package").apply { parentFile?.mkdirs() }.writeText("the Space's own")
    File(spaceDir, "home/jupydroid").mkdirs()
    File(spaceDir, "home/jupydroid/notes.md").writeText("# Thesis")
    SpaceDirs(spaceDir).ensure()

    val installer = SpaceGnuImageInstaller(
      archive = ArchiveSource { context.assets.open(GNU_ASSET) },
    )
    val first = installer.ensureInstalled(spaceDir)
    val second = installer.ensureInstalled(spaceDir)

    val report = StringBuilder("== the probe\n")
      .append("device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} abis=${Build.SUPPORTED_ABIS.joinToString()}\n")
      .append("gnu asset=${context.assets.open(GNU_ASSET).use { it.available() }}B\n")
      .append("patched proot=${File(patchedLibs, ProotCommand.PROOT)}\n")
      .append("ensureInstalled: first=$first second=$second\n")
      .append(
        "payload files: interpreter=${File(spaceDir, GNU_INTERPRETER).isFile} " +
          "loader=${File(spaceDir, GNU_LOADER).isFile} stamp=${File(spaceDir, GNU_STAMP).readText().trim()}\n",
      )
      .append(
        "sentinels: pkgs=${File(spaceDir, "pkgs/user-site-package").readText()} " +
          "home=${File(spaceDir, "home/jupydroid/notes.md").readText()}\n",
      )

    val gnuRuntime = SpaceRuntime(
      spaceDir,
      patchedLibs,
      dns,
      variables = mapOf("PROOT_SIGSYS_ENOSYS" to "1"),
    )
    val muslRuntime = SpaceRuntime(spaceDir, patchedLibs, dns)

    // Every glibc command runs the environment a second interpreter actually gets: the musl
    // LD_PRELOAD cleared (its libc.musl would load into the glibc process) and
    // PROOT_SIGSYS_ENOSYS set on the runtime for the tracer's ENOSYS-only answer.
    val banner = runIn(gnuRuntime, GNU_PYTHON + "-c" + BANNER, BANNER_TIMEOUT_SECONDS, report, NO_PRELOAD)
    val kernelStack = runIn(
      gnuRuntime,
      GNU_PYTHON + "-c" + KERNEL_IMPORTS,
      IMPORT_TIMEOUT_SECONDS,
      report,
      NO_PRELOAD,
    )
    val userSite = runIn(gnuRuntime, GNU_PYTHON + "-c" + USER_SITE_CHECK, SEAM_TIMEOUT_SECONDS, report, NO_PRELOAD)
    val muslBanner = runIn(muslRuntime, listOf("/usr/bin/python3", "-c", BANNER), BANNER_TIMEOUT_SECONDS, report)

    File(probeDir, REPORT).writeText(report.toString())
    Log.i(PROBE_TAG, report.toString())

    assertTrue("the payload did not extract: first=$first", first > 0)
    assertEquals("a second ensureInstalled must be a no-op: $second", 0, second)
    assertTrue("the gnu interpreter is not in place", File(spaceDir, GNU_INTERPRETER).isFile)
    assertEquals(
      "the Space's own package must survive the overlay",
      "the Space's own",
      File(spaceDir, "pkgs/user-site-package").readText(),
    )
    assertEquals("the glibc banner did not exit 0: ${banner.output}", 0, banner.exitCode)
    assertTrue("the payload's interpreter is not glibc's: ${banner.output}", "glibc" in banner.output)
    assertEquals("the kernel stack did not import: ${kernelStack.output}", 0, kernelStack.exitCode)
    assertTrue(
      "the user site is not on the glibc interpreter's sys.path: ${userSite.output}",
      "True" in userSite.output,
    )
    assertEquals(
      "the musl interpreter did not survive the overlay: ${muslBanner.output}",
      0,
      muslBanner.exitCode,
    )
  }

  private fun runIn(
    runtime: SpaceRuntime,
    command: List<String>,
    timeoutSeconds: Long,
    report: StringBuilder,
    extraEnv: Map<String, String> = emptyMap(),
  ): SpaceProcessRun {
    val run = runBounded(runtime, command, timeoutSeconds, extraEnv)
    report.append("\n== ${run.command}  -> exit=${run.exitCode}\n${run.output.trim()}\n")
    return run
  }

  private companion object {
    const val PROBE_TAG = "JupydroidGnuOverlay"
    const val ROOT_NAME = "gnu-overlay-probe"
    const val REPORT = "gnu-overlay-report.txt"
    const val GNU_ASSET = "spaces/gnu/gnu-arm64-v8a.tzst"
    const val GNU_INTERPRETER = "opt/gnu/python/bin/python3.12"
    const val GNU_LOADER = "lib/ld-linux-aarch64.so.1"
    const val GNU_STAMP = ".jupydroid/gnu-version"

    val GNU_PYTHON = listOf("/opt/gnu/python/bin/python3.12")
    val NO_PRELOAD = mapOf("LD_PRELOAD" to "")

    const val BANNER = "import sys,platform;print(sys.version.split()[0]);print(platform.libc_ver())"
    const val KERNEL_IMPORTS = "import ipykernel,jupydroid_kernel;print('ipykernel',ipykernel.__version__)"
    const val USER_SITE_CHECK =
      "import site,sys;print('user-site-on-path', site.getusersitepackages() in sys.path)"

    const val BANNER_TIMEOUT_SECONDS = 120L
    const val IMPORT_TIMEOUT_SECONDS = 300L
    const val SEAM_TIMEOUT_SECONDS = 120L
  }
}
