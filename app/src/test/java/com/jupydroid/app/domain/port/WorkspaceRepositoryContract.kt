package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.WorkspaceEntryKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [WorkspaceRepository] must do, whoever implements it.
 *
 * The workspace is the directory the kernel calls `HOME` and the server calls `root_dir` (X1a), so
 * this is a *shared* tree: a notebook writes into it, the user browses it, and neither owns it.
 * That shapes the contract more than any preference of ours.
 *
 * Behaviour is JupyterLab's, from `packages/filebrowser`: directories first then natural name
 * order, paths relative to the root, and hidden entries left out - `ContentsManager.allow_hidden`
 * is false by default, so the reference's own browser does not show them either.
 */
abstract class WorkspaceRepositoryContract {
  protected abstract fun repository(): WorkspaceRepository

  /** The path a caller passes and the path it gets back are the same shape: relative to the root. */
  protected abstract fun given(vararg paths: String)

  @Test
  fun listsWhatIsThere() = runTest {
    given("notes.txt", "reports/")

    val listed = repository().list("").map { it.name }

    assertEquals(listOf("reports", "notes.txt"), listed)
  }

  @Test
  fun listsInsideADirectory() = runTest {
    given("reports/summary.ipynb", "reports/data.csv")

    val listed = repository().list("reports").map { it.name }

    assertEquals(listOf("data.csv", "summary.ipynb"), listed)
  }

  @Test
  fun saysWhichEntriesAreDirectories() = runTest {
    given("reports/", "notes.txt")

    val kinds = repository().list("").associate { it.name to it.kind }

    assertEquals(WorkspaceEntryKind.DIRECTORY, kinds["reports"])
    assertEquals(WorkspaceEntryKind.FILE, kinds["notes.txt"])
  }

  /** Paths are relative to the root throughout, the way `Contents.IModel.path` is. */
  @Test
  fun pathsAreRelativeToTheRoot() = runTest {
    given("reports/summary.ipynb")

    val entry = repository().list("reports").single()

    assertEquals("reports/summary.ipynb", entry.path)
  }

  /**
   * Hidden entries are not listed, which is the server's rule as much as the browser's:
   * `ContentsManager.allow_hidden` defaults to false and jupyter_server will not serve them.
   *
   * It also keeps our own bookkeeping out of sight - the migration stamp lives under `.jupydroid`.
   */
  @Test
  fun hiddenEntriesAreNotListed() = runTest {
    given(".hidden", ".jupydroid/stamp", "visible.txt")

    val listed = repository().list("").map { it.name }

    assertEquals(listOf("visible.txt"), listed)
  }

  @Test
  fun anEmptyDirectoryListsNothing() = runTest {
    given("empty/")

    assertTrue(repository().list("empty").isEmpty())
  }

  /**
   * A path outside the workspace is refused rather than served.
   *
   * The root is the kernel's `HOME` inside the app's own storage; everything above it is the
   * rootfs and the app's private files. jupyter_server refuses the same thing - `_validate_path`
   * rejects anything resolving outside `root_dir` - and it must not depend on the UI never asking.
   */
  @Test
  fun aPathEscapingTheRootIsRefused() = runTest {
    given("notes.txt")

    val escaped = runCatching { repository().list("../..") }

    assertTrue("expected a refusal, got $escaped", escaped.isFailure)
  }

  @Test
  fun aPathThatDoesNotExistIsRefused() = runTest {
    given("notes.txt")

    val missing = runCatching { repository().list("nope") }

    assertTrue("expected a refusal, got $missing", missing.isFailure)
  }
}
