package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.WorkspaceRepositoryContract
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.Files
import java.time.Instant

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
   * `updatedAt` is the file's **own mtime**, not the moment it was listed.
   *
   * This is A6's rule, moved to where the reference keeps it: `last_modified` is the mtime
   * (`filemanager.py:285`), which is what makes an edit, a run and a rename all count without
   * anything having to remember to say so. The contract can only assert that a freshly written
   * file reads as recent - a backdated one is what tells the two apart, and it is exactly the
   * fixture `a6-edit-updates-recents` backdates to 2020 for the same reason.
   */
  @Test
  fun updatedAtIsTheFilesOwnMtimeRatherThanTheTimeItWasListed() = runTest {
    given("stale.txt")
    val longAgo = Instant.parse("2020-01-01T00:00:00Z")
    assertTrue(File(temp.root, "stale.txt").setLastModified(longAgo.toEpochMilli()))

    val listed = repository().list("").single()

    assertEquals(longAgo, listed.updatedAt)
  }

  /**
   * A write replaces the file rather than rewriting it where it sits, so a kill mid-save leaves the
   * file before the save, never half of it (ADR-0089). A hard link taken first still names the old
   * inode: rewritten in place, it would read the new text too.
   */
  @Test
  fun writeText_replacesTheFileRatherThanRewritingIt() = runTest {
    given("notes.txt")
    val link = linkTakenBeforeTheSave("notes.txt")

    repository().writeText("notes.txt", "after")

    assertEquals("x", link.readText())
    assertEquals("after", File(temp.root, "notes.txt").readText())
  }

  @Test
  fun writeBytes_replacesTheFileRatherThanRewritingIt() = runTest {
    given("export.html")
    val link = linkTakenBeforeTheSave("export.html")

    repository().writeBytes("export.html", "after".toByteArray())

    assertEquals("x", link.readText())
    assertEquals("after", File(temp.root, "export.html").readText())
  }

  @Test
  fun writeText_leavesNothingButTheFileBesideIt() = runTest {
    repository().writeText("notes.txt", "after")

    assertEquals(listOf("notes.txt"), temp.root.list()?.toList())
  }

  private fun linkTakenBeforeTheSave(path: String): File = File(temp.root, "taken-before-the-save").also {
    Files.createLink(it.toPath(), File(temp.root, path).toPath())
  }
}
