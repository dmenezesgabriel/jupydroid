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
import java.nio.file.Files

/**
 * **The whole glibc image against the second interpreter - the measurements the choice needs.**
 *
 * Two shapes are built on the tablet and measured the same way: the *whole glibc image* (the
 * Ubuntu base plus python-build-standalone's CPython, everything glibc) and the *second
 * interpreter* (the shipped Alpine rootfs untouched, with the same CPython at
 * `/opt/gnu/python` and the glibc runtime files it links copied into
 * `usr/lib/aarch64-linux-gnu`, its loader at `/lib/ld-linux-aarch64.so.1` where the
 * interpreter's `PT_INTERP` looks). For each: staged and extracted bytes, untar seconds,
 * `pip install duckdb` and `pip install ipykernel` wall times - the package-install latency a
 * user feels, and `ipykernel` because it is what makes an interpreter a kernel - and the
 * imports that prove the wheels loaded.
 *
 * Then the seams. The whole image answers what it did to `apk`, `busybox`, `usr/pip.conf`,
 * the usersite `.pth` and `sh`. The second interpreter answers the same questions from
 * Alpine's side - `apk --version`, the musl `/usr/bin/python3`, `usr/pip.conf`, the `.pth`,
 * `import ipykernel` under musl - and from the glibc side: its prefix, its user site, its
 * pip config, and what the Space's musl-built `LD_PRELOAD` does to it (the raw banner runs
 * with the Space's own environment; every measured command clears `LD_PRELOAD`, because a
 * second interpreter cannot share the first's preloads - a glibc loader handed musl builds
 * finds `libc.musl-aarch64.so.1` sitting in `/lib` and loads a second libc into the process).
 *
 * ```
 * scripts/glibc-image-shapes-driver.sh
 * ```
 *
 * TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and on x86_64 nothing runs a cell.
 */
