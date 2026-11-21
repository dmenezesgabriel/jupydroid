package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * A Space's writable skeleton appears the first time it is launched, from a directory that may hold
 * nothing but `config.json` - which is all `DiskSpaceRepository` writes, and all its scan needs to
 * count the directory as a Space (ADR-0010).
 */
class SpaceDirsTest {
  @get:Rule val tempFolder = TemporaryFolder()

  @Test
  fun ensure_provisionsTheWritableDirsOfASpaceThatHoldsOnlyItsConfig() {
    val spaceDir = configOnlySpace()

    SpaceDirs(spaceDir).ensure()

    listOf("home/jupydroid", "run", "tmp", "pkgs", "pkgs-gnu").forEach { relative ->
      assertTrue("expected $relative under $spaceDir", File(spaceDir, relative).isDirectory)
    }
  }

  @Test
  fun ensure_leavesWhatIsAlreadyThereAlone() {
    val spaceDir = configOnlySpace()
    val notebook = File(spaceDir, "home/jupydroid/kept.ipynb").apply {
      parentFile!!.mkdirs()
      writeText("{}")
    }

    SpaceDirs(spaceDir).ensure()
    SpaceDirs(spaceDir).ensure()

    assertEquals("{}", notebook.readText())
  }

  @Test
  fun ensure_namesTheDirectoryItCouldNotCreate() {
    val spaceDir = configOnlySpace()
    File(spaceDir, "run").writeText("a file where a directory must go")

    val error = assertThrows(IllegalStateException::class.java) { SpaceDirs(spaceDir).ensure() }

    assertTrue(error.message!!, File(spaceDir, "run").path in error.message!!)
  }

  @Test
  fun eachWritableDirIsNamedOnceUnderTheSpace() {
    val spaceDir = File("/data/data/com.jupydroid.app/files/spaces/space-1")

    assertEquals(notebookWorkspaceIn(spaceDir), SpaceDirs(spaceDir).workspace)
    assertEquals(File(spaceDir, "run"), SpaceDirs(spaceDir).runtime)
    assertEquals(File(spaceDir, "tmp"), SpaceDirs(spaceDir).tmp)
    assertEquals(File(spaceDir, "pkgs"), SpaceDirs(spaceDir).pkgs)
    assertEquals(File(spaceDir, "pkgs-gnu"), SpaceDirs(spaceDir).gnuPkgs)
  }

  /** A Space is its own `/` under proot (ADR-0073), so a process names each dir by its path under the Space. */
  @Test
  fun eachWritableDirIsNamedInsideTheRootByItsPathUnderTheSpace() {
    val dirs = SpaceDirs(File("/data/data/com.jupydroid.app/files/spaces/space-1"))

    assertEquals("/home/jupydroid", dirs.inRoot(dirs.workspace))
    assertEquals("/run", dirs.inRoot(dirs.runtime))
    assertEquals("/tmp", dirs.inRoot(dirs.tmp))
    assertEquals("/pkgs", dirs.inRoot(dirs.pkgs))
    assertEquals("/pkgs-gnu", dirs.inRoot(dirs.gnuPkgs))
  }

  @Test
  fun aPathOutsideTheSpaceHasNoNameInsideItsRoot() {
    val dirs = SpaceDirs(File("/data/data/com.jupydroid.app/files/spaces/space-1"))
    val elsewhere = File("/data/data/com.jupydroid.app/files/spaces/base/tmp")

    val error = assertThrows(IllegalArgumentException::class.java) { dirs.inRoot(elsewhere) }

    assertTrue(error.message!!, elsewhere.path in error.message!!)
  }

  /** A killed server removes nothing, so each of these was left by one (ADR-0128). */
  @Test
  fun clearRuntime_removesEveryFileAFormerServerLeftAndKeepsTheDirectory() {
    val dirs = SpaceDirs(configOnlySpace()).apply { ensure() }
    listOf("jpserver-9-open.html", "jpserver-mcp-9.json", "kernel-abc.json", "kernels/k1/conn.json").forEach { name ->
      File(dirs.runtime, name).apply { parentFile!!.mkdirs() }.writeText("stale")
    }

    dirs.clearRuntime()

    assertEquals(emptyList<File>(), dirs.runtime.listFiles()!!.toList())
  }

  @Test
  fun clearRuntime_leavesWhatTheNotebooksAndPackagesHold() {
    val dirs = SpaceDirs(configOnlySpace()).apply { ensure() }
    val kept = listOf(dirs.workspace, dirs.pkgs, dirs.gnuPkgs, dirs.tmp)
      .map { File(it, "kept.txt").apply { writeText("kept") } }

    dirs.clearRuntime()

    assertTrue("expected $kept to survive", kept.all(File::isFile))
  }

  @Test
  fun clearRuntime_isANoOpWhereNothingWasProvisionedYet() {
    val dirs = SpaceDirs(configOnlySpace())

    dirs.clearRuntime()

    assertFalse("clearing must not create ${dirs.runtime}", dirs.runtime.exists())
  }

  private fun configOnlySpace(): File = tempFolder.newFolder("space-1").apply {
    File(this, "config.json").writeText("{}")
  }
}
