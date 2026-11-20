package com.jupydroid.app.data.space

import androidx.test.platform.app.InstrumentationRegistry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

/** What a probe's process returned when it never finished: no exit status exists. */
const val PROBE_TIMED_OUT = -1

/**
 * Runs [command] in [runtime] with a bound, so a probe records a hang instead of waiting on it.
 *
 * A probe's process is a Space process under proot: it can outlive its command when the command
 * leaves children behind, so the bound is what makes "did not terminate" a result rather than a
 * test timeout.
 *
 * ```
 * val run = runBounded(runtime, listOf("/usr/bin/true"), 120)
 * ```
 */
fun runBounded(
  runtime: SpaceRuntime,
  command: List<String>,
  timeoutSeconds: Long,
  extraEnv: Map<String, String> = emptyMap(),
): SpaceProcessRun {
  val process = runtime.execProcessBuilder(command, extraEnv).redirectErrorStream(true).start()
  val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
  if (!exited) process.destroyForcibly()
  val output = process.inputStream.bufferedReader().use { it.readText() }
  return SpaceProcessRun(command.joinToString(" "), if (exited) process.exitValue() else PROBE_TIMED_OUT, output)
}

/**
 * Replaces [this] with a symlink to [target], as an image's own entry is written.
 *
 * `delete()` first because a re-extract may find the path already there, and `createSymbolicLink`
 * refuses an existing one.
 *
 * ```
 * destination.replaceWithSymbolicLink("usr/bin")
 * ```
 */
fun File.replaceWithSymbolicLink(target: String) {
  parentFile?.mkdirs()
  delete()
  Files.createSymbolicLink(toPath(), File(target).toPath())
}

/**
 * Extracts [tarball] - a gzipped tar - into [root] with the rules a Space image needs: the
 * executable bit survives, symlinks stay symlinks, and a hard link is copied because `link(2)`
 * is refused under `filesDir`. An entry that would escape [root] is refused.
 *
 * The glibc probes share it: one extracts the Ubuntu base and then a CPython tarball into the
 * same root, so it does not delete - the caller that wants a fresh root deletes first.
 *
 * ```
 * root.deleteRecursively(); root.mkdirs()
 * extractTarGz(tarball, root)
 * ```
 */
fun extractTarGz(tarball: File, root: File) {
  GzipCompressorInputStream(tarball.inputStream().buffered()).use { gzip ->
    TarArchiveInputStream(gzip).use { tar ->
      while (true) {
        val entry = tar.nextEntry ?: break
        val destination = tarEntryInside(root, entry.name)
        when {
          entry.isDirectory -> destination.mkdirs()
          entry.isSymbolicLink -> destination.replaceWithSymbolicLink(entry.linkName)
          entry.isLink -> destination.copyFromTarEntry(tarEntryInside(root, entry.linkName))
          entry.isFile -> destination.writeTarEntry(tar, entry.mode)
        }
      }
    }
  }
}

/**
 * The pinned inputs the glibc probes share: Ubuntu's own `SHA256SUMS` for the 24.04.4 arm64
 * base, and python-build-standalone's CPython 3.12.16 (release 20261110) whose sha256 is the
 * release asset's own digest - a stock glibc build whose modules live inside libpython.
 */
const val UBUNTU_TARBALL = "ubuntu-base-24.04.4-base-arm64.tar.gz"
const val UBUNTU_SHA256 = "04207713ece899c3740823d33690441ad3a7f0ded1101aca744e2b0f37ac7ff2"
const val CPYTHON_TARBALL = "cpython-3.12.16+20261110-aarch64-unknown-linux-gnu-install_only.tar.gz"
const val CPYTHON_SHA256 = "4792f81750282597fb2a3389fe18b945ed2ba6aad5e45ad406d122c58cb33081"

/** The SHA-256 of [file] as lowercase hex, for the pinned-input checks the probes make. */
fun sha256Hex(file: File): String {
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

/**
 * The test APK's own `nativeLibraryDir`, where `stage_proot_patched.py` put the rebuilt proot
 * pair. It is a different directory from the app's: the shipped proot stays what Spaces run.
 */
fun patchedProotLibraryDir(): File {
  val dir = File(InstrumentationRegistry.getInstrumentation().context.applicationInfo.nativeLibraryDir)
  val missing = listOf(ProotCommand.PROOT, ProotCommand.PROOT_LOADER).filter { !File(dir, it).isFile }
  require(missing.isEmpty()) {
    "patched proot not staged in the test APK's jniLibs (missing $missing); " +
      "run tools/proot/build.sh then scripts/stage_proot_patched.py first"
  }
  return dir
}

private fun tarEntryInside(root: File, name: String): File {
  val resolved = root.toPath().resolve(name).normalize()
  require(resolved.startsWith(root.toPath())) { "archive entry '$name' escapes '$root'" }
  return resolved.toFile()
}

private fun File.writeTarEntry(tar: TarArchiveInputStream, mode: Int) {
  parentFile?.mkdirs()
  delete()
  outputStream().use { tar.copyTo(it) }
  if (mode and OWNER_EXECUTE != 0) setExecutable(true, true)
}

private fun File.copyFromTarEntry(source: File) {
  parentFile?.mkdirs()
  delete()
  source.copyTo(this, overwrite = true)
}

private const val OWNER_EXECUTE = 0b001_000_000
private const val READ_CHUNK = 1 shl 20
