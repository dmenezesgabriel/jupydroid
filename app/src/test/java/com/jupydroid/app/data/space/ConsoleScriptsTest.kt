package com.jupydroid.app.data.space

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/**
 * `pip`, `jupyter`, `ipython` and 28 other console scripts ship as `#!/usr/bin/python3` text files
 * in `usr/bin`. SELinux refuses to `execve` any file under `filesDir`
 * (`PythonInterpreterDeviceTest.aScriptUnderFilesDirIsRefusedEvenWithItsExecutableBitSet`), so the
 * kernel never reads the `#!` line. This links each one from `usr/local/bin` - first on `PATH` -
 * to a native trampoline that reads the shebang and execs the interpreter, the fourth use of the
 * pattern `BusyBoxApplets` and `BaseSpaceInstaller` already run three times.
 */
class ConsoleScriptsTest {
  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun relink_pointsTheTrampolineItselfAtTheNativeLibrary() = runTest {
    val base = rootfsWithScripts()
    val nativeTrampoline = tempFolder.newFile("libshebang.so").apply { writeText("ELF") }

    ConsoleScripts(base, nativeTrampoline).relink()

    val trampoline = File(base, "bin/shebang-exec")
    assertTrue("expected a symlink at $trampoline", Files.isSymbolicLink(trampoline.toPath()))
    assertEquals(nativeTrampoline.canonicalPath, trampoline.canonicalPath)
  }

  @Test
  fun relink_linksEveryShebangScriptInUsrBinFromUsrLocalBin() = runTest {
    val base = rootfsWithScripts()

    ConsoleScripts(base, tempFolder.newFile("libshebang.so")).relink()

    listOf("pip", "jupyter", "ldd").forEach { name ->
      val link = File(base, "usr/local/bin/$name")
      assertTrue("$name was not linked", Files.isSymbolicLink(link.toPath()))
    }
  }

  /** An ELF in `usr/bin` - the real interpreter, `getconf`, `patchelf` - is not a console script. */
  @Test
  fun relink_leavesAnElfInUsrBinAlone() = runTest {
    val base = rootfsWithScripts()

    ConsoleScripts(base, tempFolder.newFile("libshebang.so")).relink()

    assertFalse(File(base, "usr/local/bin/python3.12").exists())
  }

  /** A busybox applet symlink in `usr/bin` is not a script either. */
  @Test
  fun relink_leavesAnAppletSymlinkAlone() = runTest {
    val base = rootfsWithScripts()

    ConsoleScripts(base, tempFolder.newFile("libshebang.so")).relink()

    assertFalse(File(base, "usr/local/bin/awk").exists())
  }

  /**
   * Relative, so it can never go stale - only `bin/shebang-exec` is repointed on an app update,
   * exactly as `BusyBoxApplets` keeps one maintained link rather than 305.
   */
  @Test
  fun relink_makesTheUsrLocalBinLinksRelative() = runTest {
    val base = rootfsWithScripts()

    ConsoleScripts(base, tempFolder.newFile("libshebang.so")).relink()

    assertEquals(
      "../../../bin/shebang-exec",
      Files.readSymbolicLink(File(base, "usr/local/bin/pip").toPath()).toString(),
    )
  }

  @Test
  fun relink_repairsTheTrampolineAfterTheNativeLibraryDirectoryMoves() = runTest {
    val base = rootfsWithScripts()
    ConsoleScripts(base, tempFolder.newFile("old-libshebang.so")).relink()

    val afterUpdate = tempFolder.newFile("new-libshebang.so").apply { writeText("ELF") }
    ConsoleScripts(base, afterUpdate).relink()

    assertEquals(afterUpdate.canonicalPath, File(base, "usr/local/bin/pip").canonicalPath)
  }

  /** **The second-install case every install-time path owes a test for.** */
  @Test
  fun relink_isIdempotent() = runTest {
    val base = rootfsWithScripts()
    val nativeTrampoline = tempFolder.newFile("libshebang.so").apply { writeText("ELF") }

    val first = ConsoleScripts(base, nativeTrampoline).relink()
    val second = ConsoleScripts(base, nativeTrampoline).relink()

    assertEquals(first, second)
    assertEquals(
      "../../../bin/shebang-exec",
      Files.readSymbolicLink(File(base, "usr/local/bin/pip").toPath()).toString(),
    )
  }

  /** Counted, because the number is how many console scripts the shell can then run by name. */
  @Test
  fun relink_reportsHowManyScriptsItLinked() = runTest {
    val base = rootfsWithScripts()

    assertEquals(3, ConsoleScripts(base, tempFolder.newFile("libshebang.so")).relink())
  }

  /** The shapes `usr/bin` actually holds: `#!` scripts, an ELF, and applet symlinks. */
  private fun rootfsWithScripts(): File {
    val base = tempFolder.newFolder("base-${System.nanoTime()}")
    File(base, "bin").mkdirs()
    File(base, "usr/bin").mkdirs()
    File(base, "usr/bin/pip").writeText("#!/usr/bin/python3\nimport pip\n")
    File(base, "usr/bin/jupyter").writeText("#!/usr/bin/python3\nfrom jupyter_core import main\n")
    File(base, "usr/bin/ldd").writeText("#!/bin/sh\necho ldd\n")
    File(base, "usr/bin/python3.12").writeText("ELF and the rest of a binary")
    Files.createSymbolicLink(File(base, "usr/bin/awk").toPath(), File("../../bin/busybox").toPath())
    return base
  }
}
