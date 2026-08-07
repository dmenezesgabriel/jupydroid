package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
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

  @Test
  fun renamingANotebookMovesItAboveOneCreatedAfterIt() = runTest {
    val repository = repository()
    val renamed = repository.createNotebook(title = "Renamed", kernelId = "python3")
    repository.createNotebook(title = "Newer", kernelId = "python3")

    repository.renameNotebook(renamed.id, "Renamed again")

    assertFirst(repository, renamed.id, "renaming")
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
