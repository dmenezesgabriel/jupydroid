package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.domain.model.TextEdit
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The notebook seen as the thing a workspace replace edits - `OpenDocument`, whose resources are
 * its **cells**.
 *
 * `ReplaceService.createEdits` builds an edit against `match.cell?.uri` for a notebook match
 * (`replaceService.ts:206-214`), so a cell genuinely is a resource in the reference too. The index
 * is the one `MatchedLine.cell` carries, and P2x-4a's reason for an index rather than an id holds
 * here for the same reason: `CellFile.toDomain` mints a `UUID` for a cell nbformat left without one.
 */
class EditorViewModelOpenDocumentTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val cells = listOf(codeCell, secondCodeCell)

  @Test
  fun theOpenNotebookIsReachedByItsPath() = runTest {
    val open = OpenDocuments()
    val viewModel = loadedEditor(cells = cells, openDocuments = open)

    assertSame(viewModel.document, open.at("nb-1"))
  }

  /** A cell is the resource; the notebook as a whole is not one a match can address. */
  @Test
  fun eachCellIsAResourceAndTheNotebookItselfIsNot() = runTest {
    val open = OpenDocuments()
    loadedEditor(cells = cells, openDocuments = open)

    assertEquals(codeCell.source, open.at("nb-1")?.sourceOf(cell = 0))
    assertEquals(secondCodeCell.source, open.at("nb-1")?.sourceOf(cell = 1))
    assertNull(open.at("nb-1")?.sourceOf(cell = null))
  }

  /** A notebook that shrank under the walk has no such cell, which the engine refuses on. */
  @Test
  fun aCellThatIsNotThereReadsAsNothing() = runTest {
    val open = OpenDocuments()
    loadedEditor(cells = cells, openDocuments = open)

    assertNull(open.at("nb-1")?.sourceOf(cell = 9))
  }

  @Test
  fun anEditRewritesTheCellItNames() = runTest {
    val open = OpenDocuments()
    val viewModel = loadedEditor(cells = cells, openDocuments = open)

    open.at("nb-1")?.applyEdits(cell = 0, edits = listOf(TextEdit(0..4, "dog")))
    advanceUntilIdle()

    assertEquals("dog" + codeCell.source.drop(5), viewModel.uiState.value.cells[0].source)
    assertEquals(secondCodeCell.source, viewModel.uiState.value.cells[1].source)
  }

  /**
   * **The field is told**, which is `sourceRevisions`' whole job - a cell's `TextFieldState` cannot
   * see a changed `Cell.source` on its own (`CellSourceTracking.adoptRewrittenSource`).
   */
  @Test
  fun anEditTellsTheRewrittenCellsFieldItsTextChanged() = runTest {
    val open = OpenDocuments()
    val viewModel = loadedEditor(cells = cells, openDocuments = open)
    val before = viewModel.uiState.value.sourceRevisions[codeCell.id] ?: 0

    open.at("nb-1")?.applyEdits(cell = 0, edits = listOf(TextEdit(0..4, "dog")))

    assertEquals(before + 1, viewModel.uiState.value.sourceRevisions[codeCell.id])
    assertNull(viewModel.uiState.value.sourceRevisions[secondCodeCell.id])
  }

  /**
   * **And it is not an entry in this notebook's own history.** The reference records one
   * `MultiModelEditStackElement` for the whole replace (`bulkTextEdits.ts:319-330`); a second entry
   * here would make one command undoable twice, in two scopes that could then disagree. P2x-5d is
   * where the one entry lives.
   */
  @Test
  fun anEditIsNotAnEntryInTheNotebooksOwnUndoHistory() = runTest {
    val open = OpenDocuments()
    val viewModel = loadedEditor(cells = cells, openDocuments = open)

    open.at("nb-1")?.applyEdits(cell = 0, edits = listOf(TextEdit(0..4, "dog")))

    assertTrue(viewModel.uiState.value.undoable.isEmpty())
  }

  /** It is still a change to the document, so it is written back like every other one. */
  @Test
  fun anEditMakesTheNotebookDirtyAndIsSavedOut() = runTest {
    val open = OpenDocuments()
    val repository = com.jupydroid.app.data.fake.FakeNotebookRepository()
    loadedEditor(cells = cells, repository = repository, openDocuments = open)

    open.at("nb-1")?.applyEdits(cell = 0, edits = listOf(TextEdit(0..4, "dog")))
    advanceUntilIdle()

    assertEquals("dog" + codeCell.source.drop(5), repository.readNotebook("nb-1").cells[0].source)
  }

  @Test
  fun aClosedNotebookIsNotOpenAnyMore() = runTest {
    val open = OpenDocuments()
    val viewModel = loadedEditor(cells = cells, openDocuments = open)

    viewModel.onCleared()

    assertNull(open.at("nb-1"))
  }

  /** The path is read live, so a rename moves the document - `Context._updatePath`. */
  @Test
  fun aRenamedNotebookIsReachedByWhereItLivesNow() = runTest {
    val open = OpenDocuments()
    val viewModel = loadedEditor(cells = cells, openDocuments = open)

    viewModel.renameNotebook("Renamed")
    advanceUntilIdle()

    assertNull(open.at("nb-1"))
    assertSame(viewModel.document, open.at(viewModel.uiState.value.notebookId))
  }
}
