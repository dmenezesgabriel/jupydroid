package com.jupydroid.app.ui.shell

import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.MatchedFile
import com.jupydroid.app.domain.model.MatchedLine
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.TextEdit
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Replace across files, **through the editor's model rather than the filesystem**.
 *
 * `IReplaceService.replace` builds `ResourceTextEdit`s from the matches already on screen and hands
 * them to `IBulkEditService` (`replaceService.ts:115-118`), which resolves a text model for every
 * resource - open or not - edits the models and only then saves (`bulkTextEdits.ts:227`,
 * `replaceService.ts:136`). Two things follow and both are asserted here: a document that is **open**
 * is edited in memory, and a conflict **refuses the whole operation** rather than prompting
 * (`_validateBeforePrepare`, `bulkTextEdits.ts:213-216`).
 */
class WorkspaceReplaceTest {
  private val cat = SearchQuery("cat")

  private val open = OpenDocuments()
  private val notebooks = FakeNotebookRepository()
  private val workspace = FakeWorkspaceRepository(textByPath = mapOf("a.py" to "cat = 1", "b.py" to "cat = 2"))

  private val replace = WorkspaceReplace(open, notebooks, workspace)

  /** A plain file's match: line 1, the whole line, and where `cat` sits in it. */
  private fun fileMatch(path: String, text: String, cell: Int? = null) =
    MatchedFile(path, listOf(MatchedLine(line = 1, text = text, range = 0..2, cell = cell)))

  @Test
  fun aClosedFileIsRewrittenOnDisk() = runTest {
    val outcome = replace.replaceAll(listOf(fileMatch("a.py", "cat = 1")), "dog", cat, preserveCase = false)

    assertEquals(ReplaceOutcome.Replaced(occurrences = 1, fileCount = 1), outcome)
    assertEquals("dog = 1", workspace.readText("a.py"))
  }

  @Test
  fun everyFileIsCountedAndEveryFileIsWritten() = runTest {
    val files = listOf(fileMatch("a.py", "cat = 1"), fileMatch("b.py", "cat = 2"))

    val outcome = replace.replaceAll(files, "dog", cat, preserveCase = false)

    assertEquals(ReplaceOutcome.Replaced(occurrences = 2, fileCount = 2), outcome)
    assertEquals("dog = 1", workspace.readText("a.py"))
    assertEquals("dog = 2", workspace.readText("b.py"))
  }

  /**
   * **A closed notebook goes through `NotebookRepository`, never `writeText`** - the walk reports a
   * cell hit, and rewriting a `.ipynb` as text would write the replacement into the JSON that stores
   * it. `MatchedLine.cell` is what says which of the two a resource is.
   */
  @Test
  fun aClosedNotebookIsRewrittenCellByCellThroughItsOwnRepository() = runTest {
    notebooks.saveNotebook(aNotebookWithForeignFields())

    val outcome = replace.replaceAll(
      listOf(MatchedFile("nb.ipynb", listOf(MatchedLine(1, "cat = 1", 0..2, cell = 1)))),
      "dog",
      cat,
      preserveCase = false,
    )

    assertEquals(ReplaceOutcome.Replaced(occurrences = 1, fileCount = 1), outcome)
    val saved = notebooks.readNotebook("nb.ipynb")
    assertEquals("print('hello')", saved.cells[0].source)
    assertEquals("dog = 1", saved.cells[1].source)
  }

  /**
   * **And the round-trip keeps what this app does not model**, which is P2x-5's own gate and the one
   * P2x-4 could not meet, because P2x-1e had left no replace here to round-trip. nbformat is an open
   * format (`NotebookRoundTripTest`), and a replace that shredded a cell's `nbgrader` block would be
   * a worse bug than the one it fixed.
   */
  @Test
  fun replacingInANotebookKeepsTheFieldsItDoesNotModel() = runTest {
    notebooks.saveNotebook(aNotebookWithForeignFields())

    replace.replaceAll(
      listOf(MatchedFile("nb.ipynb", listOf(MatchedLine(1, "cat = 1", 0..2, cell = 1)))),
      "dog",
      cat,
      preserveCase = false,
    )

    val saved = notebooks.readNotebook("nb.ipynb")
    assertEquals(JsonPrimitive("kept"), saved.preserved["foreign"])
    assertEquals(JsonPrimitive("also kept"), saved.cells[1].preserved["nbgrader"])
  }

  /**
   * **The open document is edited in memory and the file is not touched by this**, which is the whole
   * reason P2x-1e took replace off the port: writing the file would be overwritten by that editor's
   * next debounced save, and the reader would watch the replacement vanish.
   */
  @Test
  fun theOpenDocumentIsEditedRatherThanWritten() = runTest {
    val document = RecordingOpenDocument("a.py", "cat = 1")
    open.opened(document)

    val outcome = replace.replaceAll(listOf(fileMatch("a.py", "cat = 1")), "dog", cat, preserveCase = false)

    assertEquals(ReplaceOutcome.Replaced(occurrences = 1, fileCount = 1), outcome)
    assertEquals(listOf(null to listOf(TextEdit(0..2, "dog"))), document.applied)
    // Untouched by the engine: the document is what saves, on its own debounce.
    assertEquals("cat = 1", workspace.readText("a.py"))
  }

