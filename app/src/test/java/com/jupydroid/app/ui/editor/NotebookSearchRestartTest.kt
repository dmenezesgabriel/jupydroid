package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.SearchQuery
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.KernelEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

/**
 * An open search follows the notebook - *"`startQuery` is also used as 'restartQuery'"*
 * (`packages/notebook/src/searchprovider.ts:391-395`), and the restarts are not only for typing:
 * `cells.changed` rebuilds the providers (`:559-590`) and `outputLengthChanged` rebuilds a cell's
 * output providers (`packages/cells/src/searchprovider.ts:76-81`), each re-querying. A find bar
 * left open across one of these counts what is actually there, not what was.
 */
class NotebookSearchRestartTest {
  private fun code(id: String, source: String) = Cell(id, CellKind.CODE, source)

  private val notebook = EditorUiState(
    cells = listOf(code("a", "cat"), code("b", "dog"), code("c", "cat cat")),
    selectedCellId = "a",
    loaded = true,
  )

  private fun searchingIn(state: EditorUiState) =
    MutableStateFlow(state.withSearchOpened().withSearchQuery(SearchQuery("cat")))

  private fun cellEditsOver(state: MutableStateFlow<EditorUiState>, scope: TestScope) = NotebookCellEdits(
    state,
    DocumentAutosave(scope, MutableStateFlow(EditorSettings()), { false }) {},
  )

  @Test
  fun deletingACellUnderAnOpenSearchDropsItsHits() = runTest {
    val state = searchingIn(notebook)

    cellEditsOver(state, TestScope(testScheduler)).applyTo("a", CellAction.Delete)

    assertEquals("1/2", state.value.searchCounter())
  }

  @Test
  fun duplicatingACellUnderAnOpenSearchCountsItsCopy() = runTest {
    val state = searchingIn(notebook)

    cellEditsOver(state, TestScope(testScheduler)).applyTo("a", CellAction.Duplicate)

    assertEquals("1/4", state.value.searchCounter())
  }

  /** Undo restores cells, so the search restores what they matched. */
  @Test
  fun undoingACellOperationUnderAnOpenSearchFindsAgain() = runTest {
    val state = searchingIn(notebook)
    val edits = cellEditsOver(state, TestScope(testScheduler))
    edits.applyTo("a", CellAction.Delete)
    assertEquals("1/2", state.value.searchCounter())

    edits.undo()

    assertEquals("1/3", state.value.searchCounter())
  }

  /**
   * A rename's whole-document answer is more source under the search, and is re-found. The
   * landing is `2/4` rather than `1/4` because `cellsRenamed` moves the head onto the renamed
   * cell first - the recompute then lands beside it.
   */
  @Test
  fun aRenameUnderAnOpenSearchFindsTheNewText() = runTest {
    val state = searchingIn(notebook)

    cellEditsOver(state, TestScope(testScheduler)).cellsRenamed(
      "b",
      mapOf("b" to "cat now"),
      askedSources = mapOf("a" to "cat", "b" to "dog", "c" to "cat cat"),
    )

    assertEquals("2/4", state.value.searchCounter())
  }

  /**
   * A run printing the query is a hit while the `output` filter is on - the reference's
   * `outputLengthChanged` re-queries that cell's output providers
   * (`packages/cells/src/searchprovider.ts:298-316`).
   */
  @Test
  fun anArrivingOutputIsCountedWhileOutputsAreSearched() {
    val listening = EditorUiState(
      cells = listOf(code("a", "x = 1")),
      selectedCellId = "a",
      loaded = true,
    ).withSearchOpened().withSearchOutputs(true).withSearchQuery(SearchQuery("cat"))
    assertEquals("-/-", listening.searchCounter())

    val arrived = listening.after(
      KernelEvent.Output("a", CellOutput.Stream(StreamName.STDOUT, "the cat sat")),
    )

    assertEquals("1/1", arrived.searchCounter())
  }

  /** With the filter off an arriving output changes nothing the search reads. */
  @Test
  fun anArrivingOutputChangesNothingWhileOutputsAreNotSearched() {
    val listening = EditorUiState(
      cells = listOf(code("a", "x = 1")),
      selectedCellId = "a",
      loaded = true,
    ).withSearchOpened().withSearchQuery(SearchQuery("cat"))

    val arrived = listening.after(
      KernelEvent.Output("a", CellOutput.Stream(StreamName.STDOUT, "the cat sat")),
    )

    assertEquals("-/-", arrived.searchCounter())
  }

  /** A re-run replaces its outputs, so the hits they carried go first. */
  @Test
  fun aRerunDropsTheHitsItsEarlierOutputsHad() {
    val ran = EditorUiState(
      cells = listOf(code("a", "x = 1")),
      selectedCellId = "a",
      loaded = true,
      outputs = mapOf("a" to listOf(CellOutput.Stream(StreamName.STDOUT, "the cat sat"))),
    ).withSearchOpened().withSearchQuery(SearchQuery("cat")).withSearchOutputs(true)
    assertEquals("1/1", ran.searchCounter())

    val rerun = ran.startingRun("a", Instant.now())

    assertEquals("-/-", rerun.searchCounter())
  }
}
