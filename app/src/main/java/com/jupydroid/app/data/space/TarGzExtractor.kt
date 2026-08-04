package com.jupydroid.app.data.space

import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream

/** Opens the rootfs archive: an APK asset on device, a byte array in tests. */
fun interface ArchiveSource {
  fun open(): InputStream
}

/**
 * Unpacks a `docker export | gzip` tarball into a directory.
 *
 * Nothing here sets an executable bit, and nothing needs to: the rootfs is only ever
 * read and `mmap`'d. The two files that are genuinely execve'd ship as APK native
 * libraries instead (docs/PLAN.md, "Exec model").
 *
 * ```
 * val written = TarGzExtractor().extract({ assets.open("spaces/base/rootfs-x86_64.tgz") }, baseDir)
 * ```
 */
class TarGzExtractor(private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO) {
  /** Returns the number of entries written. */
  suspend fun extract(source: ArchiveSource, targetDir: File): Int = withContext(ioDispatcher) {
    targetDir.mkdirs()
    val root = targetDir.canonicalFile
    var written = 0
    TarArchiveInputStream(GZIPInputStream(source.open().buffered())).use { tar ->
      while (true) {
        val entry = tar.nextEntry ?: break
        if (writeEntry(tar, entry, root)) written++
      }
    }
    written
  }

  private fun writeEntry(tar: TarArchiveInputStream, entry: TarArchiveEntry, root: File): Boolean {
    val destination = resolveInside(root, entry.name)
    return when {
      entry.isDirectory -> destination.mkdirs()
      entry.isSymbolicLink -> destination.linkTo(entry.linkName)
      entry.isLink -> destination.copyFrom(resolveInside(root, entry.linkName))
      entry.isFile -> destination.writeFrom(tar)
      else -> false // character/block devices and FIFOs: a docker export has none we need
    }
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
