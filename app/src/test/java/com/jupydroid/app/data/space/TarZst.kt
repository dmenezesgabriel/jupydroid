package com.jupydroid.app.data.space

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import java.io.ByteArrayOutputStream

/**
 * A `docker export | zstd` tarball built in memory - the archive shape `TarZstdExtractor`
 * consumes, small enough for a test to hand a repository a "rootfs" that is three files.
 *
 * ```
 * val archive = tarZst { file("usr/bin/hello", "#!/bin/sh\n") }
 * extractor.extract({ archive.inputStream() }, targetDir)
 * ```
 */
fun tarZst(build: TarZstBuilder.() -> Unit): ByteArray {
  val bytes = ByteArrayOutputStream()
  TarArchiveOutputStream(ZstdCompressorOutputStream(bytes)).use { tar ->
    tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
    TarZstBuilder(tar).build()
  }
  return bytes.toByteArray()
}

class TarZstBuilder(private val tar: TarArchiveOutputStream) {
  fun directory(name: String) {
    tar.putArchiveEntry(TarArchiveEntry("$name/"))
    tar.closeArchiveEntry()
  }

  fun file(name: String, content: String, mode: Int = TarArchiveEntry.DEFAULT_FILE_MODE) {
    val entry = TarArchiveEntry(name)
    entry.mode = mode
    entry.size = content.toByteArray().size.toLong()
    tar.putArchiveEntry(entry)
    tar.write(content.toByteArray())
    tar.closeArchiveEntry()
  }

  fun symlink(name: String, target: String) {
    val entry = TarArchiveEntry(name, TarArchiveEntry.LF_SYMLINK)
    entry.linkName = target
    tar.putArchiveEntry(entry)
    tar.closeArchiveEntry()
  }

  fun hardlink(name: String, target: String, mode: Int = TarArchiveEntry.DEFAULT_FILE_MODE) {
    val entry = TarArchiveEntry(name, TarArchiveEntry.LF_LINK)
    entry.mode = mode
    entry.linkName = target
    tar.putArchiveEntry(entry)
    tar.closeArchiveEntry()
  }
}
