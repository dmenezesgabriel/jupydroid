package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.WorkspaceEntry
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

  /**
   * `untitled.txt`, which is the server's name and not one we chose.
   *
   * `new_untitled` builds `untitled` + `.<ext>` for `type: "file"` (`manager.py:608-613`). The
   * caller picks a type; the name comes from the server, and the rename the reference opens on
   * the new item is where a person picks one.
   */
  @Test
  fun aNewFileIsNamedTheWayTheServerNamesIt() = runTest {
    given("keep.txt")

    assertEquals("untitled.txt", repository().createFile("", "txt"))
  }

  @Test
  fun aNewFileIsCreatedEmptyAndInsideTheDirectoryAsked() = runTest {
    given("reports/keep.txt")
    val repository = repository()

    val created = repository.createFile("reports", "py")

    assertEquals("reports/untitled.py", created)
    assertEquals("", repository.readText(created))
  }

  /** The counter goes on the stem, so the extension survives: `untitled1.txt`. */
  @Test
  fun aSecondFileOfTheSameTypeIsCounted() = runTest {
    given("keep.txt")
    val repository = repository()
    repository.createFile("", "txt")

    assertEquals("untitled1.txt", repository.createFile("", "txt"))
  }

  @Test
  fun aNewFolderIsNamedTheWayTheServerNamesIt() = runTest {
    given("keep.txt")

    assertEquals("Untitled Folder", repository().createFolder(""))
  }

  /** `insert=" "` is passed for a directory and nothing else, so this counts with a space. */
  @Test
  fun aSecondFolderCountsWithASpace() = runTest {
    given("keep.txt")
    val repository = repository()
    repository.createFolder("")

    assertEquals("Untitled Folder 1", repository.createFolder(""))
  }

  @Test
  fun aNewFolderIsADirectoryThatCanBeListed() = runTest {
    given("keep.txt")
    val repository = repository()

    val created = repository.createFolder("")

    assertEquals(emptyList<WorkspaceEntry>(), repository.list(created))
    assertEquals(WorkspaceEntryKind.DIRECTORY, repository.list("").first { it.path == created }.kind)
  }

  @Test
  fun creatingOutsideTheRootIsRefused() = runTest {
    given("keep.txt")

    assertTrue(runCatching { repository().createFile("../..", "txt") }.isFailure)
    assertTrue(runCatching { repository().createFolder("../..") }.isFailure)
  }

  @Test
  fun aPathThatDoesNotExistIsRefused() = runTest {
    given("notes.txt")

    val missing = runCatching { repository().list("nope") }

    assertTrue("expected a refusal, got $missing", missing.isFailure)
  }

  /**
   * A rename takes a *name* and answers with the path it now lives at.
   *
   * `Contents.rename(oldPath, newPath)` moves the file; nothing is re-created and the content is
   * the same file's (`filemanager.py:619-647` is `shutil.move`).
   */
  @Test
  fun renamingMovesTheFileAndAnswersWithItsNewPath() = runTest {
    given("notes.txt")
    val repository = repository()

    val renamed = repository.rename("notes.txt", "kept.txt")

    assertEquals("kept.txt", renamed)
    assertEquals("x", repository.readText(renamed))
    assertEquals(listOf("kept.txt"), repository.list("").map { it.name })
  }

  /**
   * The new name joins onto the folder the old path names, never onto the root.
   *
   * `PathExt.join(this._model.path, newName)` (`listing.ts:2621`), and `Context._rename` replaces
   * only the last segment. Renaming a notebook inside a folder to the root was a real defect once
   * (X2a), which is why this is a contract test and not a detail of one implementation.
   */
  @Test
  fun renamingInsideAFolderStaysInThatFolder() = runTest {
    given("reports/summary.ipynb")

    val renamed = repository().rename("reports/summary.ipynb", "final.ipynb")

    assertEquals("reports/final.ipynb", renamed)
  }

  /** `rename_file` is `shutil.move`, so a directory moves exactly like a file. */
  @Test
  fun aFolderRenamesLikeAFile() = runTest {
    given("reports/summary.ipynb")
    val repository = repository()

    val renamed = repository.rename("reports", "archive")

    assertEquals("archive", renamed)
    assertEquals(listOf("summary.ipynb"), repository.list("archive").map { it.name })
  }

  /** `if new_path == old_path: return` - the server does nothing, and says nothing. */
  @Test
  fun renamingToTheSameNameDoesNothing() = runTest {
    given("notes.txt")
    val repository = repository()

    val renamed = repository.rename("notes.txt", "notes.txt")

    assertEquals("notes.txt", renamed)
    assertEquals("x", repository.readText("notes.txt"))
  }

  /**
   * A name is refused, never rewritten - and a name carrying a folder is not a name.
   *
   * `isValidFileName` disallows `/`, `\`, `:` and a leading dot. Winlator strips the reserved
   * characters instead (`StringUtils.clearReservedChars`), which hands back a file the user never
   * named; X1b settled that we follow JupyterLab here.
   */
  @Test
  fun anInvalidNameIsRefused() = runTest {
    given("notes.txt")
    val repository = repository()

    listOf("a/b", "a\\b", "a:b", "", "   ", ".hidden").forEach { name ->
      val refused = runCatching { repository.rename("notes.txt", name) }
      assertTrue("expected '$name' to be refused, got $refused", refused.isFailure)
    }
    assertEquals("x", repository.readText("notes.txt"))
  }

  /**
   * A taken name is [FileNameTakenException] and nothing else, because the caller branches on it.
   *
   * JupyterLab catches exactly a 409 and turns it into "already exists, overwrite?"; every other
   * failure rethrows (`docmanager/src/dialogs.ts`, `renameFile`). An invalid name must therefore
   * *not* arrive as this type - it is never offered an overwrite.
   */
  @Test
  fun aTakenNameIsItsOwnRefusal() = runTest {
    given("notes.txt", "taken.txt")
    val repository = repository()

    val taken = runCatching { repository.rename("notes.txt", "taken.txt") }.exceptionOrNull()
    val invalid = runCatching { repository.rename("notes.txt", "a/b") }.exceptionOrNull()

    assertTrue("a taken name must be distinguishable, got $taken", taken is FileNameTakenException)
    assertTrue("invalid must not be reported as taken", invalid !is FileNameTakenException)
  }

  @Test
  fun renamingSomethingThatIsNotThereIsRefused() = runTest {
    given("notes.txt")

    val missing = runCatching { repository().rename("nope.txt", "something.txt") }

    assertTrue("expected a refusal, got $missing", missing.isFailure)
  }

  @Test
  fun renamingOutsideTheRootIsRefused() = runTest {
    given("notes.txt")

    val escaped = runCatching { repository().rename("../../secrets", "mine.txt") }

    assertTrue("expected a refusal, got $escaped", escaped.isFailure)
  }

  /**
   * Overwriting replaces the target and leaves exactly one file.
   *
   * Offered only after the user has been asked. `DocumentManager.overwrite` moves ours aside,
   * deletes the target and renames into place (`manager.ts:514-528`) - deleting first would open a
   * window in which neither file exists.
   */
  @Test
  fun deletingRemovesTheFile() = runTest {
    given("notes.txt", "keep.txt")
    val repository = repository()

    repository.delete("notes.txt")

    assertEquals(listOf("keep.txt"), repository.list("").map { it.name })
  }

  @Test
  fun anEmptyFolderIsDeleted() = runTest {
    given("empty/", "keep.txt")
    val repository = repository()

    repository.delete("empty")

    assertEquals(listOf("keep.txt"), repository.list("").map { it.name })
  }

  /**
   * A folder with anything in it is **refused**, and says so.
   *
   * The server that serves this same tree does exactly this: `delete_file` raises
   * `400 "Directory %s not empty"` unless `always_delete_dir` (`filemanager.py:607-613`), which
   * defaults to false. Its other branch sends the directory to the platform's trash - and Android
   * has no trash for an app's private files, so the trashless branch is the honest one. Winlator
   * *does* delete a tree recursively (`FileUtils.delete`), and that is the one place we follow the
   * server instead: behaviour is JupyterLab's question, not the file manager's.
   */
  @Test
  fun aFolderWithAnythingInItIsRefused() = runTest {
    given("reports/summary.ipynb")
    val repository = repository()

    val refused = runCatching { repository.delete("reports") }

    assertTrue("expected a refusal, got $refused", refused.isFailure)
    assertEquals(listOf("summary.ipynb"), repository.list("reports").map { it.name })
  }

  @Test
  fun deletingSomethingThatIsNotThereIsRefused() = runTest {
    given("notes.txt")

    val missing = runCatching { repository().delete("nope.txt") }

    assertTrue("expected a refusal, got $missing", missing.isFailure)
  }

  @Test
  fun deletingOutsideTheRootIsRefused() = runTest {
    given("notes.txt")

    val escaped = runCatching { repository().delete("../../secrets") }

    assertTrue("expected a refusal, got $escaped", escaped.isFailure)
  }

  @Test
  fun overwritingReplacesTheTargetAndLeavesOneFile() = runTest {
    given("notes.txt", "taken.txt")
    val repository = repository()
    repository.writeText("notes.txt", "the one that survives")

    val overwritten = repository.overwrite("notes.txt", "taken.txt")

    assertEquals("taken.txt", overwritten)
    assertEquals("the one that survives", repository.readText("taken.txt"))
    assertEquals(listOf("taken.txt"), repository.list("").map { it.name })
  }
}