  /**
   * `_validateBeforePrepare` throws *"has changed in the meantime"* before a single task is prepared
   * (`bulkTextEdits.ts:213-216`) - so a stale match refuses the **whole** operation, and the files
   * that were still good are left alone.
   */
  @Test
  fun aFileThatChangedSinceTheWalkRefusesTheWholeOperation() = runTest {
    workspace.writeText("b.py", "cow = 2")
    val files = listOf(fileMatch("a.py", "cat = 1"), fileMatch("b.py", "cat = 2"))

    val outcome = replace.replaceAll(files, "dog", cat, preserveCase = false)

    assertEquals(ReplaceOutcome.Refused(listOf("b.py")), outcome)
    assertEquals("cat = 1", workspace.readText("a.py"))
    assertEquals("cow = 2", workspace.readText("b.py"))
  }

  /** A notebook that lost the cell the walk found is the same refusal, by the same rule. */
  @Test
  fun aNotebookThatLostTheCellRefusesTheWholeOperation() = runTest {
    notebooks.saveNotebook(Notebook("nb.ipynb", "nb", "python3", listOf(Cell("c0", CellKind.CODE, "cat = 1"))))

    val outcome = replace.replaceAll(
      listOf(MatchedFile("nb.ipynb", listOf(MatchedLine(1, "cat = 1", 0..2, cell = 4)))),
      "dog",
      cat,
      preserveCase = false,
    )

    assertEquals(ReplaceOutcome.Refused(listOf("nb.ipynb")), outcome)
  }

  /** A file that is open but has not loaded reads as nothing, which is the same refusal. */
  @Test
  fun anOpenDocumentThatCannotBeReadRefusesRatherThanFallingBackToTheFile() = runTest {
    open.opened(RecordingOpenDocument("a.py", text = null))

    val outcome = replace.replaceAll(listOf(fileMatch("a.py", "cat = 1")), "dog", cat, preserveCase = false)

    assertEquals(ReplaceOutcome.Refused(listOf("a.py")), outcome)
    assertEquals("cat = 1", workspace.readText("a.py"))
  }

  /** [com.jupydroid.app.domain.model.replacementFor]'s two steps reach a workspace replace whole. */
  @Test
  fun theReplacementKeepsTheCaseItFound() = runTest {
    workspace.writeText("a.py", "CAT = 1")

    replace.replaceAll(
      listOf(MatchedFile("a.py", listOf(MatchedLine(1, "CAT = 1", 0..2)))),
      "dog",
      cat,
      preserveCase = true,
    )

    assertEquals("DOG = 1", workspace.readText("a.py"))
  }

  /** Nothing found is nothing done, and it is not a refusal. */
  @Test
  fun replacingNothingReplacesNothing() = runTest {
    val outcome = replace.replaceAll(emptyList(), "dog", cat, preserveCase = false)

    assertEquals(ReplaceOutcome.Replaced(occurrences = 0, fileCount = 0), outcome)
  }

  /** Two matches in one file are two occurrences in one file - `buildReplaceAllConfirmationMessage`. */
  @Test
  fun twoMatchesInOneFileAreCountedApartFromTheFile() = runTest {
    workspace.writeText("a.py", "cat\ncat")
    val file = MatchedFile("a.py", listOf(MatchedLine(1, "cat", 0..2), MatchedLine(2, "cat", 0..2)))

    val outcome = replace.replaceAll(listOf(file), "dog", cat, preserveCase = false)

    assertEquals(ReplaceOutcome.Replaced(occurrences = 2, fileCount = 1), outcome)
    assertEquals("dog\ndog", workspace.readText("a.py"))
  }

  @Test
  fun aRefusalNamesEveryResourceThatMovedOn() = runTest {
    workspace.writeText("a.py", "cow = 1")
    workspace.writeText("b.py", "cow = 2")
    val files = listOf(fileMatch("a.py", "cat = 1"), fileMatch("b.py", "cat = 2"))

    val outcome = replace.replaceAll(files, "dog", cat, preserveCase = false)

    assertTrue(outcome is ReplaceOutcome.Refused)
    assertEquals(listOf("a.py", "b.py"), (outcome as ReplaceOutcome.Refused).paths)
  }

  private fun aNotebookWithForeignFields() = Notebook(
    id = "nb.ipynb",
    title = "nb",
    kernelId = "python3",
    cells = listOf(
      Cell("c0", CellKind.CODE, "print('hello')"),
      Cell("c1", CellKind.CODE, "cat = 1", preserved = JsonObject(mapOf("nbgrader" to JsonPrimitive("also kept")))),
    ),
    preserved = JsonObject(mapOf("foreign" to JsonPrimitive("kept"))),
  )
}
