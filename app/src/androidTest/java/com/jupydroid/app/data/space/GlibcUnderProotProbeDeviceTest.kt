package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/**
 * **Does stock glibc start under the runtime this app actually ships?**
 *
 * ADR-0003 chose musl because "glibc's libc startup is killed by the app seccomp filter". That was
 * measured on 2026-08-25, before ADR-0004's SIGSYS handler existed: the death it saw was an
 * unhandled `SECCOMP_RET_TRAP` at `set_robust_list`, and the handler now turns that trap into the
 * `ENOSYS` a Linux kernel without the syscall would return. Nobody has re-run the premise since,
 * and it decides whether a glibc environment (and PyPI's `manylinux` wheels) is possible at all.
 *
 * The probe is a private root extracted from Ubuntu base 24.04.4 - stock glibc 2.39 - run through
 * the shipped [ProotCommand] and [SpaceRuntime] environment, **inside the app process**, which is
 * the only place the seccomp filter applies. Three arms separate the question from the instrument:
 *
 * | Arm | Preload in `<root>/lib/` | What it measures |
 * |---|---|---|
 * | 0 | none | the baseline: does glibc die of SIGSYS under proot, as ADR-0003 measured without it? |
 * | 1 | a glibc build of `sigsys_report.c` | whether a preload can arm glibc before `ld.so`'s own `set_robust_list` |
 * | 2 | the shipped musl build | that the shipped handler cannot arm a glibc root: it `NEEDED`s `libc.musl-aarch64.so.1` |
 *
 * The report is written to `probe/report.txt` in the app's external files dir and to logcat, and
 * the test pins what it measured: `set_robust_list` is the refused syscall, `ld.so` issues it
 * before any preload constructor runs - so an `LD_PRELOAD` handler cannot arm a glibc process at
 * all - and the shipped musl handler cannot resolve its libc in a glibc root. If a later Android
 * build stops refusing the syscall, the trace loses `signal 31` and this fails, which is the point.
 *
 * ```
 * scripts/glibc-under-proot-driver.sh
 * ```
 *
 * TABLET ONLY (SM-X216B): only arm64 ships a rootfs, and on x86_64 nothing runs a cell.
 */
