package com.jupydroid.app.data.space

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SpaceGnuImageInstallerTest {
  @get:Rule val tempFolder = TemporaryFolder()

  /**
   * An existing Space already has its musl image and its own state; the payload must land
   * beside it - `opt/gnu`, the multiarch libs, the loader - and leave every other byte alone.
   */
  @Test
  fun ensureInstalled_putsThePayloadBesideAnExistingImage() = runTest {
    val space = tempFolder.newFolder("space-1")
    File(space, "usr/bin").mkdirs()
    File(space, "usr/bin/python3").writeText("musl")
    File(space, "home/jupydroid").mkdirs()
    File(space, "home/jupydroid/notes.md").writeText("# Thesis")
    File(space, "pkgs").mkdirs()
    File(space, "pkgs/installed-by-the-space").writeText("the Space's own")

    val written = SpaceGnuImageInstaller(archive = { payload().inputStream() }).ensureInstalled(space)

    assertEquals(3, written)
    assertEquals("elf", File(space, "opt/gnu/python/bin/python3.12").readText())
    assertEquals("libc", File(space, "usr/lib/aarch64-linux-gnu/libc.so.6").readText())
    assertEquals("ld", File(space, "lib/ld-linux-aarch64.so.1").readText())
    assertEquals("musl", File(space, "usr/bin/python3").readText())
    assertEquals("# Thesis", File(space, "home/jupydroid/notes.md").readText())
    assertEquals("the Space's own", File(space, "pkgs/installed-by-the-space").readText())
    assertEquals("1", File(space, ".jupydroid/gnu-version").readText())
  }

  /** A Space that already carries this payload keeps it: the archive is never even opened. */
  @Test
  fun ensureInstalled_leavesACurrentPayloadUntouched() = runTest {
    val space = tempFolder.newFolder("space-1")
    File(space, "opt/gnu/python/bin").mkdirs()
    File(space, "opt/gnu/python/bin/python3.12").writeText("elf")
    File(space, ".jupydroid").mkdirs()
    File(space, ".jupydroid/gnu-version").writeText("1")

    val written = SpaceGnuImageInstaller(
      archive = { error("the archive must not open for an installed payload") },
    ).ensureInstalled(space)

    assertEquals(0, written)
    assertEquals("elf", File(space, "opt/gnu/python/bin/python3.12").readText())
  }

  /**
   * The interpreter is deep in the payload, so the stamp alone cannot tell a half extract from
   * a whole one: a current stamp with the interpreter missing still re-extracts.
   */
  @Test
  fun ensureInstalled_reExtractsWhenTheInterpreterIsMissing() = runTest {
    val space = tempFolder.newFolder("space-1")
    File(space, ".jupydroid").mkdirs()
    File(space, ".jupydroid/gnu-version").writeText("1")

    val written = SpaceGnuImageInstaller(archive = { payload().inputStream() }).ensureInstalled(space)

    assertEquals(3, written)
    assertTrue(File(space, "opt/gnu/python/bin/python3.12").isFile)
  }

  /**
   * A payload bump clears the payload's own paths first: a file only the old payload had must
   * not linger beside the new one, while the Space's own state is never in the cleared set.
   */
  @Test
  fun ensureInstalled_onAVersionBumpClearsOnlyThePayloadsPaths() = runTest {
    val space = tempFolder.newFolder("space-1")
    seedOldPayload(space)
    File(space, "pkgs").mkdirs()
    File(space, "pkgs/user-package").writeText("the Space's own")

    val installer = SpaceGnuImageInstaller(archive = { payload().inputStream() }, bundledVersion = 2)
    val written = installer.ensureInstalled(space)

    assertEquals(3, written)
    assertFalse("a file only the old payload had must not survive the bump", File(space, "opt/gnu/old-only").exists())
    assertEquals("elf", File(space, "opt/gnu/python/bin/python3.12").readText())
    assertEquals("libc", File(space, "usr/lib/aarch64-linux-gnu/libc.so.6").readText())
    assertEquals("musl stays", File(space, "lib/libc.musl-aarch64.so.1").readText())
    assertEquals("the Space's own", File(space, "pkgs/user-package").readText())
    assertEquals("2", File(space, ".jupydroid/gnu-version").readText())
  }

  /** What a version-0 payload left behind, plus the musl loader a bump must never touch. */
  private fun seedOldPayload(space: File) {
    File(space, "opt/gnu/old-only").mkdirs()
    File(space, "opt/gnu/old-only/stale.so").writeText("stale")
    File(space, "opt/gnu/python/bin").mkdirs()
    File(space, "opt/gnu/python/bin/python3.12").writeText("old")
    File(space, "usr/lib/aarch64-linux-gnu").mkdirs()
    File(space, "usr/lib/aarch64-linux-gnu/libc.so.6").writeText("old-libc")
    File(space, "lib").mkdirs()
    File(space, "lib/ld-linux-aarch64.so.1").writeText("old-ld")
    File(space, "lib/libc.musl-aarch64.so.1").writeText("musl stays")
    File(space, ".jupydroid").mkdirs()
    File(space, ".jupydroid/gnu-version").writeText("0")
  }

  private fun payload(): ByteArray = tarZst {
    file("opt/gnu/python/bin/python3.12", "elf")
    file("usr/lib/aarch64-linux-gnu/libc.so.6", "libc")
    file("lib/ld-linux-aarch64.so.1", "ld")
  }
}
