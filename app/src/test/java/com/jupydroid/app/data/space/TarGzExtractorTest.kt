package com.jupydroid.app.data.space

import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TarGzExtractorTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val extractor = TarGzExtractor()

  @Test
  fun extract_writesRegularFilesAndDirectories() = runTest {
    val archive = tarGz {
      directory("usr/bin")
      file("usr/bin/hello", "#!/bin/sh\n")
    }
    val target = tempFolder.newFolder("rootfs")

    val count = extractor.extract({ archive.inputStream() }, target)

    assertEquals("#!/bin/sh\n", File(target, "usr/bin/hello").readText())
    assertTrue(File(target, "usr/bin").isDirectory)
    assertEquals(2, count)
  }

  @Test
  fun extract_recreatesSymlinksRatherThanCopyingTargets() = runTest {
    val archive = tarGz {
      file("usr/lib/libz.so.1.2.13", "payload")
      symlink(name = "usr/lib/libz.so.1", target = "libz.so.1.2.13")
    }
    val target = tempFolder.newFolder("rootfs")

    extractor.extract({ archive.inputStream() }, target)

    val link = File(target, "usr/lib/libz.so.1")
    assertTrue("expected a symlink at $link", link.isSymlink())
    assertEquals("payload", link.readText())
  }

  /** Docker exports hardlink duplicate files; the rootfs must still contain the bytes. */
  @Test
  fun extract_materialisesHardLinks() = runTest {
    val archive = tarGz {
      file("bin/first", "shared")
      hardlink(name = "bin/second", target = "bin/first")
    }
    val target = tempFolder.newFolder("rootfs")

    extractor.extract({ archive.inputStream() }, target)

    assertEquals("shared", File(target, "bin/second").readText())
  }

  /** A crafted archive must not be able to write outside the Space directory. */
  @Test
  fun extract_rejectsPathsEscapingTheTargetDirectory() = runTest {
    val archive = tarGz { file("../escaped", "nope") }
    val target = tempFolder.newFolder("rootfs")

    try {
      extractor.extract({ archive.inputStream() }, target)
      fail("expected the traversing entry to be rejected")
    } catch (e: IllegalArgumentException) {
      assertTrue("message must name the entry, was '${e.message}'", e.message!!.contains("../escaped"))
    }
  }

  private fun File.isSymlink(): Boolean = Files.isSymbolicLink(toPath())

  private fun tarGz(build: TarBuilder.() -> Unit): ByteArray {
    val bytes = ByteArrayOutputStream()
    TarArchiveOutputStream(GZIPOutputStream(bytes)).use { tar ->
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
      TarBuilder(tar).build()
    }
    return bytes.toByteArray()
  }

  private class TarBuilder(private val tar: TarArchiveOutputStream) {
    fun directory(name: String) {
      tar.putArchiveEntry(TarArchiveEntry("$name/"))
      tar.closeArchiveEntry()
    }

    fun file(name: String, content: String) {
      val entry = TarArchiveEntry(name)
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

    fun hardlink(name: String, target: String) {
      val entry = TarArchiveEntry(name, TarArchiveEntry.LF_LINK)
      entry.linkName = target
      tar.putArchiveEntry(entry)
      tar.closeArchiveEntry()
    }
  }
}
