package com.jupydroid.app.data.space

import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class TarZstdExtractorTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val extractor = TarZstdExtractor()

  @Test
  fun extract_writesRegularFilesAndDirectories() = runTest {
    val archive = tarZst {
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
    val archive = tarZst {
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
    val archive = tarZst {
      file("bin/first", "shared")
      hardlink(name = "bin/second", target = "bin/first")
    }
    val target = tempFolder.newFolder("rootfs")

    extractor.extract({ archive.inputStream() }, target)

    assertEquals("shared", File(target, "bin/second").readText())
  }

  /**
   * Regression: the app-update path. A previous install left `lib/ld-musl-aarch64.so.1`
   * symlinked into nativeLibraryDir, and an app update moves that directory - so at
   * re-extract time the link dangles. Writing through it fails with ENOENT because the
   * old target's directory is gone, which crash-looped the app on the first bundled
   * rootfs bump. Replace the entry, never write through it.
   */
  @Test
  fun extract_replacesADanglingSymlinkLeftByAPreviousInstall() = runTest {
    val target = tempFolder.newFolder("rootfs")
    val stale = File(target, "lib/ld-musl-aarch64.so.1").apply { parentFile!!.mkdirs() }
    Files.createSymbolicLink(stale.toPath(), File("/data/app/gone-on-update/lib/arm64/libloader.so").toPath())
    val archive = tarZst { file("lib/ld-musl-aarch64.so.1", "fresh loader") }

    extractor.extract({ archive.inputStream() }, target)

    assertTrue("expected the dangling link to be replaced by a real file", !stale.isSymlink())
    assertEquals("fresh loader", stale.readText())
  }

  /** proot checks the executable bit where the kernel would, so the archive's own mode must survive. */
  @Test
  fun extract_keepsTheOwnerExecutableBitOfAnExecutableEntry() = runTest {
    val archive = tarZst {
      file("bin/busybox", "elf", mode = 0b111_101_101)
      file("etc/motd", "hi", mode = 0b110_100_100)
    }
    val target = tempFolder.newFolder("rootfs")

    extractor.extract({ archive.inputStream() }, target)

    assertTrue("bin/busybox lost its executable bit", File(target, "bin/busybox").canExecute())
    assertFalse("etc/motd gained an executable bit", File(target, "etc/motd").canExecute())
  }

  /** A docker export hardlinks duplicate executables, and the copy runs as the original does. */
  @Test
  fun extract_keepsTheExecutableBitOfAMaterialisedHardLink() = runTest {
    val archive = tarZst {
      file("usr/bin/python3.12", "elf", mode = 0b111_101_101)
      hardlink(name = "usr/bin/python3", target = "usr/bin/python3.12", mode = 0b111_101_101)
    }
    val target = tempFolder.newFolder("rootfs")

    extractor.extract({ archive.inputStream() }, target)

    assertTrue("usr/bin/python3 lost its executable bit", File(target, "usr/bin/python3").canExecute())
  }

  /** A crafted archive must not be able to write outside the Space directory. */
  @Test
  fun extract_rejectsPathsEscapingTheTargetDirectory() = runTest {
    val archive = tarZst { file("../escaped", "nope") }
    val target = tempFolder.newFolder("rootfs")

    try {
      extractor.extract({ archive.inputStream() }, target)
      fail("expected the traversing entry to be rejected")
    } catch (e: IllegalArgumentException) {
      assertTrue("message must name the entry, was '${e.message}'", e.message!!.contains("../escaped"))
    }
  }

  private fun File.isSymlink(): Boolean = Files.isSymbolicLink(toPath())

  private fun tarZst(build: TarBuilder.() -> Unit): ByteArray {
    val bytes = ByteArrayOutputStream()
    TarArchiveOutputStream(ZstdCompressorOutputStream(bytes)).use { tar ->
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
}
