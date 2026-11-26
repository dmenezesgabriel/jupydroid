@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The run chords end to end through [NotebookChordRuns]: `runSelected`'s cells reach the kernel,
 * and the document move lands around them.
 *
 * The claim-before-the-selection-moves rule ([NotebookCellRuns.run]'s comment owns it) applies
 * twice over here: the marked run is read before `runSelected` deselects, and the head it leaves
 * is what the advance or insert then counts from.
 */
class NotebookChordRunsTest {
  // The load and the autosave debounce run on viewModelScope; without Main on the test clock the
  // asserts race a real-time dispatcher (`EditorAutosaveTest` and `NotebookSaveInFlightTest` do
  // the same for the same reason).
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /**
   * `Shift`+`Enter` mid-notebook: the head's cell runs and the head walks down one
   * (`actions.tsx:843` - `runSelected`, then `activeCellIndex++`).
   */
  @Test
  fun runAndAdvance_runsTheCellAndLeavesTheHeadOnTheNext() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, cells = cells("c1", "c2", "c3"))
    viewModel.cellFocusChanged("c1", focused = true)

    viewModel.kernel.chords.runAndAdvance()
    advanceUntilIdle()

    assertEquals(listOf("c1"), gateway.executed)
    assertEquals("c2", viewModel.uiState.value.selectedCellId)
    assertEquals(listOf("c1", "c2", "c3"), viewModel.uiState.value.cells.map { it.id })
  }

  /**
   * At the last cell the reference appends a `defaultCell` and lands on it in edit mode
   * (`actions.tsx:830-849`): ours is the appended cell, the head and the caret ask - edit mode
   * arrives when the field takes the caret, the way [EditorViewModel.cellFocusChanged] works.
   */
  @Test
  fun runAndAdvance_onTheLastCell_appendsACodeCellUnderTheCaret() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, cells = cells("c1", "c2"))
    viewModel.cellFocusChanged("c2", focused = true)

    viewModel.kernel.chords.runAndAdvance()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    val appended = state.cells.last()
    assertEquals(listOf("c2"), gateway.executed)
    assertEquals(3, state.cells.size)
    assertEquals(CellKind.CODE, appended.kind)
    assertEquals(appended.id, state.selectedCellId)
    assertEquals(appended.id, state.cellTakingCaret)
    // *"The new cell creation can be undone"* (`actions.tsx:813`) - `recording(before)` gives it.
    assertTrue(state.undoable.isNotEmpty())
  }

  /** The appended cell is a document edit, so it owes the same debounced save a typed one does. */
  @Test
  fun runAndAdvance_onTheLastCell_schedulesTheSaveTheInsertIs() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository, cells = cells("c1"))
    viewModel.cellFocusChanged("c1", focused = true)
    val writesBefore = repository.saveCount

    viewModel.kernel.chords.runAndAdvance()
    runCurrent()

    // The run's own checkpoint writes first ([NotebookCellRuns.runCells]); the append's
    // debounced save is what the second write is.
    assertEquals(1, repository.saveCount - writesBefore)
    advanceTimeBy(DEBOUNCE_ELAPSED_MILLIS)
    runCurrent()

    assertEquals(2, repository.saveCount - writesBefore)
  }

  /**
   * A marked run's head lands one past its **last** cell - `runSelected`'s `lastIndex`
   * (`actions.tsx:3020-3034`), then `activeCellIndex++`.
   */
  @Test
  fun runAndAdvance_fromAMarkedRun_runsThemAllAndLandsPastThem() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, cells = cells("c1", "c2", "c3", "c4"))
    viewModel.edits.applyTo("c1", CellAction.StartSelecting)
    viewModel.selection.extendTo("c3")

    viewModel.kernel.chords.runAndAdvance()
    advanceUntilIdle()

    assertEquals(listOf("c1", "c2", "c3"), gateway.executed)
    assertEquals("c4", viewModel.uiState.value.selectedCellId)
    assertNull(viewModel.uiState.value.cellTakingCaret)
  }

  /**
   * `Alt`+`Enter` is `runSelected` then `insertCell(activeCellIndex + 1)`
   * (`actions.tsx:894-918`): the run's cell, then a code cell below it, selected and editing.
   */
  @Test
  fun runAndInsert_runsTheCellAndOpensACodeCellBelowIt() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, cells = cells("c1", "c2", "c3"))
    viewModel.cellFocusChanged("c1", focused = true)

    viewModel.kernel.chords.runAndInsert()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(listOf("c1"), gateway.executed)
    assertEquals(4, state.cells.size)
    val inserted = state.cells[1]
    assertEquals(CellKind.CODE, inserted.kind)
    assertEquals("", inserted.source)
    assertEquals(inserted.id, state.selectedCellId)
    assertEquals(inserted.id, state.cellTakingCaret)
    assertTrue(state.undoable.isNotEmpty())
  }

  /** An undo after `Alt`+`Enter` puts the document back to before the insert - run output aside. */
  @Test
  fun runAndInsert_isUndoneAsOneCellOperation() = runTest {
    val viewModel = loadedEditor(cells = cells("c1", "c2"))
    viewModel.cellFocusChanged("c1", focused = true)

    viewModel.kernel.chords.runAndInsert()
    advanceUntilIdle()
    viewModel.edits.undo()

    assertEquals(listOf("c1", "c2"), viewModel.uiState.value.cells.map { it.id })
  }

  /**
   * The marked run is over once the chord has run - `runSelected` ends on `deselectAll`
   * (`actions.tsx:3034`), so afterwards exactly one cell is active and none are marked. The
   * `!notebook.activeCell` bail (`:820-822`, `:875-877`) has no ViewModel-level case here:
   * a loaded notebook always has a head ([toEditorUiState]'s `selectedCellId`), and the
   * stale-head no-op is [RunChordTransformsTest]'s.
   */
  @Test
  fun aChord_leavesOnlyTheHeadItLandedOn() = runTest {
    val viewModel = loadedEditor(cells = cells("c1", "c2", "c3", "c4"))
    viewModel.edits.applyTo("c1", CellAction.StartSelecting)
    viewModel.selection.extendTo("c3")

    viewModel.kernel.chords.runAndAdvance()
    advanceUntilIdle()

    val state = viewModel.uiState.value
    assertEquals(listOf("c4"), state.selectedCellIds)
  }

  private fun cells(vararg ids: String) = ids.map { Cell(it, CellKind.CODE, "print($it)") }

  private companion object {
    const val DEBOUNCE_ELAPSED_MILLIS = 501L
  }
}
