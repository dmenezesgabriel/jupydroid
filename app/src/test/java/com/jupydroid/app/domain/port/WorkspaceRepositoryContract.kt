package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.WorkspaceEntryKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

/**
 * What every [WorkspaceRepository] must do, whoever implements it.
 *
 * The workspace is the directory the kernel calls `HOME` and the server calls `root_dir`, so
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
   * Every entry says when it was last modified, folders included.
   *
   * `DirListing` renders a `last_modified` column and it is **shown by default** - only
   * `file_size` and `date_created` start in `_hiddenColumns` (`listing.ts:259-261`) - so this is
   * part of listing a directory rather than an extra somebody asks for. It is also where A6's rule
   * lives after Recents stops being a list of notebooks: `last_modified` is the file's mtime
   * (`filemanager.py:285`), so an edit, a run and a rename all move it without anyone remembering
   * to say so.
   */
  @Test
  fun everyEntrySaysWhenItWasLastModified() = runTest {
    given("reports/", "notes.txt")
    val listedAround = Instant.now()

    val listed = repository().list("")

    assertEquals(2, listed.size)
    listed.forEach { entry ->
      val age = Duration.between(entry.updatedAt, listedAround).abs()
      assertTrue("${entry.name} reported ${entry.updatedAt}, which is not a time it was just given", age < RECENT)
    }
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
  fun textIsReadBackWholeAndUnchanged() = runTest {
    given("notes.txt")

    assertEquals("x", repository().readText("notes.txt"))
  }

  @Test
  fun writingReplacesTheWholeFile() = runTest {
    given("notes.txt")
    val repository = repository()

    repository.writeText("notes.txt", "rewritten\nover two lines\n")

    assertEquals("rewritten\nover two lines\n", repository.readText("notes.txt"))
  }

  /** A file the editor creates has to be readable by the same path it was written under. */
  @Test
  fun writingAFileThatIsNotThereCreatesIt() = runTest {
    given("keep.txt")
    val repository = repository()

    repository.writeText("fresh.txt", "new")

    assertEquals("new", repository.readText("fresh.txt"))
    assertTrue("fresh.txt" in repository.list("").map { it.name })
  }

  /**
   * An nbconvert export is not always text: a notebook with an image output converted to
   * `markdown` comes back as a **zip**, and a zip written
   * through a `String` is a corrupt zip.
   */
  @Test
  fun bytesAreWrittenAndReadBackWithoutGoingThroughAString() = runTest {
    val repository = repository()

    repository.writeBytes("report.zip", ZIP_HEADER)

    assertTrue("report.zip" in repository.list("").map { it.name })
    assertArrayEquals(ZIP_HEADER, repository.readBytes("report.zip"))
  }

  /** The same rule [writingReplacesTheWholeFile] states, for the byte form. */
  @Test
  fun writingBytesReplacesTheWholeFile() = runTest {
    given("notes.txt")
    val repository = repository()

    repository.writeBytes("notes.txt", ZIP_HEADER)

    assertArrayEquals(ZIP_HEADER, repository.readBytes("notes.txt"))
  }

  @Test
  fun writingBytesOutsideTheRootIsRefused() = runTest {
    given("notes.txt")

    val escaped = runCatching { repository().writeBytes("../escaped.zip", ZIP_HEADER) }

    assertTrue("expected a refusal, got $escaped", escaped.isFailure)
  }

  @Test
  fun readingOutsideTheRootIsRefused() = runTest {
    given("notes.txt")

    val escaped = runCatching { repository().readText("../../secrets") }

    assertTrue("expected a refusal, got $escaped", escaped.isFailure)
  }

  @Test
  fun writingOutsideTheRootIsRefused() = runTest {
    given("notes.txt")

    val escaped = runCatching { repository().writeText("../escaped.txt", "no") }

    assertTrue("expected a refusal, got $escaped", escaped.isFailure)
  }

  @Test
  fun readingAFileThatIsNotThereIsRefused() = runTest {
    given("notes.txt")

    val missing = runCatching { repository().readText("nope.txt") }

    assertTrue("expected a refusal, got $missing", missing.isFailure)
  }

  @Test
  fun aPathThatDoesNotExistIsRefused() = runTest {
    given("notes.txt")

    val missing = runCatching { repository().list("nope") }

    assertTrue("expected a refusal, got $missing", missing.isFailure)
  }

  private companion object {
    /** Generous on purpose: this asserts "the file's own mtime", not a stopwatch. */
    val RECENT: Duration = Duration.ofMinutes(1)

    /** `PK\x03\x04` and a NUL - four bytes no text encoding round-trips unchanged. */
    val ZIP_HEADER = byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)
  }
}
