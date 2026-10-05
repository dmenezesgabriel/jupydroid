package com.jupydroid.app.data.space

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files

/** Opens the rootfs archive: an APK asset on device, a byte array in tests. */
fun interface ArchiveSource {
  fun open(): InputStream
}

/**
 * Unpacks a `docker export | zstd` tarball into a directory.
 *
 * zstd rather than gzip: decompressing the 82 MB arm64 tarball was ~9 s of
 * single-threaded CPU on the tablet - the floor no write-side optimisation can
 * go under. Winlator's rootfs installer runs the same
 * archive shape through the same two libraries.
 *
 * An entry's owner-executable bit survives the extract, because a Space runs under proot
 * (ADR-0073), which checks that bit where the kernel would: `/bin/sh` without it is refused
 * before proot's loader is ever reached.
 *
 * ```
 * val written = TarZstdExtractor().extract({ assets.open("spaces/base/rootfs-arm64-v8a.tzst") }, baseDir)
 * ```
 */
class TarZstdExtractor(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
  /**
   * Returns the number of entries written.
   *
   * [onProgress] gets the fraction of the archive consumed so far, after each entry - the
   * stream's own `available()` answers both the total and how much of it is left, so progress
   * needs no second pass over the bytes. A source that cannot say its length emits nothing.
   */
  suspend fun extract(source: ArchiveSource, targetDir: File, onProgress: suspend (Float) -> Unit = {}): Int =
    withContext(ioDispatcher) {
      targetDir.mkdirs()
      val root = targetDir.canonicalFile
      var written = 0
      val raw = source.open()
      val total = raw.available().takeIf { it > 0 }?.toFloat()
      TarArchiveInputStream(ZstdCompressorInputStream(raw.buffered())).use { tar ->
        while (true) {
          val entry = tar.nextEntry ?: break
          if (writeEntry(tar, entry, root)) {
            written++
            if (total != null) onProgress(((total - raw.available()) / total).coerceIn(0f, 1f))
          }
        }
      }
      written
    }

  private fun writeEntry(tar: TarArchiveInputStream, entry: TarArchiveEntry, root: File): Boolean {
    val destination = resolveInside(root, entry.name)
    return when {
      entry.isDirectory -> destination.mkdirs()
      entry.isSymbolicLink -> destination.linkTo(entry.linkName)
      entry.isLink -> destination.copyFrom(resolveInside(root, entry.linkName)) && destination.keepExecutableBit(entry)
      entry.isFile -> destination.writeFrom(tar) && destination.keepExecutableBit(entry)
      else -> false // character/block devices and FIFOs: a docker export has none we need
    }
  }

  /** Owner only: a Space's files belong to the app's one uid, so group and other bits mean nothing. */
  private fun File.keepExecutableBit(entry: TarArchiveEntry): Boolean {
    if (entry.mode and OWNER_EXECUTE != 0) setExecutable(true, true)
    return true
  }

  private companion object {
    const val OWNER_EXECUTE = 0b001_000_000
  }

  /**
   * Guards against `../` entries in a crafted archive escaping the Space directory.
   *
   * Lexical (`normalize`) rather than `canonicalFile`, which resolves symlinks: on a
   * re-extract the loader link already points at nativeLibraryDir, outside the tree
   * by design, and canonicalising would reject the very entry we mean to replace.
   */
  private fun resolveInside(root: File, name: String): File {
    val resolved = root.toPath().resolve(name).normalize()
    require(resolved.startsWith(root.toPath())) {
      "archive entry '$name' resolves to '$resolved', outside '$root'"
    }
    return resolved.toFile()
  }

  private fun File.writeFrom(tar: TarArchiveInputStream): Boolean {
    parentFile?.mkdirs()
    // Replace, never write through: on a re-extract this entry may be the loader symlink
    // into nativeLibraryDir, which an app update has just left dangling. Opening that for
    // write follows the link and fails with ENOENT.
    delete()
    outputStream().use { tar.copyTo(it) }
    return true
  }

  // java.nio rather than android.system.Os: identical behaviour, available since
  // API 26 (we require 32), and it runs in plain JVM unit tests.
  private fun File.linkTo(linkName: String): Boolean {
    parentFile?.mkdirs()
    delete() // re-running the install must not fail on an existing link
    Files.createSymbolicLink(toPath(), File(linkName).toPath())
    return true
  }

  private fun File.copyFrom(source: File): Boolean {
    parentFile?.mkdirs()
    source.copyTo(this, overwrite = true)
    return true
  }
}
