package com.jupydroid.app.data.space

import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.apache.commons.compress.compressors.zstandard.ZstdCompressorOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files

class BaseSpaceInstallerTest {
  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun install_unpacksTheRootfsAndStampsTheVersion() = runTest {
    val installer = installer(version = 1)

    val installed = installer.installIfNeeded()

    assertEquals("print(1)\n", File(installed.baseDir, "usr/bin/python3.11").readText())
    assertTrue(installed.freshlyInstalled)
    assertEquals(1, installed.version)
  }

  @Test
  fun install_isANoOpWhenTheStampedVersionMatches() = runTest {
    val baseDir = tempFolder.newFolder("base")
    installer(version = 1, baseDir = baseDir).installIfNeeded()
    File(baseDir, "usr/bin/python3.11").writeText("edited by the user")

    val second = installer(version = 1, baseDir = baseDir).installIfNeeded()

    assertFalse(second.freshlyInstalled)
    assertEquals("edited by the user", File(baseDir, "usr/bin/python3.11").readText())
  }

  @Test
  fun install_reinstallsWhenTheBundledVersionIsNewer() = runTest {
    val baseDir = tempFolder.newFolder("base")
    installer(version = 1, baseDir = baseDir).installIfNeeded()
    File(baseDir, "usr/bin/python3.11").writeText("stale")

    val upgraded = installer(version = 2, baseDir = baseDir).installIfNeeded()

    assertTrue(upgraded.freshlyInstalled)
    assertEquals("print(1)\n", File(baseDir, "usr/bin/python3.11").readText())
  }

  /**
   * Under proot the image runs as built (ADR-0073), so the installer leaves its files alone: the
   * interpreter is the image's ELF and the shell its own busybox applet, with nothing relinked into
   * `nativeLibraryDir`, a path proot would translate into the root, where it does not exist.
   */
  @Test
  fun install_leavesTheImagesOwnInterpreterAndShellInPlace() = runTest {
    val installed = installer(version = 1).installIfNeeded()

    assertFalse(Files.isSymbolicLink(File(installed.baseDir, "usr/bin/python3.12").toPath()))
    assertEquals("/bin/busybox", Files.readSymbolicLink(File(installed.baseDir, "bin/sh").toPath()).toString())
  }

  @Test
  fun install_pointsTheKernelSpecAtTheImagesInterpreter() = runTest {
    val installed = installer(version = 1).installIfNeeded()

    val kernelJson = File(installed.baseDir, "usr/share/jupyter/kernels/python3/kernel.json").readText()
    assertTrue(kernelJson, "\"$SPACE_INTERPRETER\"" in kernelJson)
  }

  /** Base's server-less callers (`PythonRunner`) run pip with no server start to write it. */
  @Test
  fun install_writesBasesOwnPipConf() = runTest {
    val installed = installer(version = 1).installIfNeeded()

    assertTrue(File(installed.baseDir, "usr/pip.conf").isFile)
  }

  /**
   * An install from before proot relinked the image's own files into `nativeLibraryDir`. The
   * re-extract a version bump triggers replaces every one of them the archive carries - the
   * interpreter here - because the extractor replaces an entry rather than writing through it.
   */
  @Test
  fun install_restoresTheImagesInterpreterOverALinkAnOlderInstallLeft() = runTest {
    val baseDir = tempFolder.newFolder("base")
    linkInto(baseDir, "usr/bin/python3.12", "/data/app/~~old==/lib/arm64/libpython3.so")

    installer(version = 2, baseDir = baseDir).installIfNeeded()

    assertEquals("ELF", File(baseDir, "usr/bin/python3.12").readText())
  }

  /**
   * The console-script links that older install made in `usr/local/bin` are not in the archive, so
   * no re-extract replaces them: they are swept, and anything else in the directory is not.
   */
  @Test
  fun install_sweepsTheConsoleScriptLinksAnOlderInstallLeft() = runTest {
    val baseDir = tempFolder.newFolder("base")
    val stale = linkInto(baseDir, "usr/local/bin/pip", "/data/app/~~old==/lib/arm64/libshebang.so")
    val users = File(baseDir, "usr/local/bin/mine").apply { writeText("#!/bin/sh\n") }

    installer(version = 2, baseDir = baseDir).installIfNeeded()

    assertFalse("$stale survived the upgrade", Files.isSymbolicLink(stale.toPath()))
    assertTrue("$users was swept", users.isFile)
  }

  private fun linkInto(baseDir: File, relative: String, target: String): File = File(baseDir, relative).apply {
    parentFile!!.mkdirs()
    Files.createSymbolicLink(toPath(), File(target).toPath())
  }

  private fun installer(version: Int, baseDir: File = tempFolder.newFolder("base-$version-${System.nanoTime()}")) =
    BaseSpaceInstaller(
      archive = { rootfsArchive().inputStream() },
      baseDir = baseDir,
      bundledVersion = version,
    )

  /** Stands in for the real rootfs: the same shapes - a file, busybox and its applet, python's links. */
  private fun rootfsArchive(): ByteArray {
    val bytes = ByteArrayOutputStream()
    TarArchiveOutputStream(ZstdCompressorOutputStream(bytes)).use { tar ->
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
      tar.putArchiveEntry(TarArchiveEntry("usr/bin/"))
      tar.closeArchiveEntry()
      tar.putFile("usr/bin/python3.11", "print(1)\n")
      // The image's own busybox: one real ELF, and an applet link pointing absolutely at it.
      tar.putFile("bin/busybox", "ELF")
      tar.putSymlink("bin/sh", "/bin/busybox")
      // The image's own python, in the three shapes `tar -tzf` reports: one real ELF and two
      // relative links onto it.
      tar.putFile("usr/bin/python3.12", "ELF")
      tar.putSymlink("usr/bin/python3", "python3.12")
      tar.putSymlink("usr/bin/python", "python3")
    }
    return bytes.toByteArray()
  }

  private fun TarArchiveOutputStream.putFile(path: String, content: String) {
    val bytes = content.toByteArray()
    putArchiveEntry(TarArchiveEntry(path).apply { size = bytes.size.toLong() })
    write(bytes)
    closeArchiveEntry()
  }

  private fun TarArchiveOutputStream.putSymlink(path: String, target: String) {
    putArchiveEntry(TarArchiveEntry(path, TarArchiveEntry.LF_SYMLINK).apply { linkName = target })
    closeArchiveEntry()
  }
}
