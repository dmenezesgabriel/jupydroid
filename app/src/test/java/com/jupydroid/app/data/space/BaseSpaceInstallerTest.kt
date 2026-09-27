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

  /**
   * P7e-1: the shell could not run `python3` at all, and the link is what fixes it.
   *
   * `usr/bin/python3.12` is the one real ELF, and `tools/rootfs/stage-app-artifacts.sh:27-28`
   * stages *that file* as `libpython3.so` - so pointing the rootfs name at the native library
   * points it at its own bytes, with the one difference that matters: the target carries an
   * SELinux context that permits execve and an executable bit, and nothing under `filesDir` does
   * (`docs/DECISIONS.md`, "Exec model").
   */
  @Test
  fun install_pointsTheInterpreterLinkAtTheNativeLibrary() = runTest {
    val nativeInterpreter = tempFolder.newFile("libpython3-${System.nanoTime()}.so").apply { writeText("ELF") }

    val installed = installer(version = 1, nativeInterpreter = nativeInterpreter).installIfNeeded()

    val link = File(installed.baseDir, "usr/bin/python3.12")
    assertTrue("expected a symlink at $link", Files.isSymbolicLink(link.toPath()))
    assertEquals(nativeInterpreter.canonicalPath, link.canonicalPath)
  }

  /**
   * **Which name the link carries is decided by `PATH`, not by taste.** `SpaceRuntime:42` orders it
   * `usr/local/bin:usr/bin:bin`, so a link made in `bin/` - which is where `docs/PLAN.md`'s P7e-1
   * row said to put it - is shadowed by the `usr/bin/python3` the image already ships and is never
   * reached. Replacing the ELF the shipped links point *at* is what makes all three names work,
   * and it is `bin/busybox`'s own shape.
   */
  @Test
  fun install_makesEveryShippedPythonNameResolveThroughTheLink() = runTest {
    val nativeInterpreter = tempFolder.newFile("libpython3-${System.nanoTime()}.so").apply { writeText("ELF") }

    val installed = installer(version = 1, nativeInterpreter = nativeInterpreter).installIfNeeded()

    listOf("usr/bin/python3", "usr/bin/python").forEach { name ->
      assertEquals(
        "$name does not reach the interpreter",
        nativeInterpreter.canonicalPath,
        File(installed.baseDir, name).canonicalPath,
      )
    }
  }

  /**
   * P7e-2: a `#!` console script cannot be `execve`'d out of `filesDir`, so `usr/local/bin/pip`
   * links to the trampoline instead. See [ConsoleScriptsTest] for the shapes it does and does not
   * link; here it is only that the installer runs it.
   */
  @Test
  fun install_linksTheConsoleScriptsThroughTheTrampoline() = runTest {
    val nativeTrampoline = tempFolder.newFile("libshebang-${System.nanoTime()}.so").apply { writeText("ELF") }

    val installed = installer(version = 1, nativeTrampoline = nativeTrampoline).installIfNeeded()

    assertEquals(
      nativeTrampoline.canonicalPath,
      File(installed.baseDir, "usr/local/bin/pip").canonicalPath,
    )
  }

  /** The loader link's reason, from the other side: nativeLibraryDir moves on every app update. */
  @Test
  fun install_refreshesTheInterpreterLinkEvenWhenTheRootfsIsAlreadyInstalled() = runTest {
    val baseDir = tempFolder.newFolder("base")
    val firstInstall = tempFolder.newFile("old-libpython3.so").apply { writeText("ELF") }
    installer(version = 1, baseDir = baseDir, nativeInterpreter = firstInstall).installIfNeeded()

    val afterUpdate = tempFolder.newFile("new-libpython3.so").apply { writeText("ELF") }
    installer(version = 1, baseDir = baseDir, nativeInterpreter = afterUpdate).installIfNeeded()

    assertEquals(afterUpdate.canonicalPath, File(baseDir, "usr/bin/python3").canonicalPath)
  }

  /**
   * A dangling link is the failure this guard exists for, and it is invisible until a shell tries
   * to run it: the loader's guard names `useLegacyPackaging` for the same reason.
   */
  @Test
  fun install_refusesAnInterpreterThatIsNotThere() = runTest {
    val missing = File(tempFolder.newFolder("gone-${System.nanoTime()}"), "libpython3.so")

    val refusal = runCatching { installer(version = 1, nativeInterpreter = missing).installIfNeeded() }

    assertEquals(missing.path, refusal.exceptionOrNull()?.message?.substringAfter("'")?.substringBefore("'"))
  }

  private fun installer(
    version: Int,
    baseDir: File = tempFolder.newFolder("base-$version-${System.nanoTime()}"),
    nativeLoader: File = tempFolder.newFile("loader-${System.nanoTime()}.so"),
    nativeInterpreter: File = tempFolder.newFile("python3-${System.nanoTime()}.so"),
    nativeBusyBox: File = tempFolder.newFile("busybox-${System.nanoTime()}.so"),
    nativeTrampoline: File = tempFolder.newFile("shebang-${System.nanoTime()}.so"),
  ) = BaseSpaceInstaller(
    archive = { rootfsArchive().inputStream() },
    baseDir = baseDir,
    nativeLoader = nativeLoader,
    nativeInterpreter = nativeInterpreter,
    nativeBusyBox = nativeBusyBox,
    nativeTrampoline = nativeTrampoline,
    loaderLinkPath = "lib64/ld-linux-x86-64.so.2",
    bundledVersion = version,
  )

  /** Stands in for the real rootfs: the same shapes - a file, the shipped loader link, busybox. */
  private fun rootfsArchive(): ByteArray {
    val bytes = ByteArrayOutputStream()
    TarArchiveOutputStream(ZstdCompressorOutputStream(bytes)).use { tar ->
      tar.setLongFileMode(TarArchiveOutputStream.LONGFILE_POSIX)
      tar.putArchiveEntry(TarArchiveEntry("usr/bin/"))
      tar.closeArchiveEntry()
      tar.putFile("usr/bin/python3.11", "print(1)\n")
      tar.putSymlink("lib64/ld-linux-x86-64.so.2", "/usr/lib/x86_64-linux-gnu/ld-linux-x86-64.so.2")
      // The image's own busybox: one real ELF, and an applet link pointing absolutely at it.
      tar.putFile("bin/busybox", "ELF")
      tar.putSymlink("bin/sh", "/bin/busybox")
      // The image's own python, in the three shapes `tar -tzf` reports: one real ELF and two
      // *relative* links onto it. The relativeness is the point - it is what lets the installer
      // maintain one link and leave the other two alone (BusyBoxApplets' own rule).
      tar.putFile("usr/bin/python3.12", "ELF")
      tar.putSymlink("usr/bin/python3", "python3.12")
      tar.putSymlink("usr/bin/python", "python3")
      // A console script, in the shape the image ships them: a `#!/usr/bin/python3` text file.
      tar.putFile("usr/bin/pip", "#!/usr/bin/python3\nimport pip\n")
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
