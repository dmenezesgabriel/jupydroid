package com.jupydroid.app.data.space

import kotlinx.coroutines.test.runTest
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.GZIPOutputStream

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
   * The whole point of the link: PT_INTERP is baked with a stable /data/data path, but
   * the loader has to be executed out of nativeLibraryDir, whose path is not stable.
   */
  @Test
  fun install_pointsTheLoaderLinkAtTheNativeLibrary() = runTest {
    val nativeLib = tempFolder.newFile("libld-linux.so").apply { writeText("ELF") }
    val installed = installer(version = 1, nativeLoader = nativeLib).installIfNeeded()

    val link = File(installed.baseDir, "lib64/ld-linux-x86-64.so.2")
    assertTrue("expected a symlink at $link", Files.isSymbolicLink(link.toPath()))
    assertEquals(nativeLib.canonicalPath, link.canonicalPath)
  }

  /**
   * nativeLibraryDir carries per-install random path segments, so it changes on every
   * app update while the extracted Space survives. If the link were only written on a
   * fresh extract it would point at the previous install and every update would break
   * exec with ENOENT.
   */
  @Test
  fun install_refreshesTheLoaderLinkEvenWhenTheRootfsIsAlreadyInstalled() = runTest {
    val baseDir = tempFolder.newFolder("base")
    val firstInstall = tempFolder.newFile("old-loader.so").apply { writeText("ELF") }
    installer(version = 1, baseDir = baseDir, nativeLoader = firstInstall).installIfNeeded()

    val afterUpdate = tempFolder.newFile("new-loader.so").apply { writeText("ELF") }
    installer(version = 1, baseDir = baseDir, nativeLoader = afterUpdate).installIfNeeded()

    val link = File(baseDir, "lib64/ld-linux-x86-64.so.2")
    assertEquals(afterUpdate.canonicalPath, link.canonicalPath)
  }

  /** The archive ships its own /lib64 loader symlink; ours has to win. */
  @Test
  fun install_replacesTheLoaderLinkShippedInTheArchive() = runTest {
    val nativeLib = tempFolder.newFile("libld-linux.so").apply { writeText("ELF") }
    val installed = installer(version = 1, nativeLoader = nativeLib).installIfNeeded()

    val link = File(installed.baseDir, "lib64/ld-linux-x86-64.so.2")
    assertEquals(nativeLib.canonicalPath, link.canonicalPath)
  }

  /**
   * The shell has to be reachable before a terminal can be, and the installer is the only thing
   * that runs on every launch - which is when `nativeLibraryDir` may have moved. See
   * [BusyBoxAppletsTest] for why it is all 304 applets and not just `bin/sh`.
   */
  @Test
  fun install_pointsTheShellAtTheNativeBusyBox() = runTest {
    val nativeBusyBox = tempFolder.newFile("libbusybox-${System.nanoTime()}.so").apply { writeText("ELF") }

    val installed = installer(version = 1, nativeBusyBox = nativeBusyBox).installIfNeeded()

    assertEquals(nativeBusyBox.canonicalPath, File(installed.baseDir, "bin/sh").canonicalPath)
  }

  private fun installer(
    version: Int,
    baseDir: File = tempFolder.newFolder("base-$version-${System.nanoTime()}"),
    nativeLoader: File = tempFolder.newFile("loader-${System.nanoTime()}.so"),
    nativeBusyBox: File = tempFolder.newFile("busybox-${System.nanoTime()}.so"),
  ) = BaseSpaceInstaller(
    archive = { rootfsArchive().inputStream() },
    baseDir = baseDir,
    nativeLoader = nativeLoader,
    nativeInterpreter = nativeLoader,
    nativeBusyBox = nativeBusyBox,
    loaderLinkPath = "lib64/ld-linux-x86-64.so.2",
    bundledVersion = version,
  )

  /** Stands in for the real rootfs: the same shapes - a file, the shipped loader link, busybox. */
  private fun rootfsArchive(): ByteArray {
    val bytes = ByteArrayOutputStream()
    TarArchiveOutputStream(GZIPOutputStream(bytes)).use { tar ->
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
      tar.putArchiveEntry(TarArchiveEntry("usr/bin/"))
      tar.closeArchiveEntry()
      tar.putFile("usr/bin/python3.11", "print(1)\n")
      tar.putSymlink("lib64/ld-linux-x86-64.so.2", "/usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2")
      // The image's own busybox: one real ELF, and an applet link pointing absolutely at it.
      tar.putFile("bin/busybox", "ELF")
      tar.putSymlink("bin/sh", "/bin/busybox")
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