@RunWith(AndroidJUnit4::class)
class GlibcImageShapesProbeDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val probeDir by lazy { File(requireNotNull(context.getExternalFilesDir(null)), "probe") }
  private val dns = androidDnsServerSource(context)

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no proot to run either shape",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
  }

  @Test(timeout = 1_800_000)
  fun theWholeGlibcImageAgainstTheSecondInterpreter() = runBlocking {
    val ubuntu = stagedVerified(UBUNTU_TARBALL, UBUNTU_SHA256)
    val cpython = stagedVerified(CPYTHON_TARBALL, CPYTHON_SHA256)
    val patchedLibs = patchedProotLibraryDir()
    val nameservers = dns.current()
    require(nameservers.isNotEmpty()) { "the device reported no DNS servers; pip cannot reach PyPI" }

    val report = probeHeader(ubuntu, cpython, patchedLibs, nameservers)

    val wholeRoot = File(spacesDir(context), WHOLE_ROOT)
    report.append(extractWholeImage(wholeRoot, ubuntu, cpython))
    writeResolvConf(wholeRoot, nameservers)
    SpacePipConfig.ensure(wholeRoot)
    SpaceDirs(wholeRoot).ensure()
    File(wholeRoot, "usr/bin/python3").replaceWithSymbolicLink("/python/bin/python3.12")
    val wholeRuntime = SpaceRuntime(wholeRoot, patchedLibs, dns)
    val whole = measureInterpreter(report, "whole glibc image", wholeRuntime, WHOLE_PYTHON)
    wholeImageSeams(wholeRuntime, report)

    val secondRoot = File(spacesDir(context), SECOND_ROOT)
    report.append(extractSecondInterpreter(secondRoot, cpython, wholeRoot))
    writeResolvConf(secondRoot, nameservers)
    SpacePipConfig.ensure(secondRoot)
    SpaceDirs(secondRoot).ensure()
    val secondRuntime = SpaceRuntime(secondRoot, patchedLibs, dns)
    val second = measureInterpreter(report, "second interpreter", secondRuntime, SECOND_PYTHON, NO_PRELOAD)
    val muslSeams = muslSeams(secondRuntime, report)
    gnuSeams(secondRuntime, report)

    File(probeDir, REPORT).writeText(report.toString())
    Log.i(PROBE_TAG, report.toString())

    assertShapeRan(whole, "whole glibc image")
    assertShapeRan(second, "second interpreter")
    assertMuslSeamsIntact(muslSeams)
  }

  /** The whole image: Ubuntu's userspace with the CPython tree inside it, `/python` its prefix. */
  private fun extractWholeImage(root: File, ubuntu: File, cpython: File): String {
    root.deleteRecursively()
    root.mkdirs()
    val start = System.nanoTime()
    extractTarGz(ubuntu, root)
    extractTarGz(cpython, root)
    val staged = ubuntu.length() + cpython.length()
    return "whole image: staged=${staged}B extracted=${extractedBytes(root)}B " +
      "untar=${"%.1f".format(elapsedSeconds(start))}s\n"
  }

  /**
   * The second interpreter: the shipped Alpine image as it ships (read straight from the app's
   * assets, the same bytes `SpaceImageInstaller` would extract), the CPython tree under
   * `/opt/gnu`, and the glibc runtime files copied out of the whole image's own extraction -
   * the same Ubuntu bytes, at the multiarch path glibc searches by default. `libstdc++.so.6`
   * arrives as a regular file: `copyTo` follows the symlink, which is all the loader needs.
   */
  private suspend fun extractSecondInterpreter(root: File, cpython: File, wholeRoot: File): String {
    root.deleteRecursively()
    val start = System.nanoTime()
    TarZstdExtractor().extract(ArchiveSource { context.assets.open(ROOTFS_ASSET) }, root)
    extractTarGz(cpython, File(root, OPT_GNU))
    copyGnuLibs(wholeRoot, root)
    placeLoader(root)
    val alpineBytes = context.assets.open(ROOTFS_ASSET).use { it.available().toLong() }
    return "second interpreter: alpine=${alpineBytes}B cpython=${cpython.length()}B " +
      "extracted=${extractedBytes(root)}B untar=${"%.1f".format(elapsedSeconds(start))}s\n"
  }

  private fun copyGnuLibs(wholeRoot: File, secondRoot: File) {
    val source = File(wholeRoot, "usr/lib/aarch64-linux-gnu")
    val target = File(secondRoot, "usr/lib/aarch64-linux-gnu").apply { mkdirs() }
    GNU_LIB_NAMES.forEach { name -> copyLib(File(source, name), File(target, name)) }
  }

  /**
   * One runtime file, byte-faithful: `copyTo` drops the mode, and the loader's `PT_INTERP` path
   * goes through `open_exec`, which refuses a file without the execute bit; a symlink stays a
   * symlink so `libstdc++.so.6` keeps naming its `soname` target.
   */
  private fun copyLib(from: File, to: File) {
    if (Files.isSymbolicLink(from.toPath())) {
      to.replaceWithSymbolicLink(Files.readSymbolicLink(from.toPath()).toString())
      return
    }
    from.copyTo(to, overwrite = true)
    to.setExecutable(true, false)
  }

  private fun placeLoader(root: File) {
    File(root, "lib").mkdirs()
    copyLib(
      File(root, "usr/lib/aarch64-linux-gnu/ld-linux-aarch64.so.1"),
      File(root, "lib/ld-linux-aarch64.so.1"),
    )
  }

  /** Banner, both package installs and both imports, each timed - the interpreter's whole story. */
  private fun measureInterpreter(
    report: StringBuilder,
    label: String,
    runtime: SpaceRuntime,
    python: List<String>,
    extraEnv: Map<String, String> = emptyMap(),
  ): InterpreterRun {
    report.append("\n== $label\n")
    return InterpreterRun(
      banner = runIn(runtime, python + "-c" + BANNER, BANNER_TIMEOUT_SECONDS, report, extraEnv),
      duckdbInstall = runIn(
        runtime,
        python + pipCommand("install", "--no-cache-dir", "duckdb"),
        PIP_TIMEOUT_SECONDS,
        report,
        extraEnv,
      ),
      duckdbImport = runIn(runtime, python + "-c" + DUCKDB_IMPORT, IMPORT_TIMEOUT_SECONDS, report, extraEnv),
      ipykernelInstall = runIn(
        runtime,
        python + pipCommand("install", "--no-cache-dir", "ipykernel"),
        PIP_TIMEOUT_SECONDS,
        report,
        extraEnv,
      ),
      ipykernelImport = runIn(runtime, python + "-c" + IPYKERNEL_IMPORT, IMPORT_TIMEOUT_SECONDS, report, extraEnv),
    )
  }

  /** What the whole image cost Alpine's seams: `apk`, `busybox`, `pip.conf`, the `.pth`, `sh`. */
  private fun wholeImageSeams(runtime: SpaceRuntime, report: StringBuilder) {
    report.append("\n== whole image: what Alpine's seams became\n")
    runIn(runtime, listOf("/bin/sh", "-c", "command -v apk || echo no-apk"), SEAM_TIMEOUT_SECONDS, report)
    runIn(runtime, listOf("/bin/sh", "-c", "command -v busybox || echo no-busybox"), SEAM_TIMEOUT_SECONDS, report)
    runIn(runtime, listOf("/bin/sh", "-c", "ls -l /bin/sh"), SEAM_TIMEOUT_SECONDS, report)
    runIn(runtime, WHOLE_PYTHON + "-c" + PTH_GLOB, SEAM_TIMEOUT_SECONDS, report)
    runIn(runtime, WHOLE_PYTHON + pipCommand("config", "list"), SEAM_TIMEOUT_SECONDS, report)
    runIn(runtime, WHOLE_PYTHON + pipCommand("show", "duckdb"), SEAM_TIMEOUT_SECONDS, report)
  }

  /** Alpine's side after the glibc interpreter landed: `apk`, musl python, `pip.conf`, `.pth`. */
  private fun muslSeams(runtime: SpaceRuntime, report: StringBuilder): MuslSeams {
    report.append("\n== second interpreter: Alpine's seams, checked\n")
    return MuslSeams(
      apk = runIn(runtime, listOf("/bin/sh", "-c", "apk --version"), SEAM_TIMEOUT_SECONDS, report),
      python = runIn(runtime, listOf("/usr/bin/python3", "--version"), SEAM_TIMEOUT_SECONDS, report),
      pipConf = runIn(runtime, listOf("/bin/sh", "-c", "cat /usr/pip.conf"), SEAM_TIMEOUT_SECONDS, report),
      userSite = runIn(runtime, MUSL_PYTHON + "-c" + USER_SITE_CHECK, SEAM_TIMEOUT_SECONDS, report),
      ipykernel = runIn(runtime, MUSL_PYTHON + "-c" + "import ipykernel", SEAM_TIMEOUT_SECONDS, report),
    )
  }

  /**
   * The glibc side's own view. The first run is the raw one - the Space's environment
   * untouched, musl `LD_PRELOAD` included - because whether the glibc loader survives being
   * handed musl builds is itself a measurement; the rest run with `LD_PRELOAD` cleared, the
   * environment a second interpreter would actually get.
   */
  private fun gnuSeams(runtime: SpaceRuntime, report: StringBuilder) {
    report.append("\n== second interpreter: the glibc side's own view\n")
    runIn(runtime, SECOND_PYTHON + "-c" + BANNER, BANNER_TIMEOUT_SECONDS, report)
    runIn(runtime, SECOND_PYTHON + "-c" + "import sys;print(sys.prefix)", SEAM_TIMEOUT_SECONDS, report, NO_PRELOAD)
    runIn(runtime, SECOND_PYTHON + "-c" + USER_SITE_CHECK, SEAM_TIMEOUT_SECONDS, report, NO_PRELOAD)
    runIn(runtime, SECOND_PYTHON + pipCommand("config", "list"), SEAM_TIMEOUT_SECONDS, report, NO_PRELOAD)
    runIn(runtime, SECOND_PYTHON + pipCommand("show", "duckdb"), SEAM_TIMEOUT_SECONDS, report, NO_PRELOAD)
  }

  private fun runIn(
    runtime: SpaceRuntime,
    command: List<String>,
    timeout: Long,
    report: StringBuilder,
    extraEnv: Map<String, String> = emptyMap(),
  ): SpaceProcessRun {
    val start = System.nanoTime()
    val run = runBounded(runtime, command, timeout, extraEnv)
    val seconds = elapsedSeconds(start)
    report.append("\n== ${run.command}  (${"%.1f".format(seconds)}s)\n-> exit=${run.exitCode}\n${run.output.trim()}\n")
    return run
  }

  /** The bytes a Space's rootfs occupies: regular files only, symlinked directories not doubled. */
  private fun extractedBytes(dir: File): Long {
    var total = 0L
    dir.listFiles()?.forEach { child ->
      when {
        Files.isSymbolicLink(child.toPath()) -> Unit
        child.isDirectory -> total += extractedBytes(child)
        child.isFile -> total += child.length()
      }
    }
    return total
  }

  private fun elapsedSeconds(startNanos: Long): Double = (System.nanoTime() - startNanos) / 1_000_000_000.0

  private fun writeResolvConf(root: File, nameservers: List<String>) {
    File(root, "etc/resolv.conf").writeText(nameservers.joinToString("\n", postfix = "\n") { "nameserver $it" })
  }

  private fun pipCommand(vararg args: String): List<String> = listOf("-m", "pip") + args

  private fun stagedVerified(name: String, sha256: String): File {
    val file = staged(name)
    assertEquals(
      "the staged $name is not the pinned one; rerun scripts/glibc-image-shapes-driver.sh",
      sha256,
      sha256Hex(file),
    )
    return file
  }

  private fun staged(name: String): File = File(probeDir, name).also {
    require(it.isFile) { "missing '$it'; run scripts/glibc-image-shapes-driver.sh before this class" }
  }

  private fun probeHeader(ubuntu: File, cpython: File, patchedLibs: File, nameservers: List<String>): StringBuilder =
    StringBuilder("== the probe\n")
      .append("device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} abis=${Build.SUPPORTED_ABIS.joinToString()}\n")
      .append("ubuntu=${ubuntu.length()}B sha256=${sha256Hex(ubuntu)}\n")
      .append("cpython=${cpython.length()}B sha256=${sha256Hex(cpython)}\n")
      .append("patched proot=${File(patchedLibs, ProotCommand.PROOT)}\n")
      .append("nameservers=${nameservers.joinToString(",")}\n")

  private fun assertShapeRan(run: InterpreterRun, label: String) {
    assertEquals("$label: the interpreter did not start: ${run.banner.output}", 0, run.banner.exitCode)
    assertTrue("$label: the interpreter is not glibc's: ${run.banner.output}", "glibc" in run.banner.output)
    assertEquals("$label: pip install duckdb failed: ${run.duckdbInstall.output}", 0, run.duckdbInstall.exitCode)
    assertEquals("$label: import duckdb failed: ${run.duckdbImport.output}", 0, run.duckdbImport.exitCode)
    assertEquals(
      "$label: pip install ipykernel failed: ${run.ipykernelInstall.output}",
      0,
      run.ipykernelInstall.exitCode,
    )
    assertEquals("$label: import ipykernel failed: ${run.ipykernelImport.output}", 0, run.ipykernelImport.exitCode)
  }

  /** The second interpreter must leave Alpine's own runtime working: apk, musl python, its stack. */
  private fun assertMuslSeamsIntact(seams: MuslSeams) {
    assertEquals("apk did not answer beside the glibc interpreter: ${seams.apk.output}", 0, seams.apk.exitCode)
    assertEquals("the musl interpreter did not start: ${seams.python.output}", 0, seams.python.exitCode)
    assertEquals("usr/pip.conf is gone: ${seams.pipConf.output}", 0, seams.pipConf.exitCode)
    assertTrue(
      "the .pth no longer puts the user site on sys.path: ${seams.userSite.output}",
      "True" in seams.userSite.output,
    )
    assertEquals("ipykernel did not import under musl: ${seams.ipykernel.output}", 0, seams.ipykernel.exitCode)
  }

  private data class InterpreterRun(
    val banner: SpaceProcessRun,
    val duckdbInstall: SpaceProcessRun,
    val duckdbImport: SpaceProcessRun,
    val ipykernelInstall: SpaceProcessRun,
    val ipykernelImport: SpaceProcessRun,
  )

  private data class MuslSeams(
    val apk: SpaceProcessRun,
    val python: SpaceProcessRun,
    val pipConf: SpaceProcessRun,
    val userSite: SpaceProcessRun,
    val ipykernel: SpaceProcessRun,
  )

  private companion object {
    const val PROBE_TAG = "JupydroidImageShapes"
    const val WHOLE_ROOT = "image-shapes-whole"
    const val SECOND_ROOT = "image-shapes-second"
    const val REPORT = "image-shapes-report.txt"
    const val ROOTFS_ASSET = "spaces/base/rootfs-arm64-v8a.tzst"
    const val OPT_GNU = "opt/gnu"

    /** The two ways a Space's processes name the glibc interpreter, one per shape. */
    val WHOLE_PYTHON = listOf("/usr/bin/python3")
    val SECOND_PYTHON = listOf("/opt/gnu/python/bin/python3.12")

    /**
     * The glibc runtime files the second interpreter needs beyond what PBS ships: the loader's
     * own family, the NSS modules `hosts: dns` loads, and `libstdc++` the wheels' `.so`s link.
     * Copied out of the whole image's extraction - the same Ubuntu bytes, not a second download.
     */
    val GNU_LIB_NAMES = listOf(
      "ld-linux-aarch64.so.1",
      "libc.so.6",
      "libm.so.6",
      "libpthread.so.0",
      "librt.so.1",
      "libdl.so.2",
      "libutil.so.1",
      "libgcc_s.so.1",
      "libstdc++.so.6",
      "libstdc++.so.6.0.33",
      "libnss_dns.so.2",
      "libnss_files.so.2",
    )

    val MUSL_PYTHON = listOf("/usr/bin/python3")

    /** `LD_PRELOAD` emptied: the second interpreter's environment, without musl's preloads. */
    val NO_PRELOAD = mapOf("LD_PRELOAD" to "")

    const val BANNER = "import sys,platform;print(sys.version.split()[0]);print(platform.libc_ver())"
    const val DUCKDB_IMPORT = "import duckdb; print('duckdb', duckdb.__version__, duckdb.__file__)"
    const val IPYKERNEL_IMPORT = "import ipykernel; print('ipykernel', ipykernel.__version__, ipykernel.__file__)"
    const val USER_SITE_CHECK =
      "import site,sys;print('user-site-on-path', site.getusersitepackages() in sys.path)"
    const val PTH_GLOB =
      "import glob;print('pth-files', glob.glob('/python/lib/python3.12/**/*.pth', recursive=True))"

    const val BANNER_TIMEOUT_SECONDS = 120L
    const val PIP_TIMEOUT_SECONDS = 600L
    const val IMPORT_TIMEOUT_SECONDS = 300L
    const val SEAM_TIMEOUT_SECONDS = 120L
  }
}
