package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.KernelSpec
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What every [NotebookRepository] must do, whoever implements it.
 *
 * Run against the fake and the disk one alike: a port whose implementations agree only by
 * inspection is a port that will drift, and both were wrong about the same thing here - a
 * notebook you edited kept the timestamp it was created with.
 *
 * **Every write is an update.** Jupyter takes `last_modified` straight off the file
 * (`jupyter_server/services/contents/filemanager.py:285`, `os.lstat().st_mtime`), so editing,
 * running, renaming and creating all move a notebook to the top of Recents because they all
 * write the file. There is no notion of an edit that does not count.
 *
 * Written as *ordering* rather than as a comparison against the clock, because that is the
 * behaviour a person sees and because it needs no clock to inject and no sleep to be reliable:
 * touch the older notebook and it must not still sort below the newer one.
 */
abstract class NotebookRepositoryContract {
  protected abstract fun repository(): NotebookRepository

  /**
   * An edit is written where it can be read back, which is all a save owes.
   *
   * This used to assert the edit also moved the notebook to the top of Recents. That ordering was
   * a property of Recents-as-a-scan and went with it; when a file was last written is now
   * `WorkspaceEntry.updatedAt`, and `WorkspaceRepositoryContract` holds every implementation to it.
   */
  @Test
  fun savingANotebookKeepsTheEdit() = runTest {
    val repository = repository()
    val edited = repository.createNotebook(title = "Edited", kernel = BASE_SPACE.kernels.single())

    repository.saveNotebook(
      repository.readNotebook(edited.id).copy(cells = listOf(Cell("c1", CellKind.CODE, "x = 1"))),
    )

    assertEquals(listOf("x = 1"), repository.readNotebook(edited.id).cells.map { it.source })
  }

  /** A new notebook records the kernel it was created for, whichever the Space's default is. */
  @Test
  fun aNewNotebookRecordsItsKernel() = runTest {
    val repository = repository()

    val created = repository.createNotebook(
      title = "Thesis",
      kernel = KernelSpec("xpython", "Python 3.13 (XPython)", "python"),
    )

    assertEquals("xpython", repository.readNotebook(created.id).kernelId)
  }
}
