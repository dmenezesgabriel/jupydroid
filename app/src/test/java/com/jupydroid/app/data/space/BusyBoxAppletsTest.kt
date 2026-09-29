package com.jupydroid.app.data.space

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * The rootfs ships **304** applet links and every one is the same absolute `-> /bin/busybox`,
 * which on device resolves against Android's root rather than the Space. Repairing only `bin/sh`
 * would leave a shell in which `ls`, `cat`, `grep` and `vi` are all *not found* - and the obvious
 * gate would not catch it, because `echo` is an ash builtin.
 *
 */
class BusyBoxAppletsTest {
  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun relink_pointsBusyBoxItselfAtTheNativeLibrary() = runTest {
    val base = rootfsWithApplets()
    val nativeBusyBox = tempFolder.newFile("libbusybox.so").apply { writeText("ELF") }

    BusyBoxApplets(base, nativeBusyBox).relink()

    val busybox = File(base, "bin/busybox")
    assertTrue("expected a symlink at $busybox", Files.isSymbolicLink(busybox.toPath()))
    assertEquals(nativeBusyBox.canonicalPath, busybox.canonicalPath)
  }

  /** `bin/ls` and friends resolve to the native library through `bin/busybox`, not past it. */
  @Test
  fun relink_makesEveryAppletResolveThroughBusyBox() = runTest {
    val base = rootfsWithApplets()
    val nativeBusyBox = tempFolder.newFile("libbusybox.so").apply { writeText("ELF") }

    BusyBoxApplets(base, nativeBusyBox).relink()

    listOf("bin/sh", "bin/ls", "usr/bin/awk").forEach { applet ->
      assertEquals("$applet does not reach busybox", nativeBusyBox.canonicalPath, File(base, applet).canonicalPath)
    }
  }

  /**
   * **One maintained link, not 305.** `nativeLibraryDir` moves on every app update, so a relative
   * applet can never go stale - only `bin/busybox` has to be repointed, which is what makes running
   * this on every launch cheap.
   */
  @Test
  fun relink_leavesTheAppletsRelativeSoOnlyBusyBoxCanGoStale() = runTest {
    val base = rootfsWithApplets()

    BusyBoxApplets(base, tempFolder.newFile("libbusybox.so")).relink()

    assertEquals("busybox", Files.readSymbolicLink(File(base, "bin/ls").toPath()).toString())
    assertEquals("../../bin/busybox", Files.readSymbolicLink(File(base, "usr/bin/awk").toPath()).toString())
  }

  @Test
  fun relink_repairsBusyBoxAfterTheNativeLibraryDirectoryMoves() = runTest {
    val base = rootfsWithApplets()
    BusyBoxApplets(base, tempFolder.newFile("old-libbusybox.so")).relink()

    val afterUpdate = tempFolder.newFile("new-libbusybox.so").apply { writeText("ELF") }
    BusyBoxApplets(base, afterUpdate).relink()

    assertEquals(afterUpdate.canonicalPath, File(base, "bin/ls").canonicalPath)
  }

  /** Nothing else in the rootfs is a busybox applet, and the walk must not claim it. */
  @Test
  fun relink_leavesLinksThatArePointingSomewhereElseAlone() = runTest {
    val base = rootfsWithApplets()

    BusyBoxApplets(base, tempFolder.newFile("libbusybox.so")).relink()

    assertEquals("/proc/mounts", Files.readSymbolicLink(File(base, "etc/mtab").toPath()).toString())
  }

  /** Counted, because the number is the reason this walks the tree instead of naming `bin/sh`. */
  @Test
  fun relink_reportsHowManyAppletsItRepaired() = runTest {
    val base = rootfsWithApplets()

    assertEquals(3, BusyBoxApplets(base, tempFolder.newFile("libbusybox.so")).relink())
  }

  /** The shapes the shipped tarball actually has: `bin/busybox` real, every applet absolute. */
  private fun rootfsWithApplets(): File {
    val base = tempFolder.newFolder("base-${System.nanoTime()}")
    File(base, "bin").mkdirs()
    File(base, "usr/bin").mkdirs()
    File(base, "etc").mkdirs()
    File(base, "bin/busybox").writeText("ELF")
    listOf("bin/sh", "bin/ls", "usr/bin/awk").forEach { applet ->
      Files.createSymbolicLink(File(base, applet).toPath(), File("/bin/busybox").toPath())
    }
    Files.createSymbolicLink(File(base, "etc/mtab").toPath(), File("/proc/mounts").toPath())
    return base
  }
}
