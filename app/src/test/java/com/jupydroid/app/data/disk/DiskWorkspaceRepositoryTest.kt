package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.WorkspaceRepositoryContract
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** The disk workspace owes the port's contract, plus what only a real filesystem can be asked. */
class DiskWorkspaceRepositoryTest : WorkspaceRepositoryContract() {
  @get:Rule val temp = TemporaryFolder()

  override fun repository(): WorkspaceRepository = DiskWorkspaceRepository(temp.root)

  override fun given(vararg paths: String) {
    paths.forEach { path ->
      val file = File(temp.root, path)
      if (path.endsWith("/")) file.mkdirs() else file.apply { parentFile?.mkdirs() }.writeText("x")
    }
  }

  /**
   * A symlinked directory is listed as a directory, and its target's contents are what it shows.
   *
   * The workspace sits inside a rootfs, which is built out of symlinks - Winlator's browser
   * resolves them explicitly before listing (`FileInfo.list`, `getLinkFile()`) for exactly this
   * reason. `File.isDirectory` already follows the link; what must not happen is a link being
   * reported as a plain file and refusing to open.
   */
  @Test
  fun aSymlinkedDirectoryIsADirectory() = runTest {
    given("real/inside.txt")
    Files.createSymbolicLink(File(temp.root, "link").toPath(), File(temp.root, "real").toPath())

    val kinds = repository().list("").associate { it.name to it.kind }

    assertEquals(WorkspaceEntryKind.DIRECTORY, kinds["link"])
    assertEquals(listOf("inside.txt"), repository().list("link").map { it.name })
  }

  /** A link pointing nowhere is listed rather than crashing the browse it appears in. */
  @Test
  fun aBrokenSymlinkIsStillListed() = runTest {
    Files.createSymbolicLink(File(temp.root, "dangling").toPath(), File(temp.root, "gone").toPath())

    assertEquals(listOf("dangling"), repository().list("").map { it.name })
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
   * defect as `TarGzExtractor` writing through the stale loader symlink (docs/PLAN.md, residual
   * risks), which crash-looped the app.
   */
  @Test
  fun renamingASymlinkMovesTheLinkAndNotItsTarget() = runTest {
    given("real/inside.txt")
    Files.createSymbolicLink(File(temp.root, "link").toPath(), File(temp.root, "real").toPath())
    val repository = repository()

    repository.rename("link", "renamed-link")

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

    repository.delete("link")

    assertEquals(listOf("real"), repository.list("").map { it.name })
    assertTrue("the target must survive", File(temp.root, "real/inside.txt").exists())
  }
}
