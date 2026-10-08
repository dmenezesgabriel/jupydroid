package com.jupydroid.app.data.space

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorInputStream
import java.io.File
import java.io.InputStream

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
 * Decoding and writing overlap: one coroutine decodes while another writes what it has decoded,
 * in archive order (ADR-0079). On the tablet, writing the image's bytes costs about twice what
 * decoding them does (`ExtractProbeDeviceTest`), so in series each waited on the other for
 * nothing. Content crosses in pieces of at most [CHUNK_BYTES] through a channel of
 * [PIPELINE_STEPS], so what is in flight stays a few MiB however large a file the image carries.
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
      val steps = Channel<TarStep>(PIPELINE_STEPS)
      val writing = async { TarEntryWriter(targetDir.canonicalFile).writeAll(steps, onProgress) }
      val raw = source.open()
      val total = raw.available().takeIf { it > 0 }?.toFloat()
      TarArchiveInputStream(ZstdCompressorInputStream(raw.buffered())).use { tar ->
        decode(tar, steps) { total?.let { ((it - raw.available()) / it).coerceIn(0f, 1f) } }
      }
      steps.close()
      writing.await()
    }

  private suspend fun decode(tar: TarArchiveInputStream, steps: SendChannel<TarStep>, consumed: () -> Float?) {
    while (true) {
      val entry = tar.nextEntry ?: break
      if (entry.carriesContent && entry.size > CHUNK_BYTES) {
        sendInChunks(tar, entry, steps)
        steps.send(TarStep.End(consumed()))
      } else {
        val bytes = if (entry.carriesContent) tar.readChunk(entry.size.toInt()) else null
        steps.send(TarStep.Whole(entry, bytes, consumed()))
      }
    }
  }

  private suspend fun sendInChunks(tar: TarArchiveInputStream, entry: TarArchiveEntry, steps: SendChannel<TarStep>) {
    steps.send(TarStep.Header(entry))
    var remaining = entry.size
    while (remaining > 0) {
      val chunk = tar.readChunk(minOf(remaining, CHUNK_BYTES.toLong()).toInt())
      if (chunk.isEmpty()) return
      steps.send(TarStep.Content(chunk))
      remaining -= chunk.size
    }
  }

  /** A hard link answers `isFile` too, but it is written by copying its target, not from bytes. */
  private val TarArchiveEntry.carriesContent: Boolean get() = isFile && !isLink && !isSymbolicLink

  /** Up to [size] bytes of the current entry: fewer only where the entry itself ends short. */
  private fun TarArchiveInputStream.readChunk(size: Int): ByteArray {
    val chunk = ByteArray(size)
    var filled = 0
    while (filled < size) {
      val read = read(chunk, filled, size - filled)
      if (read < 0) break
      filled += read
    }
    return if (filled == size) chunk else chunk.copyOf(filled)
  }

  private companion object {
    const val CHUNK_BYTES = 1024 * 1024

    /** Eight steps of at most [CHUNK_BYTES]: the decoder runs at most a few MiB ahead. */
    const val PIPELINE_STEPS = 8
  }
}
