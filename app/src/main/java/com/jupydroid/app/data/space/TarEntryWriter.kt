package com.jupydroid.app.data.space

import kotlinx.coroutines.channels.ReceiveChannel
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import java.io.File
import java.io.OutputStream
import java.nio.file.Files

/**
 * What [TarZstdExtractor]'s decoder hands its writer, in archive order. An entry whose bytes fit
 * one chunk - almost every entry in an image - crosses as one [Whole] step: each step is a hand-off
 * between two threads, and three per entry measured slower than not overlapping at all. A larger
 * file crosses as [Header], its [Content] chunks, then [End], so no file is ever held whole.
 * `consumedFraction` is how much of the compressed archive had been read by the entry's end (null
 * when the archive cannot say its length).
 */
internal sealed interface TarStep {
  class Whole(val entry: TarArchiveEntry, val bytes: ByteArray?, val consumedFraction: Float?) : TarStep

  class Header(val entry: TarArchiveEntry) : TarStep

  class Content(val bytes: ByteArray) : TarStep

  class End(val consumedFraction: Float?) : TarStep
}

/**
 * Applies [TarStep]s to the tree under [root], one entry at a time and in the order the archive
 * lists them - which is what lets a hard link be materialised by copying a file the archive wrote
 * before it (ADR-0012). Single use: one instance per extract.
 *
 * ```
 * val written = TarEntryWriter(targetDir.canonicalFile).writeAll(steps, onProgress)
 * ```
 */
internal class TarEntryWriter(private val root: File) {
  /** The entry being written: from its header to its end, or within one [TarStep.Whole]. */
  private class OpenEntry(val header: TarArchiveEntry, val destination: File, val begun: Boolean)

  private var current: OpenEntry? = null
  private var output: OutputStream? = null
  private var written = 0

  /**
   * Returns the number of entries written; [onProgress] gets each written entry's consumed
   * fraction, once that entry is on disk.
   */
  suspend fun writeAll(steps: ReceiveChannel<TarStep>, onProgress: suspend (Float) -> Unit): Int {
    try {
      for (step in steps) apply(step, onProgress)
    } finally {
      output?.close()
    }
    return written
  }

  private suspend fun apply(step: TarStep, onProgress: suspend (Float) -> Unit) {
    when (step) {
      is TarStep.Whole -> {
        begin(step.entry)
        step.bytes?.let { outputFor(it).write(it) }
        if (finish()) step.consumedFraction?.let { onProgress(it) }
      }
      is TarStep.Header -> begin(step.entry)
      is TarStep.Content -> outputFor(step.bytes).write(step.bytes)
      is TarStep.End -> if (finish()) step.consumedFraction?.let { onProgress(it) }
    }
  }

  private fun begin(header: TarArchiveEntry) {
    val destination = resolveInside(root, header.name)
    val begun = when {
      header.isDirectory -> destination.mkdirs()
      header.isSymbolicLink -> destination.linkTo(header.linkName)
      header.isLink -> destination.copyFrom(resolveInside(root, header.linkName))
      header.isFile -> true.also { output = destination.openReplacing() }
      else -> false // character/block devices and FIFOs: a docker export has none we need
    }
    current = OpenEntry(header, destination, begun)
  }

  private fun outputFor(bytes: ByteArray): OutputStream = requireNotNull(output) {
    "${bytes.size} content bytes arrived for '${current?.header?.name}', which is not an open regular file"
  }

  private fun finish(): Boolean {
    output?.close()
    output = null
    val entry = current?.takeIf { it.begun }
    current = null
    if (entry == null) return false
    if (entry.header.isFile || entry.header.isLink) entry.destination.keepExecutableBit(entry.header)
    written++
    return true
  }

  /** Owner only: a Space's files belong to the app's one uid, so group and other bits mean nothing. */
  private fun File.keepExecutableBit(entry: TarArchiveEntry) {
    if (entry.mode and OWNER_EXECUTE != 0) setExecutable(true, true)
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

  private fun File.openReplacing(): OutputStream {
    parentFile?.mkdirs()
    // Replace, never write through: on a re-extract this entry may be the loader symlink
    // into nativeLibraryDir, which an app update has just left dangling. Opening that for
    // write follows the link and fails with ENOENT.
    delete()
    return outputStream()
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
