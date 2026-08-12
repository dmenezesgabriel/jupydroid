package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
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

  @Test
  fun savingANotebookMovesItAboveOneCreatedAfterIt() = runTest {
    val repository = repository()
    val edited = repository.createNotebook(title = "Edited", kernelId = "python3")
    repository.createNotebook(title = "Newer", kernelId = "python3")

    repository.saveNotebook(
      repository.readNotebook(edited.id).copy(cells = listOf(Cell("c1", CellKind.CODE, "x = 1"))),
    )

    assertFirst(repository, edited.id, "saving")
  }

  /**
   * A rename supplies a *name*, and the notebook keeps the folder its id names.
   *
   * `Context._rename` replaces the path's last segment and joins the rest back on
   * (`packages/docregistry/src/context.ts:611-614`); the file browser joins onto the directory it
   * is showing (`packages/filebrowser/src/listing.ts:2621`). Both implementations have to agree,
   * because the editor calls this with whatever the rename dialog was given and a name can never
   * contain a `/` - `isValidFileName` refuses one (`packages/docmanager/src/dialogs.ts:257`).
   */
  @Test
  fun renamingANotebookInAFolderKeepsItInThatFolder() = runTest {
    val repository = repository()
    val nested = repository.createNotebook("reports/Draft", "python3")

    assertEquals("reports/Final", repository.renameNotebook(nested.id, "Final"))
  }

  @Test
  fun renamingANotebookMovesItAboveOneCreatedAfterIt() = runTest {
    val repository = repository()
    val renamed = repository.createNotebook(title = "Renamed", kernelId = "python3")
    repository.createNotebook(title = "Newer", kernelId = "python3")

    // The new id, because a rename moves the file and the old one stops naming anything - which
    // is the contract every implementation owes since X1b, fake and disk alike.
    val movedTo = repository.renameNotebook(renamed.id, "Renamed again.ipynb")

    assertFirst(repository, movedTo, "renaming")
  }

  @Test
  fun aNewNotebookIsTheMostRecentlyUpdatedOne() = runTest {
    val repository = repository()
    repository.createNotebook(title = "First", kernelId = "python3")

    val newest = repository.createNotebook(title = "Second", kernelId = "python3")

    assertFirst(repository, newest.id, "creating")
  }

  private suspend fun assertFirst(repository: NotebookRepository, id: String, act: String) {
    val recents = repository.observeNotebooks().first()
    assertTrue(
      "$act must put '$id' at the top of Recents, got ${recents.map { it.title to it.updatedAt }}",
      recents.first().id == id,
    )
  }
}
