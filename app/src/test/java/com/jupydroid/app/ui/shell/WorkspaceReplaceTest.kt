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

    assertEquals(1 to 1, outcome.occurrencesAndFiles())
    assertEquals("dog = 1", workspace.readText("a.py"))
  }

  @Test
  fun everyFileIsCountedAndEveryFileIsWritten() = runTest {
    val files = listOf(fileMatch("a.py", "cat = 1"), fileMatch("b.py", "cat = 2"))

    val outcome = replace.replaceAll(files, "dog", cat, preserveCase = false)

    assertEquals(2 to 2, outcome.occurrencesAndFiles())
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

    assertEquals(1 to 1, outcome.occurrencesAndFiles())
    val saved = notebooks.readNotebook("nb.ipynb")
    assertEquals("print('hello')", saved.cells[0].source)
    assertEquals("dog = 1", saved.cells[1].source)
  }

  /**
   * **And the round-trip keeps what this app does not model**, which could not be asserted until
   * there was a replace here to round-trip at all. nbformat is an open
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
   * reason replace came off the port: writing the file would be overwritten by that editor's
   * next debounced save, and the reader would watch the replacement vanish.
   */
  @Test
  fun theOpenDocumentIsEditedRatherThanWritten() = runTest {
    val document = RecordingOpenDocument("a.py", "cat = 1")
    open.opened(document)

    val outcome = replace.replaceAll(listOf(fileMatch("a.py", "cat = 1")), "dog", cat, preserveCase = false)

    assertEquals(1 to 1, outcome.occurrencesAndFiles())
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

    assertEquals(0 to 0, outcome.occurrencesAndFiles())
  }

  /** Two matches in one file are two occurrences in one file - `buildReplaceAllConfirmationMessage`. */
  @Test
  fun twoMatchesInOneFileAreCountedApartFromTheFile() = runTest {
    workspace.writeText("a.py", "cat\ncat")
    val file = MatchedFile("a.py", listOf(MatchedLine(1, "cat", 0..2), MatchedLine(2, "cat", 0..2)))

    val outcome = replace.replaceAll(listOf(file), "dog", cat, preserveCase = false)

    assertEquals(2 to 1, outcome.occurrencesAndFiles())
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

  /**
   * **One entry takes back every file**, which is the property that makes this an undo of the
   * *command* rather than of each file it happened to touch: `MultiModelEditStackElement.undo` loops
   * every element it was built over, in one go (`editStack.ts:330-336`).
   */
  @Test
  fun oneUndoTakesBackEveryFileTheReplaceWrote() = runTest {
    val files = listOf(fileMatch("a.py", "cat = 1"), fileMatch("b.py", "cat = 2"))
    val replaced = replace.replaceAll(files, "dog", cat, preserveCase = false) as ReplaceOutcome.Replaced

    val outcome = replace.undo(replaced.undo)

    assertEquals(UndoOutcome.Undone, outcome)
    assertEquals("cat = 1", workspace.readText("a.py"))
    assertEquals("cat = 2", workspace.readText("b.py"))
  }

  /** The notebook route inverts through `NotebookRepository` too, and keeps what it does not model. */
  @Test
  fun undoingANotebookReplacePutsTheCellBackAndKeepsItsForeignFields() = runTest {
    notebooks.saveNotebook(aNotebookWithForeignFields())
    val match = listOf(MatchedFile("nb.ipynb", listOf(MatchedLine(1, "cat = 1", 0..2, cell = 1))))
    val replaced = replace.replaceAll(match, "dog", cat, preserveCase = false) as ReplaceOutcome.Replaced

    assertEquals(UndoOutcome.Undone, replace.undo(replaced.undo))

    val saved = notebooks.readNotebook("nb.ipynb")
    assertEquals("cat = 1", saved.cells[1].source)
    assertEquals(JsonPrimitive("also kept"), saved.cells[1].preserved["nbgrader"])
  }

  /** An open document is edited back rather than written back - the same route, pointed the other way. */
  @Test
  fun undoingAnOpenDocumentEditsItRatherThanWritingTheFile() = runTest {
    val document = RecordingOpenDocument("a.py", "cat = 1")
    open.opened(document)
    val replaced = replace
      .replaceAll(listOf(fileMatch("a.py", "cat = 1")), "dog", cat, preserveCase = false) as ReplaceOutcome.Replaced

    assertEquals(UndoOutcome.Undone, replace.undo(replaced.undo))

    assertEquals("cat = 1", document.sourceOf(null))
    assertEquals(listOf(null to listOf(TextEdit(0..2, "dog")), null to listOf(TextEdit(0..2, "cat"))), document.applied)
    assertEquals("cat = 1", workspace.readText("a.py"))
  }

  /**
   * `_checkWorkspaceUndo` refuses when the element is no longer the closest past one on **every**
   * impacted resource - *"Could not undo '{0}' across all files because changes were made to {1}"*
   * (`undoRedoService.ts:853-869`). So one file written since refuses the lot, and the others keep
   * the replacement they were given.
   */
  @Test
  fun aFileWrittenSinceTheReplaceRefusesTheWholeUndo() = runTest {
    val files = listOf(fileMatch("a.py", "cat = 1"), fileMatch("b.py", "cat = 2"))
    val replaced = replace.replaceAll(files, "dog", cat, preserveCase = false) as ReplaceOutcome.Replaced
    workspace.writeText("b.py", "dog = 22")

    val outcome = replace.undo(replaced.undo)

    assertEquals(UndoOutcome.Refused(listOf("b.py")), outcome)
    assertEquals("dog = 1", workspace.readText("a.py"))
    assertEquals("dog = 22", workspace.readText("b.py"))
  }

  /**
   * **And a change nowhere near the replacement refuses it too**, which is the half a per-edit check
   * would miss: the reference's rule is about the *resource*, not about the characters it put there.
   */
  @Test
  fun aChangeElsewhereInARewrittenFileRefusesTheUndoJustTheSame() = runTest {
    workspace.writeText("a.py", "cat = 1\nkeep = 0")
    val replaced = replace
      .replaceAll(listOf(fileMatch("a.py", "cat = 1")), "dog", cat, preserveCase = false) as ReplaceOutcome.Replaced
    workspace.writeText("a.py", "dog = 1\nkeep = 9")

    assertEquals(UndoOutcome.Refused(listOf("a.py")), replace.undo(replaced.undo))
  }

  @Test
  fun undoingAReplaceThatWroteNothingWritesNothing() = runTest {
    val replaced = replace.replaceAll(emptyList(), "dog", cat, preserveCase = false) as ReplaceOutcome.Replaced

    assertEquals(UndoOutcome.Undone, replace.undo(replaced.undo))
  }

  /**
   * The two counts the confirmation needs, apart from the undo entry the outcome also carries -
   * which no test here spells out, because what it *is* is asserted by undoing it.
   */
  private fun ReplaceOutcome.occurrencesAndFiles(): Pair<Int, Int> {
    val replaced = this as ReplaceOutcome.Replaced
    return replaced.occurrences to replaced.fileCount
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