@RunWith(AndroidJUnit4::class)
class GlibcUnderProotProbeDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val probeDir by lazy { File(requireNotNull(context.getExternalFilesDir(null)), "probe") }
  private val root by lazy { File(spacesDir(context), "glibc-probe") }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no proot to run a glibc root",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
  }

  @Test(timeout = 900_000)
  fun stockGlibcUnderTheShippedRuntime() {
    val inputs = stagedInputs()
    extractUbuntu(inputs.tarball)
    SpaceDirs(root).ensure()
    val runtime = SpaceRuntime(root, File(context.applicationInfo.nativeLibraryDir))
    val report = probeHeader(inputs)

    val noHandler = armNoHandler(runtime, report)
    val glibcArm = armGlibcHandler(runtime, inputs.glibcHandler, report)
    val muslArm = armMuslHandler(runtime, inputs.muslHandler, report)

    File(probeDir, REPORT).writeText(report.toString())
    Log.i(PROBE_TAG, report.toString())
    assertInstrumentWorked(noHandler, glibcArm, muslArm)
  }

  /** The baseline ADR-0003 measured without a handler: a bare glibc root under the shipped proot. */
  private fun armNoHandler(runtime: SpaceRuntime, report: StringBuilder): ArmResult {
    handlerInRoot().delete()
    val log = sigsysLog().apply { delete() }
    report.append("\n== arm 0 - no handler\n")
    val run = runIn(runtime, listOf("/usr/bin/true"), report)
    report.append("sigsys log: ${log.takeIf { it.isFile }?.readText() ?: "absent"}\n")
    return ArmResult(run, log)
  }

  /** The question: a glibc build of the same handler, and glibc's own `/bin/true` and `/bin/sh`. */
  private fun armGlibcHandler(runtime: SpaceRuntime, handler: File, report: StringBuilder): GlibcArm {
    handler.copyTo(handlerInRoot(), overwrite = true)
    val log = sigsysLog().apply { delete() }
    val environment = runtime.environment()
    report.append("\n== arm 1 - the glibc handler\n")
    report.append("preload=${environment["LD_PRELOAD"]} log=${environment["JUPYDROID_SIGSYS_LOG"]}\n")
    report.append("handler in root: ${handlerInRoot().isFile} bytes=${handlerInRoot().length()}\n")
    val trueRun = runIn(runtime, listOf("/usr/bin/true"), report)
    report.append("sigsys log: ${log.takeIf { it.isFile }?.readText() ?: "absent"}\n")
    val (loader, syscalls) = loaderTrace(runtime)
    report.append("== the loader's own words\n").append(loader.joinToString("\n")).append("\n")
    report.append("== the last syscalls proot saw\n").append(syscalls.joinToString("\n")).append("\n")
    val shellRun = runIn(runtime, listOf("/usr/bin/sh", "-c", "echo glibc-ok"), report)
    return GlibcArm(trueRun, shellRun, log, loader, syscalls)
  }

  /**
   * The loader's own words and the last syscalls proot saw, in one run: whether a preload
   * constructor ever ran, and which syscall the filter refused before it could.
   */
  private fun loaderTrace(runtime: SpaceRuntime): Pair<List<String>, List<String>> {
    val proot = ProotCommand(File(context.applicationInfo.nativeLibraryDir), root, "/home/jupydroid")
    val argv = proot.argv(listOf("/usr/bin/true")).toMutableList().apply {
      add(1, "-v")
      add(2, "9")
    }
    val process = ProcessBuilder(argv).apply {
      environment().clear()
      // Full ptrace rather than proot's seccomp fast path, so every syscall is logged: the one
      // the app's filter refuses is the one whose entry line comes last. LD_DEBUG names the
      // constructors, so "no calling init" is visible next to the refusal.
      environment().putAll(
        proot.environment() + runtime.environment() + mapOf("PROOT_NO_SECCOMP" to "1", "LD_DEBUG" to "libs"),
      )
    }.redirectErrorStream(true).start()
    process.waitFor(ARM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    val trace = process.inputStream.bufferedReader().use { it.readText() }.lines()
    val loader = trace.filter { "find library" in it || "calling init" in it || "preload" in it }
    return loader to trace.filter { "proot info: vpid" in it }.takeLast(SYSCALL_LINES)
  }

  /** Why the handler cannot simply be reused: a glibc root cannot resolve musl's `DT_NEEDED`. */
  private fun armMuslHandler(runtime: SpaceRuntime, handler: File, report: StringBuilder): ArmResult {
    handler.copyTo(handlerInRoot(), overwrite = true)
    val log = sigsysLog().apply { delete() }
    report.append("\n== arm 2 - the shipped musl handler\n")
    val run = runIn(runtime, listOf("/usr/bin/true"), report)
    report.append("sigsys log: ${log.takeIf { it.isFile }?.readText() ?: "absent"}\n")
    return ArmResult(run, log)
  }

  private fun runIn(runtime: SpaceRuntime, command: List<String>, report: StringBuilder): SpaceProcessRun {
    val process = runtime.execProcessBuilder(command).redirectErrorStream(true).start()
    val exited = process.waitFor(ARM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
    if (!exited) process.destroyForcibly()
    val output = process.inputStream.bufferedReader().use { it.readText() }
    val run = SpaceProcessRun(command.joinToString(" "), if (exited) process.exitValue() else TIMED_OUT, output)
    report.append("${run.command} -> exit=${run.exitCode} output=${run.output.trim()}\n")
    return run
  }

  private fun assertInstrumentWorked(noHandler: ArmResult, glibcArm: GlibcArm, muslArm: ArmResult) {
    assertFalse("arm 0 did not terminate; the probe is broken, not glibc", noHandler.timedOut)
    assertFalse("arm 1 did not terminate; the probe is broken, not glibc", glibcArm.trueRun.exitCode == TIMED_OUT)
    assertLoaderDiedBeforeThePreload(glibcArm)
    assertTrue(
      "the shipped musl handler did not fail on musl's libc name",
      muslArm.run.output.contains("libc.musl-aarch64.so.1"),
    )
  }

  /** The measurement itself: the refusal lands in the loader, before any preload constructor. */
  private fun assertLoaderDiedBeforeThePreload(glibcArm: GlibcArm) {
    assertTrue(
      "LD_DEBUG said nothing about libc, so the loader never got as far as this probe claims",
      glibcArm.loader.any { "find library=libc.so.6" in it },
    )
    assertFalse(
      "the glibc handler's constructor ran, so a preload can arm the loader after all",
      glibcArm.loader.any { "calling init: /lib/libsigsys_report.so" in it },
    )
    assertFalse("the glibc handler opened its log, so a preload armed the loader", glibcArm.log.exists())
    assertTrue(
      "the last syscalls do not name set_robust_list; see ${File(probeDir, REPORT)}",
      glibcArm.syscalls.any { "set_robust_list" in it },
    )
    assertTrue("the trace does not show the SIGSYS that followed", glibcArm.syscalls.any { "signal 31" in it })
  }

  private fun stagedInputs(): StagedInputs {
    val tarball = staged(UBUNTU_TARBALL)
    assertEquals(
      "the staged rootfs is not the pinned one; rerun scripts/glibc-under-proot-driver.sh",
      UBUNTU_SHA256,
      sha256(tarball),
    )
    return StagedInputs(tarball, staged(GLIBC_HANDLER), staged(MUSL_HANDLER))
  }

  private fun probeHeader(inputs: StagedInputs): StringBuilder = StringBuilder("== the probe\n")
    .append("device=${Build.MODEL} sdk=${Build.VERSION.SDK_INT} abis=${Build.SUPPORTED_ABIS.joinToString()}\n")
    .append("rootfs=${inputs.tarball.name} bytes=${inputs.tarball.length()} sha256=${sha256(inputs.tarball)}\n")
    .append("glibc handler bytes=${inputs.glibcHandler.length()}\n")
    .append("musl handler bytes=${inputs.muslHandler.length()}\n")

  private fun handlerInRoot(): File = File(root, "lib/libsigsys_report.so")

  private fun sigsysLog(): File = File(root, "tmp/$SIGSYS_LOG")

  private fun staged(name: String): File = File(probeDir, name).also {
    require(it.isFile) { "missing '$it'; run scripts/glibc-under-proot-driver.sh before this class" }
  }

  /**
   * A fresh Ubuntu root, extracted with the rules a Space needs: the executable bit survives,
   * symlinks stay symlinks, and a hard link is copied because `link(2)` is refused under `filesDir`.
   */
  private fun extractUbuntu(tarball: File) {
    root.deleteRecursively()
    root.mkdirs()
    GzipCompressorInputStream(tarball.inputStream().buffered()).use { gzip ->
      TarArchiveInputStream(gzip).use { tar ->
        while (true) {
          val entry = tar.nextEntry ?: break
          val destination = resolveInside(entry.name)
          when {
            entry.isDirectory -> destination.mkdirs()
            entry.isSymbolicLink -> destination.linkTo(entry.linkName)
            entry.isLink -> destination.copyFrom(resolveInside(entry.linkName))
            entry.isFile -> destination.writeEntry(tar, entry.mode)
          }
        }
      }
    }
  }

  private fun resolveInside(name: String): File {
    val resolved = root.toPath().resolve(name).normalize()
    require(resolved.startsWith(root.toPath())) { "archive entry '$name' escapes '$root'" }
    return resolved.toFile()
  }

  private fun File.writeEntry(tar: TarArchiveInputStream, mode: Int) {
    parentFile?.mkdirs()
    delete()
    outputStream().use { tar.copyTo(it) }
    if (mode and OWNER_EXECUTE != 0) setExecutable(true, true)
  }

  private fun File.linkTo(target: String) {
    parentFile?.mkdirs()
    delete()
    Files.createSymbolicLink(toPath(), File(target).toPath())
  }

  private fun File.copyFrom(source: File) {
    parentFile?.mkdirs()
    delete()
    source.copyTo(this, overwrite = true)
  }

  private fun sha256(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    file.inputStream().buffered().use { input ->
      val buffer = ByteArray(READ_CHUNK)
      while (true) {
        val read = input.read(buffer)
        if (read < 0) break
        digest.update(buffer, 0, read)
      }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
  }

  private data class StagedInputs(val tarball: File, val glibcHandler: File, val muslHandler: File)

  private data class ArmResult(val run: SpaceProcessRun, val log: File) {
    val timedOut: Boolean get() = run.exitCode == TIMED_OUT
  }

  private data class GlibcArm(
    val trueRun: SpaceProcessRun,
    val shellRun: SpaceProcessRun,
    val log: File,
    val loader: List<String>,
    val syscalls: List<String>,
  )

  private companion object {
    const val PROBE_TAG = "JupydroidGlibc"

    /** Where the driver stages its three inputs and the report comes back from. */
    const val UBUNTU_TARBALL = "ubuntu-base-24.04.4-base-arm64.tar.gz"
    const val GLIBC_HANDLER = "libsigsys_report_glibc.so"
    const val MUSL_HANDLER = "libsigsys_report_musl.so"
    const val REPORT = "report.txt"

    /** Ubuntu's own `SHA256SUMS` for the arm64 base image, read 2026-11-20. */
    const val UBUNTU_SHA256 = "04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2"

    /** [SpaceRuntime]'s own log name, under the root's `tmp/`. */
    const val SIGSYS_LOG = "sigsys.log"
    const val OWNER_EXECUTE = 0b001_000_000
    const val READ_CHUNK = 1 shl 20
    const val ARM_TIMEOUT_SECONDS = 120L
    const val TIMED_OUT = -1
    const val SYSCALL_LINES = 30
  }
}
