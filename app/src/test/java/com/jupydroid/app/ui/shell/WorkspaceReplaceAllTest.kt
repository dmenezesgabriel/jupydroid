package com.jupydroid.app.ui.shell

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.OpenDocuments
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.data.fake.FakeWorkspaceSearcher
import com.jupydroid.app.domain.model.SearchQuery
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Replace All from the search panel - what is asked, what is written, and what takes it back.
 *
 * Its own class rather than more of `WorkspaceSearchViewModelTest`, along the seam detekt found in
 * the production code (`WorkspaceReplaceAll`): *the workspace searched* and *the workspace
 * rewritten* are two responsibilities, and one commit later the file-length gate said the same
 * thing about the tests.
 *
 * `SearchView.replaceAll` (`searchView.ts:812-853`) with the tree left out. VS Code answers both
 * halves of this panel - JupyterLab registers no workspace search at all (`docs/DECISIONS.md`).
 */
class WorkspaceReplaceAllTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /**
   * The workspace a replace writes to, seeded **apart** from what the walk finds.
   *
   * Which is how it is on a device: `FakeWorkspaceSearcher` answers a query out of a map of its
   * own, and the replace goes to the repository. Held so a test can read the files back.
   */
  private var workspace = FakeWorkspaceRepository()

  private fun searchingIn(vararg files: Pair<String, String>): WorkspaceSearchViewModel {
    workspace = FakeWorkspaceRepository(textByPath = files.toMap())
    return WorkspaceSearchViewModel(
      FakeWorkspaceSearcher(files.toMap()),
      WorkspaceReplace(OpenDocuments(), FakeNotebookRepository(), workspace),
    )
  }

  /** Typed and typed into - the state every replace test starts from. */
  private fun WorkspaceSearchViewModel.found(query: String, replacement: String) {
    queryChanged(SearchQuery(query))
    replaceTextChanged(replacement)
  }

  /**
   * The button is `ReplaceAllAction`, enabled iff the tree has something in it -
   * `setReplaceAllActionState(!this.viewModel.searchResult.isEmpty())` (`searchView.ts:792`).
   */
  @Test
  fun replaceAllIsOfferedOnlyOnceSomethingHasBeenFound() = runTest {
    val model = searchingIn("notes.py" to "cat")
    assertFalse(model.uiState.value.replaceAllEnabled)

    model.queryChanged(SearchQuery("cat"))
    advanceUntilIdle()

    assertTrue(model.uiState.value.replaceAllEnabled)
  }

  /** `if (this.viewModel.searchResult.count() === 0) { return; }` (`searchView.ts:813-815`). */
  @Test
  fun replaceAllWithNothingFoundAsksNothing() = runTest {
    val model = searchingIn("notes.py" to "cat")

    model.replaceAll.requested()

    assertNull(model.uiState.value.confirming)
  }

  /**
   * **The counts are read off the tree, never a fresh walk** - `searchResult.count()` and
   * `fileCount()` (`searchView.ts:816-817`), which is the whole of P2x-1e: a replace that re-ran
   * its query could rewrite a file against matches nobody saw.
   */
  @Test
  fun theConfirmationCountsTheMatchesThatAreOnScreen() = runTest {
    val model = searchingIn("a.py" to "cat cat", "b.py" to "cat")
    model.found("cat", "dog")
    advanceUntilIdle()

    model.replaceAll.requested()

    val asked = ReplaceAllRequest(occurrences = 3, fileCount = 2, replaceValue = "dog")
    assertEquals(asked, model.uiState.value.confirming)
  }

  /** A question asked is not an answer given: nothing is written until it is confirmed. */
  @Test
  fun askingWritesNothing() = runTest {
    val model = searchingIn("a.py" to "cat = 1")
    model.found("cat", "dog")
    advanceUntilIdle()

    model.replaceAll.requested()
    advanceUntilIdle()

    assertEquals("cat = 1", workspace.readText("a.py"))
  }

  @Test
  fun dismissingTheConfirmationWritesNothingAndLeavesTheTreeAlone() = runTest {
    val model = searchingIn("a.py" to "cat = 1")
    model.found("cat", "dog")
    advanceUntilIdle()
    model.replaceAll.requested()

    model.replaceAll.dismissed()
    advanceUntilIdle()

    assertNull(model.uiState.value.confirming)
    assertEquals("cat = 1", workspace.readText("a.py"))
    assertEquals(listOf("a.py"), model.uiState.value.results.map { it.path })
  }

  /**
   * Confirmed, every file is written and **the tree is emptied** - `this.clear()` after the replace
   * resolves (`textSearchHeading.ts:294-302`). The results are gone because they describe a
   * workspace that no longer exists.
   */
  @Test
  fun aConfirmedReplaceWritesEveryFileAndEmptiesTheTree() = runTest {
    val model = searchingIn("a.py" to "cat = 1", "b.py" to "cat = 2")
    model.found("cat", "dog")
    advanceUntilIdle()
    model.replaceAll.requested()

    model.replaceAll.confirmed()
    advanceUntilIdle()

    assertEquals("dog = 1", workspace.readText("a.py"))
    assertEquals("dog = 2", workspace.readText("b.py"))
    assertTrue(model.uiState.value.results.isEmpty())
    assertFalse(model.uiState.value.completed)
  }

  /**
   * The report is built from the counts that were **agreed to**, which is why the reference builds
   * it before it asks (`searchView.ts:820`): a report drawn from a second count could disagree with
   * the question the reader answered.
   */
  @Test
  fun theReportIsTheSentenceTheQuestionWas() = runTest {
    val model = searchingIn("a.py" to "cat = 1", "b.py" to "cat = 2")
    model.found("cat", "dog")
    advanceUntilIdle()
    model.replaceAll.requested()

    model.replaceAll.confirmed()
    advanceUntilIdle()

    assertEquals(listOf("Replaced 2 occurrences across 2 files with 'dog'."), model.uiState.value.report?.lines)
  }

  /**
   * A file that moved on refuses **the whole operation and nothing else**: `_validateBeforePrepare`
   * runs over every resource before a task is prepared (`bulkTextEdits.ts:213-216`), so the other
   * file is untouched - and the tree stands, because it still describes what is on disk.
   */
  @Test
  fun aFileThatChangedRefusesEverythingAndTheResultsStand() = runTest {
    val model = searchingIn("a.py" to "cat = 1", "b.py" to "cat = 2")
    model.found("cat", "dog")
    advanceUntilIdle()
    model.replaceAll.requested()
    workspace.writeText("a.py", "someone else got here first")

    model.replaceAll.confirmed()
    advanceUntilIdle()

    assertEquals("cat = 2", workspace.readText("b.py"))
    assertEquals(listOf("a.py has changed in the meantime"), model.uiState.value.report?.lines)
    assertEquals(listOf("a.py", "b.py"), model.uiState.value.results.map { it.path })
    assertNull(model.uiState.value.report?.undoable)
  }

  /**
   * **One undo across every file** - `MultiModelEditStackElement.undo()` loops the lot
   * (`editStack.ts:330-336`) - and the tree it emptied comes back with them.
   *
   * The tree is a **departure**, named because it is one: an editor's Ctrl+Z leaves the search view
   * none the wiser, so the reference's results stay cleared. Here the panel is what performed the
   * undo, and `WorkspaceReplace.undo` refuses unless every file still reads as the replace left it -
   * so once it succeeds the matches are exactly valid again, and re-drawing them is what says the
   * replace was taken back. Nothing else on this panel would have changed.
   */
  @Test
  fun oneUndoPutsEveryFileBackAndTheResultsWithIt() = runTest {
    val model = searchingIn("a.py" to "cat = 1", "b.py" to "cat = 2")
    model.found("cat", "dog")
    advanceUntilIdle()
    model.replaceAll.requested()
    model.replaceAll.confirmed()
    advanceUntilIdle()

    model.replaceAll.undo()
    advanceUntilIdle()

    assertEquals("cat = 1", workspace.readText("a.py"))
    assertEquals("cat = 2", workspace.readText("b.py"))
    assertEquals(listOf("a.py", "b.py"), model.uiState.value.results.map { it.path })
    assertNull(model.uiState.value.report)
  }

  /**
   * A file written since the replace refuses the undo - `_checkWorkspaceUndo` wants this element to
   * be the closest past element on **every** impacted resource (`undoRedoService.ts:853-869`).
   *
   * And the undo is **dropped rather than left to retry**: the refusal is a file that moved on, and
   * nothing here will move it back, so an offer that can only fail again is the control this app
   * refuses to draw.
   */
  @Test
  fun aFileWrittenSinceTheReplaceRefusesTheUndoAndTakesTheOfferAway() = runTest {
    val model = searchingIn("a.py" to "cat = 1", "b.py" to "cat = 2")
    model.found("cat", "dog")
    advanceUntilIdle()
    model.replaceAll.requested()
    model.replaceAll.confirmed()
    advanceUntilIdle()
    workspace.writeText("a.py", "edited after the replace")

    model.replaceAll.undo()
    advanceUntilIdle()

    assertEquals("edited after the replace", workspace.readText("a.py"))
    assertEquals("dog = 2", workspace.readText("b.py"))
    assertEquals(
      listOf("Could not undo 'Workspace Edit' across all files because changes were made to a.py"),
      model.uiState.value.report?.lines,
    )
    assertNull(model.uiState.value.report?.undoable)
  }

  /**
   * A new walk clears the report - `showEmptyStage()` calls `clearMessage()` (`searchView.ts:1996`).
   *
   * Which is also the undo's whole lifetime, and the reason it is offered beside the report rather
   * than floated over the panel: one undo, living exactly as long as the sentence that offers it.
   */
  @Test
  fun aNewWalkClearsTheReportAndTheUndoWithIt() = runTest {
    val model = searchingIn("a.py" to "cat = 1")
    model.found("cat", "dog")
    advanceUntilIdle()
    model.replaceAll.requested()
    model.replaceAll.confirmed()
    advanceUntilIdle()

    model.queryChanged(SearchQuery("dog"))
    advanceUntilIdle()

    assertNull(model.uiState.value.report)
  }
}
