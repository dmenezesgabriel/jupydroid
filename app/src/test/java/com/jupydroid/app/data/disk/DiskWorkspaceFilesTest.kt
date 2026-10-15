package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.port.WorkspaceFiles
import com.jupydroid.app.domain.port.WorkspaceFilesContract
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files

/** The disk's file acts owe the port's contract, plus what only a real filesystem can be asked: links. */
class DiskWorkspaceFilesTest : WorkspaceFilesContract() {
  @get:Rule val temp = TemporaryFolder()

  override fun files(): WorkspaceFiles = DiskWorkspaceFiles(temp.root)

  override fun repository(): WorkspaceRepository = DiskWorkspaceRepository(temp.root)

  override fun given(vararg paths: String) {
    paths.forEach { path ->
      val file = File(temp.root, path)
      if (path.endsWith("/")) file.mkdirs() else file.apply { parentFile?.mkdirs() }.writeText("x")
    }
  }

  /**
   * Renaming a symlink moves **the link**, and leaves what it points at exactly where it was.
   *
   * The workspace lives inside the rootfs, so this is not a hypothetical shape. Both references
   * address a link as a link: jupyter_server joins and `normpath`s without resolving and stats
   * with `os.lstat` (`fileio.py:290-329`, `utils.py:153-162`), and Winlator checks `isSymlink`
   * before it ever walks a directory (`FileUtils.delete:114-119`).
   *
   * `resolve` canonicalises - which is right for refusing an escape and wrong for *acting*, since
   * the canonical path of a link is its target. Moving the target instead of the link is the same
   * defect as `TarZstdExtractor` writing through the stale loader symlink, which crash-looped the
   * app.
   */
  @Test
  fun renamingASymlinkMovesTheLinkAndNotItsTarget() = runTest {
    given("real/inside.txt")
    Files.createSymbolicLink(File(temp.root, "link").toPath(), File(temp.root, "real").toPath())
    val repository = repository()

    files().rename("link", "renamed-link")

    assertTrue("the link should have moved", Files.isSymbolicLink(File(temp.root, "renamed-link").toPath()))
    assertTrue("the target must not have moved", File(temp.root, "real/inside.txt").exists())
    assertEquals(listOf("inside.txt"), repository.list("renamed-link").map { it.name })
  }

  /**
   * Deleting a symlink unlinks **the link**, and what it points at stays - even a full directory.
   *
   * This is the one case where following the link would destroy a tree nobody asked about, and it
   * is why Winlator guards its own recursion with `isSymlink` before `delete` (`FileUtils:114-119`)
   * in a rootfs built out of links. Note it also passes the "not empty" rule for the right reason:
   * a link is not a directory, so there is nothing in it to refuse.
   */
  @Test
  fun deletingASymlinkUnlinksItAndLeavesItsTarget() = runTest {
    given("real/inside.txt")
    Files.createSymbolicLink(File(temp.root, "link").toPath(), File(temp.root, "real").toPath())
    val repository = repository()

    files().delete("link")

    assertEquals(listOf("real"), repository.list("").map { it.name })
    assertTrue("the target must survive", File(temp.root, "real/inside.txt").exists())
  }
}
