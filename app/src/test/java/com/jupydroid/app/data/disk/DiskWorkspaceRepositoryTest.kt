package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.WorkspaceEntryKind
import com.jupydroid.app.domain.port.WorkspaceRepository
import com.jupydroid.app.domain.port.WorkspaceRepositoryContract
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
