package com.jupydroid.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/** What a backup carries out of a real `filesDir`, as paths relative to it. */
class SpaceHomeBackupScopeTest {
  @get:Rule val temp = TemporaryFolder()

  private val filesDir: File get() = temp.root

  private fun write(relative: String): File = File(filesDir, relative).apply {
    parentFile?.mkdirs()
    writeText(relative)
  }

  private fun backedUp(): Set<String> =
    SpaceHomeBackupScope(filesDir).filesToBackUp().map { it.relativeTo(filesDir).invariantSeparatorsPath }.toSet()

  /** What the user made is the point of the backup, however deep it sits. */
  @Test
  fun aHomesDocumentsAreBackedUp() {
    write("spaces/base/home/jupydroid/survives.ipynb")
    write("spaces/base/home/jupydroid/notes/deep/notes.md")
    write("spaces/base/home/jupydroid/talk.chat")

    assertEquals(
      setOf(
        "spaces/base/home/jupydroid/survives.ipynb",
        "spaces/base/home/jupydroid/notes/deep/notes.md",
        "spaces/base/home/jupydroid/talk.chat",
      ),
      backedUp(),
    )
  }

  /** A sign-in a program wrote under the home is what a reinstall would otherwise lose. */
  @Test
  fun aSignInUnderTheHomeIsBackedUp() {
    write("spaces/base/home/jupydroid/.local/share/opencode/auth.json")

    assertEquals(setOf("spaces/base/home/jupydroid/.local/share/opencode/auth.json"), backedUp())
  }

  /** Every Space's record and home, not just base's. */
  @Test
  fun eachSpacesConfigAndHomeAreBackedUp() {
    write("spaces/base/config.json")
    write("spaces/space-2/config.json")
    write("spaces/space-2/home/jupydroid/a.py")

    assertEquals(
      setOf("spaces/base/config.json", "spaces/space-2/config.json", "spaces/space-2/home/jupydroid/a.py"),
      backedUp(),
    )
  }

  /** A venv is rebuilt with one `pip install`, and a numpy one alone is past the 25 MB quota. */
  @Test
  fun aVirtualEnvironmentAtAnyDepthIsLeftOut() {
    write("spaces/base/home/jupydroid/.venv/pyvenv.cfg")
    write("spaces/base/home/jupydroid/.venv/lib/python3.12/site-packages/numpy/__init__.py")
    write("spaces/base/home/jupydroid/project/env/pyvenv.cfg")
    write("spaces/base/home/jupydroid/project/env/bin/python")
    write("spaces/base/home/jupydroid/project/main.py")

    assertEquals(setOf("spaces/base/home/jupydroid/project/main.py"), backedUp())
  }

  /** A directory is a venv by its `pyvenv.cfg`, never by its name. */
  @Test
  fun aDirectoryNamedVenvWithoutPyvenvCfgIsBackedUp() {
    write("spaces/base/home/jupydroid/venv/notes.txt")

    assertEquals(setOf("spaces/base/home/jupydroid/venv/notes.txt"), backedUp())
  }

  /** `~/.cache` is a copy of something elsewhere by definition. */
  @Test
  fun theHomesCacheIsLeftOut() {
    write("spaces/base/home/jupydroid/.cache/pip/wheel.whl")

    assertEquals(emptySet<String>(), backedUp())
  }

  /** The rootfs is re-extracted from the APK; what blows the quota is what a reinstall rebuilds. */
  @Test
  fun theRootfsAndTheSpacesRuntimeDirectoriesAreLeftOut() {
    write("spaces/base/usr/bin/python3")
    write("spaces/base/usr/local/bin/opencode")
    write("spaces/base/pkgs/lib/site.py")
    write("spaces/base/run/jpserver-1.json")
    write("spaces/base/tmp/sigsys.log")
    write("spaces/base/.jupydroid/rootfs-version")

    assertEquals(emptySet<String>(), backedUp())
  }

  /** A link points outside what the backup means to carry, so it is neither copied nor followed. */
  @Test
  fun aSymlinkIsNeitherBackedUpNorFollowed() {
    val target = write("spaces/base/usr/share/big.bin")
    val home = File(filesDir, "spaces/base/home/jupydroid").apply { mkdirs() }
    Files.createSymbolicLink(File(home, "big.bin").toPath(), target.toPath())
    Files.createSymbolicLink(File(home, "usr").toPath(), File(filesDir, "spaces/base/usr").toPath())

    assertEquals(emptySet<String>(), backedUp())
  }

  /** The app's settings and recents, and nothing else that happens to sit at the top. */
  @Test
  fun theAppsStateFilesAreBackedUp() {
    val stateFiles = listOf(
      "recent-documents.json",
      "theme-settings.json",
      "editor-settings.json",
      "terminal-settings.json",
      "explorer-settings.json",
      "launcher-settings.json",
    )
    stateFiles.forEach(::write)
    write("stray.tmp")
    write("notebooks/legacy.ipynb")

    assertEquals(stateFiles.toSet(), backedUp())
  }

  /** A first launch has no Space yet, and the scope must not fail on it. */
  @Test
  fun aFilesDirWithNoSpacesIsEmpty() {
    assertTrue(backedUp().isEmpty())
  }
}
