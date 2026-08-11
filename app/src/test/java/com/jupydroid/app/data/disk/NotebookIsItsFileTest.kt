package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.port.NotebookNameTakenException
import java.io.File
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The notebook's name **is** its file, which is JupyterLab's model and the point of Phase X.
 *
 * Until now the name lived in `metadata.jupydroid.title` while the file was a UUID, so the
 * workspace the kernel shares - and the explorer X2 builds over it - would show
 * `9f3a1c02-….ipynb` for a notebook called "Analysis of sales". A rename wrote the JSON and left
 * the file alone, which is the "title inside, name outside" split this closes.
 *
 * Renaming therefore *moves the file*, exactly as `Context._rename` does:
 * `await this._manager.contents.rename(this.path, newPath)`.
 */
class NotebookIsItsFileTest {
  @get:Rule val temp = TemporaryFolder()

  private val repository by lazy { DiskNotebookRepository(temp.root) }

  @Test
  fun creatingANotebookNamesTheFileAfterIt() = runTest {
    repository.createNotebook("Analysis of sales", "python3")

    assertTrue("Analysis of sales.ipynb" in temp.root.list()!!.toList())
  }

  @Test
  fun theIdIsTheName() = runTest {
    val notebook = repository.createNotebook("Analysis of sales", "python3")

    assertEquals("Analysis of sales", notebook.id)
    assertEquals("Analysis of sales", notebook.title)
  }

  @Test
  fun renamingMovesTheFile() = runTest {
    val notebook = repository.createNotebook("Draft", "python3")

    repository.renameNotebook(notebook.id, "Final")

    assertEquals(listOf("Final.ipynb"), temp.root.list()!!.toList())
  }

  /** The caller needs the new id, because the old one no longer names anything. */
  @Test
  fun renamingReturnsTheNewId() = runTest {
    val notebook = repository.createNotebook("Draft", "python3")

    val renamed = repository.renameNotebook(notebook.id, "Final")

    assertEquals("Final", renamed)
    assertEquals("Final", repository.readNotebook(renamed).title)
  }

  @Test
  fun theCellsSurviveARename() = runTest {
    val notebook = repository.createNotebook("Draft", "python3")
    repository.saveNotebook(repository.readNotebook(notebook.id).copy(cells = cellsOf("keep = 1")))

    val renamed = repository.renameNotebook(notebook.id, "Final")

    assertEquals("keep = 1", repository.readNotebook(renamed).cells.single().source)
  }

  /**
   * A name already taken is refused rather than silently overwriting somebody's notebook.
   *
   * JupyterLab asks - `"%1" already exists, overwrite?` - which is a dialog, not a repository
   * decision. What the repository owes is to not destroy the file; the asking belongs in the UI.
   */
  @Test
  fun renamingOntoAnExistingNameIsRefused() = runTest {
    repository.createNotebook("Taken", "python3")
    val other = repository.createNotebook("Draft", "python3")

    val failure = runCatching { repository.renameNotebook(other.id, "Taken") }

    assertTrue("expected a refusal, got $failure", failure.isFailure)
    assertTrue("the other notebook must still exist", File(temp.root, "Draft.ipynb").exists())
  }

  @Test
  fun aNameWithASeparatorIsRefused() = runTest {
    val notebook = repository.createNotebook("Draft", "python3")

    val failure = runCatching { repository.renameNotebook(notebook.id, "reports/final") }

    assertTrue("expected a refusal, got $failure", failure.isFailure)
    assertTrue(File(temp.root, "Draft.ipynb").exists())
    assertFalse(File(temp.root, "reports").exists())
  }

  /**
   * A refused rename says *why*, because the caller does something different for each.
   *
   * JupyterLab branches on the HTTP status: `if (error.response.status !== 409) throw error` and
   * only a 409 - the name is taken - becomes "already exists, overwrite?"
   * (`packages/docmanager/src/dialogs.ts`, `renameFile`). An invalid name is not offered an
   * overwrite; it is simply wrong.
   */
  @Test
  fun aTakenNameAndAnInvalidNameFailDifferently() = runTest {
    repository.createNotebook("Taken", "python3")
    val other = repository.createNotebook("Draft", "python3")

    val taken = runCatching { repository.renameNotebook(other.id, "Taken") }.exceptionOrNull()
    val invalid = runCatching { repository.renameNotebook(other.id, "a/b") }.exceptionOrNull()

    assertTrue("a taken name must be distinguishable, got $taken", taken is NotebookNameTakenException)
    assertTrue("an invalid name is not an overwrite offer, got $invalid", invalid is IllegalArgumentException)
    assertFalse("invalid must not be reported as taken", invalid is NotebookNameTakenException)
  }

  /**
   * Overwriting moves ours aside first, deletes the target, then moves ours into place.
   *
   * Transcribed from `DocumentManager.overwrite`: "Cleanly overwrite the file by moving it, making
   * sure the original does not exist, and then renaming to the new path." Deleting the target
   * *first* would leave a window in which neither file exists, and a failure there would lose the
   * one the user was keeping.
   */
  @Test
  fun overwritingReplacesTheTargetAndKeepsOurContents() = runTest {
    val target = repository.createNotebook("Taken", "python3")
    repository.saveNotebook(repository.readNotebook(target.id).copy(cells = cellsOf("theirs = 1")))
    val ours = repository.createNotebook("Draft", "python3")
    repository.saveNotebook(repository.readNotebook(ours.id).copy(cells = cellsOf("ours = 2")))

    val moved = repository.overwriteNotebook(ours.id, "Taken")

    assertEquals("Taken", moved)
    assertEquals("ours = 2", repository.readNotebook(moved).cells.single().source)
    assertEquals(listOf("Taken.ipynb"), temp.root.list()!!.toList())
  }

  /** No leftovers: the unique temporary name the algorithm uses must not survive. */
  @Test
  fun overwritingLeavesNoTemporaryFile() = runTest {
    repository.createNotebook("Taken", "python3")
    val ours = repository.createNotebook("Draft", "python3")

    repository.overwriteNotebook(ours.id, "Taken")

    assertEquals(emptyList<String>(), temp.root.list()!!.filter { !it.endsWith(".ipynb") })
  }

  /** Creating with a name already taken gets a suffix: the user asked for a new notebook. */
  @Test
  fun creatingWithATakenNameIsSuffixed() = runTest {
    repository.createNotebook("Untitled", "python3")

    val second = repository.createNotebook("Untitled", "python3")

    assertEquals("Untitled1", second.id)
    assertTrue(File(temp.root, "Untitled.ipynb").exists())
  }

  private fun cellsOf(source: String) =
    listOf(com.jupydroid.app.domain.model.Cell("c1", com.jupydroid.app.domain.model.CellKind.CODE, source))
}
