package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.port.DocumentCheckpoints
import com.jupydroid.app.domain.port.DocumentCheckpointsContract
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The disk workspace's checkpoints, held to the fake's, and kept where jupyter_server keeps its own -
 * so a server started on the same tree lists the checkpoint this app made, and the other way round.
 */
class DiskDocumentCheckpointsTest : DocumentCheckpointsContract() {
  // Created here rather than by the rule, so the two adapters below can be built over its root as
  // the contract's properties are initialised - a `@Rule` folder exists only once the test starts.
  private val root: File = Files.createTempDirectory("checkpoints").toFile()

  override val workspace: WorkspaceRepository = DiskWorkspaceRepository(root)
  override val checkpoints: DocumentCheckpoints = DiskDocumentCheckpoints(root)

  @After
  fun removeTheTree() {
    root.deleteRecursively()
  }

  /** `checkpoint_path`: `<parent>/.ipynb_checkpoints/<basename>-checkpoint<ext>`. */
  @Test
  fun aCheckpointLiesWhereTheServerLooksForIt() = runTest {
    File(root, "reports").mkdir()
    workspace.writeText("reports/Analysis.ipynb", "{}")

    checkpoints.create("reports/Analysis.ipynb")

    val expected = File(root, "reports/.ipynb_checkpoints/Analysis-checkpoint.ipynb")
    assertEquals("{}", expected.readText())
  }

  /** The id is the server's, so a checkpoint the server made is one this app restores. */
  @Test
  fun aCheckpointTheServerMadeIsListedAndRestored() = runTest {
    workspace.writeText("notes.py", "edited since")
    File(root, ".ipynb_checkpoints").mkdir()
    File(root, ".ipynb_checkpoints/notes-checkpoint.py").writeText("the server's")

    val listed = checkpoints.list("notes.py").single()
    checkpoints.restore("notes.py", listed.id)

    assertEquals("the server's", workspace.readText("notes.py"))
  }

  /** A dot-directory, which the listing leaves out as `allow_hidden=False` does. */
  @Test
  fun aCheckpointIsNotListedAsAnEntry() = runTest {
    workspace.writeText("notes.py", "saved")

    checkpoints.create("notes.py")

    assertEquals(listOf("notes.py"), workspace.list("").map { it.name })
  }

  /** A file with no extension keeps none, as `os.path.splitext` gives it none. */
  @Test
  fun aFileWithNoExtensionIsCheckpointedWithNone() = runTest {
    workspace.writeText("Makefile", "all:")

    checkpoints.create("Makefile")

    assertTrue(File(root, ".ipynb_checkpoints/Makefile-checkpoint").isFile)
  }
}
