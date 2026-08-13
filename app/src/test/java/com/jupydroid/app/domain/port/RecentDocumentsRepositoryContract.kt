package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.MAX_RECENTS
import com.jupydroid.app.domain.model.RecentDocument
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [RecentDocumentsRepository] must do, whoever implements it.
 *
 * This is `RecentsManager`'s job (`packages/docmanager/src/recents.ts`): hold the paths that were
 * opened, most recent first, once each, capped - and hand them back to whatever is showing them.
 * The ordering rules themselves are pure and tested in `RecentDocumentsTest`; what is owed *here*
 * is that a repository applies them and that what it returns survives being asked twice.
 *
 * Deliberately not in the contract: **validation**. `_isValid` asks the Contents API whether the
 * path still exists (`:169-181`), which only an implementation with a filesystem behind it can
 * answer - so it is asked of the disk adapter, where there is a workspace to delete a file from.
 */
abstract class RecentDocumentsRepositoryContract {
  protected abstract fun repository(): RecentDocumentsRepository

  @Test
  fun remembersWhatWasOpened() = runTest {
    val repository = repository()

    repository.record(document("notes.ipynb"))

    assertEquals(listOf("notes.ipynb"), repository.observeRecents().first().map { it.path })
  }

  @Test
  fun theMostRecentIsFirst() = runTest {
    val repository = repository()

    repository.record(document("first.ipynb"))
    repository.record(document("second.ipynb"))

    assertEquals(listOf("second.ipynb", "first.ipynb"), repository.observeRecents().first().map { it.path })
  }

  /** `addRecent` splices out the existing index before it unshifts, so a re-open moves a row. */
  @Test
  fun openingSomethingTwiceLeavesOneEntry() = runTest {
    val repository = repository()

    repository.record(document("a.ipynb"))
    repository.record(document("b.ipynb"))
    repository.record(document("a.ipynb"))

    assertEquals(listOf("a.ipynb", "b.ipynb"), repository.observeRecents().first().map { it.path })
  }

  @Test
  fun stopsAtTheCap() = runTest {
    val repository = repository()

    (1..MAX_RECENTS + 5).forEach { repository.record(document("n$it.ipynb")) }

    val paths = repository.observeRecents().first().map { it.path }
    assertEquals(MAX_RECENTS, paths.size)
    assertEquals("n${MAX_RECENTS + 5}.ipynb", paths.first())
  }

  /** A directory is remembered as one - the reference records the containing folder too. */
  @Test
  fun aDirectoryIsRememberedAsADirectory() = runTest {
    val repository = repository()

    repository.record(RecentDocument("reports", WorkspaceEntryKind.DIRECTORY))

    assertEquals(WorkspaceEntryKind.DIRECTORY, repository.observeRecents().first().single().kind)
  }

  /** `docmanager:clear-recents`, which the reference offers from the Open Recent submenu. */
  @Test
  fun clearingForgetsEverything() = runTest {
    val repository = repository()
    repository.record(document("a.ipynb"))
    repository.record(document("b.ipynb"))

    repository.clear()

    assertTrue(repository.observeRecents().first().isEmpty())
  }

  @Test
  fun startsEmpty() = runTest {
    assertTrue(repository().observeRecents().first().isEmpty())
  }

  private fun document(path: String) = RecentDocument(path, WorkspaceEntryKind.FILE)
}
