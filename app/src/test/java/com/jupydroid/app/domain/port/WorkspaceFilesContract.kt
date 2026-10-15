package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceEntryKind
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [WorkspaceFiles] must do, whoever implements it: create, rename, overwrite and delete
 * the way jupyter_server does on the tree it shares with this app, and announce each move.
 *
 * Each act is checked through a [WorkspaceRepository] on the same tree, because what an act did is
 * only visible as what is listed and read afterwards.
 */
abstract class WorkspaceFilesContract {
  protected abstract fun files(): WorkspaceFiles

  /** Reads the same tree [files] acts on. */
  protected abstract fun repository(): WorkspaceRepository

  /** Puts [paths] on the tree, relative to its root; a trailing `/` makes a folder. */
  protected abstract fun given(vararg paths: String)

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

    assertEquals("untitled.txt", files().createFile("", "txt"))
  }

  @Test
  fun aNewFileIsCreatedEmptyAndInsideTheDirectoryAsked() = runTest {
    given("reports/keep.txt")
    val files = files()
    val repository = repository()

    val created = files.createFile("reports", "py")

    assertEquals("reports/untitled.py", created)
    assertEquals("", repository.readText(created))
  }

  /** The counter goes on the stem, so the extension survives: `untitled1.txt`. */
  @Test
  fun aSecondFileOfTheSameTypeIsCounted() = runTest {
    given("keep.txt")
    val files = files()
    files.createFile("", "txt")

    assertEquals("untitled1.txt", files.createFile("", "txt"))
  }

  @Test
  fun aNewFolderIsNamedTheWayTheServerNamesIt() = runTest {
    given("keep.txt")

    assertEquals("Untitled Folder", files().createFolder(""))
  }

  /** `insert=" "` is passed for a directory and nothing else, so this counts with a space. */
  @Test
  fun aSecondFolderCountsWithASpace() = runTest {
    given("keep.txt")
    val files = files()
    files.createFolder("")

    assertEquals("Untitled Folder 1", files.createFolder(""))
  }

  @Test
  fun aNewFolderIsADirectoryThatCanBeListed() = runTest {
    given("keep.txt")
    val files = files()
    val repository = repository()

    val created = files.createFolder("")

    assertEquals(emptyList<WorkspaceEntry>(), repository.list(created))
    assertEquals(WorkspaceEntryKind.DIRECTORY, repository.list("").first { it.path == created }.kind)
  }

  @Test
  fun creatingOutsideTheRootIsRefused() = runTest {
    given("keep.txt")

    assertTrue(runCatching { files().createFile("../..", "txt") }.isFailure)
    assertTrue(runCatching { files().createFolder("../..") }.isFailure)
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
    val files = files()
    val repository = repository()

    val renamed = files.rename("notes.txt", "kept.txt")

    assertEquals("kept.txt", renamed)
    assertEquals("x", repository.readText(renamed))
    assertEquals(listOf("kept.txt"), repository.list("").map { it.name })
  }

  /**
   * The new name joins onto the folder the old path names, never onto the root.
   *
   * `PathExt.join(this._model.path, newName)` (`listing.ts:2621`), and `Context._rename` replaces
   * only the last segment. Renaming a notebook inside a folder to the root was a real defect once
   * which is why this is a contract test and not a detail of one implementation.
   */
  @Test
  fun renamingInsideAFolderStaysInThatFolder() = runTest {
    given("reports/summary.ipynb")

    val renamed = files().rename("reports/summary.ipynb", "final.ipynb")

    assertEquals("reports/final.ipynb", renamed)
  }

  /** `rename_file` is `shutil.move`, so a directory moves exactly like a file. */
  @Test
  fun aFolderRenamesLikeAFile() = runTest {
    given("reports/summary.ipynb")
    val files = files()
    val repository = repository()

    val renamed = files.rename("reports", "archive")

    assertEquals("archive", renamed)
    assertEquals(listOf("summary.ipynb"), repository.list("archive").map { it.name })
  }

  /** `if new_path == old_path: return` - the server does nothing, and says nothing. */
  @Test
  fun renamingToTheSameNameDoesNothing() = runTest {
    given("notes.txt")
    val files = files()
    val repository = repository()

    val renamed = files.rename("notes.txt", "notes.txt")

    assertEquals("notes.txt", renamed)
    assertEquals("x", repository.readText("notes.txt"))
  }

  /**
   * A name is refused, never rewritten - and a name carrying a folder is not a name.
   *
   * `isValidFileName` disallows `/`, `\`, `:` and a leading dot. Winlator strips the reserved
   * characters instead (`StringUtils.clearReservedChars`), which hands back a file the user never
   * named; we follow JupyterLab here.
   */
  @Test
  fun anInvalidNameIsRefused() = runTest {
    given("notes.txt")
    val files = files()
    val repository = repository()

    listOf("a/b", "a\\b", "a:b", "", "   ", ".hidden").forEach { name ->
      val refused = runCatching { files.rename("notes.txt", name) }
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
    val files = files()

    val taken = runCatching { files.rename("notes.txt", "taken.txt") }.exceptionOrNull()
    val invalid = runCatching { files.rename("notes.txt", "a/b") }.exceptionOrNull()

    assertTrue("a taken name must be distinguishable, got $taken", taken is FileNameTakenException)
    assertTrue("invalid must not be reported as taken", invalid !is FileNameTakenException)
  }

  @Test
  fun renamingSomethingThatIsNotThereIsRefused() = runTest {
    given("notes.txt")

    val missing = runCatching { files().rename("nope.txt", "something.txt") }

    assertTrue("expected a refusal, got $missing", missing.isFailure)
  }

  @Test
  fun renamingOutsideTheRootIsRefused() = runTest {
    given("notes.txt")

    val escaped = runCatching { files().rename("../../secrets", "mine.txt") }

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
    val files = files()
    val repository = repository()

    files.delete("notes.txt")

    assertEquals(listOf("keep.txt"), repository.list("").map { it.name })
  }

  @Test
  fun anEmptyFolderIsDeleted() = runTest {
    given("empty/", "keep.txt")
    val files = files()
    val repository = repository()

    files.delete("empty")

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
    val files = files()
    val repository = repository()

    val refused = runCatching { files.delete("reports") }

    assertTrue("expected a refusal, got $refused", refused.isFailure)
    assertEquals(listOf("summary.ipynb"), repository.list("reports").map { it.name })
  }

  @Test
  fun deletingSomethingThatIsNotThereIsRefused() = runTest {
    given("notes.txt")

    val missing = runCatching { files().delete("nope.txt") }

    assertTrue("expected a refusal, got $missing", missing.isFailure)
  }

  @Test
  fun deletingOutsideTheRootIsRefused() = runTest {
    given("notes.txt")

    val escaped = runCatching { files().delete("../../secrets") }

    assertTrue("expected a refusal, got $escaped", escaped.isFailure)
  }

  @Test
  fun overwritingReplacesTheTargetAndLeavesOneFile() = runTest {
    given("notes.txt", "taken.txt")
    val files = files()
    val repository = repository()
    repository.writeText("notes.txt", "the one that survives")

    val overwritten = files.overwrite("notes.txt", "taken.txt")

    assertEquals("taken.txt", overwritten)
    assertEquals("the one that survives", repository.readText("taken.txt"))
    assertEquals(listOf("taken.txt"), repository.list("").map { it.name })
  }

  /**
   * An overwrite is also the *delete* of whatever held the name, and it is announced as one.
   *
   * `DocumentManager.overwrite` runs `deleteFile(newPath)` first (`manager.ts:514-528`), which a
   * widget open on the target hears exactly the way a delete lands. Unannounced, a document open
   * on the target keeps its buffer and its next write lands over the file that just won the
   * name. Announced **before** the rename, in the order the disk moved: a document that adopted
   * the new path first would take the announcement for its own file going.
   */
  @Test
  fun overwritingTellsTheTargetIsGoneBeforeItMoves() = runTest {
    given("notes.txt", "taken.txt")
    val files = files()
    val heard = async { files.changes.take(2).toList() }
    advanceUntilIdle() // the collector subscribes before the emit - `changes` replays nothing

    files.overwrite("notes.txt", "taken.txt")

    assertEquals(
      listOf(
        WorkspaceChange.Deleted("taken.txt"),
        WorkspaceChange.Renamed(from = "notes.txt", to = "taken.txt"),
      ),
      heard.await(),
    )
  }
}
