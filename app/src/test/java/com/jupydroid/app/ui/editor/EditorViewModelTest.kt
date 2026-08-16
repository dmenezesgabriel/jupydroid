package com.jupydroid.app.ui.editor

import androidx.compose.ui.text.TextRange
import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.StreamName
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EditorViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun uiState_loadsNotebookTitleAndCells() = runTest {
    val viewModel = loadedEditor()

    assertEquals("Data", viewModel.uiState.value.title)
    assertEquals(listOf("print(1)"), viewModel.uiState.value.cells.map { it.source })
    assertTrue(viewModel.uiState.value.loaded)
  }

  /**
   * **An opened notebook has an active cell, and it is the first.** `Notebook.activeCellIndex`
   * starts at `-1` and its setter clamps to `Math.max(newValue, 0)` once there are widgets
   * (`packages/notebook/src/widget.ts:1965-1974`), so the first `onCellInserted` of a load leaves
   * it at 0 - which is why the reference draws its active-cell bar on cell one before anything is
   * touched.
   *
   * Ours left it null until a field took focus, and the ranged runs are what made that visible:
   * `run-all-above` and `run-all-below` are head-relative, so on a freshly opened notebook both
   * rows were greyed with nothing to say why.
   */
  @Test
  fun uiState_makesTheFirstCellActiveOnLoad() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell))

    assertEquals("c1", viewModel.uiState.value.selectedCellId)
    // Active, not *marked*: an anchor would put the selection bar up over a notebook nobody has
    // touched. `deselectAll` leaves `activeCellIndex` alone for the same reason (`widget.ts:2196`).
    assertFalse(viewModel.uiState.value.selecting)
    assertEquals(CellMode.COMMAND, viewModel.uiState.value.mode)
  }

  @Test
  fun uiState_reportsErrorForMissingNotebook() = runTest {
    val viewModel = EditorViewModel(
      notebookId = "ghost",
      notebookRepository = FakeNotebookRepository(),
      workspaceRepository = FakeWorkspaceRepository(),
      kernelGateway = FakeKernelGateway(),
      applicationScope = this,
      activeCellRuns = ActiveCellRuns(),
    )
    val collector = launch { viewModel.uiState.collect {} }
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.error?.contains("ghost") == true)
    collector.cancel()
  }

  @Test
  fun runCell_showsTheOutputTheKernelProduced() = runTest {
    val viewModel = loadedEditor()

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    val outputs = viewModel.uiState.value.outputs.getValue("c1")
    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "print(1)")), outputs)
  }

  @Test
  fun runCell_marksTheCellRunningWhileItRunsAndIdleAfter() = runTest {
    val viewModel = loadedEditor()

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(
      "nothing should still be running once the kernel goes idle",
      null,
      viewModel.uiState.value.runningCellId,
    )
  }

  @Test
  fun runCell_recordsTheExecutionCountTheKernelAssigned() = runTest {
    val viewModel = loadedEditor()

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(1, viewModel.uiState.value.executionCounts["c1"])
  }

  /** A re-run must replace the previous output, not append to it. */
  @Test
  fun runCell_replacesOutputFromAPreviousRun() = runTest {
    val viewModel = loadedEditor()

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(1, viewModel.uiState.value.outputs.getValue("c1").size)
  }

  /**
   * A kernel that cannot be reached must say so in the cell, not leave a spinner forever.
   * This is the path a dropped socket takes, and it is the one users notice.
   */
  @Test
  fun runCell_surfacesAKernelFailureAsCellOutputAndStopsRunning() = runTest {
    val viewModel = loadedEditor(gateway = UnreachableKernelGateway())

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    val error = viewModel.uiState.value.outputs.getValue("c1").single()
    assertTrue("expected an error output, got $error", error is CellOutput.Error)
    assertEquals(null, viewModel.uiState.value.runningCellId)
  }

  // --- Step 7.1: outputs survive closing the notebook ---

  @Test
  fun uiState_showsOutputsSavedByAPreviousSession() = runTest {
    val saved = codeCell.copy(
      outputs = listOf(CellOutput.Stream(StreamName.STDOUT, "from last time")),
      executionCount = 9,
    )
    val viewModel = loadedEditor(cells = listOf(saved))

    assertEquals(saved.outputs, viewModel.uiState.value.outputs["c1"])
    assertEquals(9, viewModel.uiState.value.executionCounts["c1"])
  }

  /** Closing a notebook must not throw away what the kernel produced. */
  @Test
  fun runCell_savesItsOutputSoItSurvivesReopening() = runTest {
    val repository = FakeNotebookRepository()
    launch { repository.saveNotebook(Notebook("nb-1", "Data", "python3", listOf(codeCell))) }
    advanceUntilIdle()
    val viewModel =
      EditorViewModel("nb-1", repository, FakeWorkspaceRepository(), FakeKernelGateway(), this, ActiveCellRuns())
    advanceUntilIdle()

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    val reread = repository.readNotebook("nb-1").cells.single()
    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "print(1)")), reread.outputs)
    assertEquals(1, reread.executionCount)
  }

  // --- Step 5.3: editing a cell ---

  @Test
  fun editCell_updatesTheSourceAndMarksTheNotebookDirty() = runTest {
    val viewModel = loadedEditor()

    viewModel.editCell("c1", "print(2)")

    assertEquals("print(2)", viewModel.uiState.value.cells.single().source)
    assertTrue(viewModel.uiState.value.dirty)
  }

  /**
   * A save per keystroke would write the file on every character. Debounced, so typing costs
   * one write - and the debounce is what the dirty flag exists to make visible.
   */
  @Test
  fun editCell_savesOnceAfterTypingStops() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository)
    // loadedEditor seeds the notebook through this same repository, so count from here.
    val writesBefore = repository.saveCount

    viewModel.editCell("c1", "p")
    viewModel.editCell("c1", "pr")
    viewModel.editCell("c1", "pri")
    advanceUntilIdle()

    assertEquals(1, repository.saveCount - writesBefore)
    assertEquals("pri", repository.readNotebook("nb-1").cells.single().source)
    assertTrue("a saved notebook is not dirty", !viewModel.uiState.value.dirty)
  }

  // The edit-must-reach-disk-before-the-debounce case moved to EditorTeardownTest with
  // saveNow(): leaving the screen is what triggers it, and onCleared is where that now lives.

  /** Running a cell must use what is on screen now, not what was loaded from the file. */
  @Test
  fun runCell_executesTheEditedSourceRatherThanTheSavedOne() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway)

    viewModel.editCell("c1", "print('edited')")
    viewModel.kernel.runCell(viewModel.uiState.value.cells.single())
    advanceUntilIdle()

    assertEquals(listOf("c1"), gateway.executed)
    assertEquals("print('edited')", gateway.executedSource.single())
  }

  // --- Step 4b: Stop and Run All ---

  /**
   * The cell only finishes because it was interrupted, which is what a real kernel does:
   * running it to completion first and then stopping would assert nothing.
   */
  @Test
  fun stopRunningCell_asksTheKernelToInterrupt() = runTest {
    val gateway = RecordingKernelGateway(runsUntilInterrupted = true)
    val viewModel = loadedEditor(gateway = gateway)

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    assertEquals("the cell should still be running", "c1", viewModel.uiState.value.runningCellId)

    viewModel.kernel.stopRunningCell()
    advanceUntilIdle()

    assertEquals(1, gateway.interruptCount)
    assertEquals(null, viewModel.uiState.value.runningCellId)
  }

  /** Interrupting with nothing running would ask the kernel to abandon somebody else's work. */
  @Test
  fun stopRunningCell_doesNothingWhenNoCellIsRunning() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway)

    viewModel.kernel.stopRunningCell()
    advanceUntilIdle()

    assertEquals(0, gateway.interruptCount)
  }

  @Test
  fun runAll_runsEveryCodeCellAndSkipsMarkdown() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, cells = listOf(codeCell, markdownCell, secondCodeCell))

    viewModel.kernel.run(RunRange.ALL)
    advanceUntilIdle()

    assertEquals(listOf("c1", "c3"), gateway.executed)
  }

  /**
   * Jupyter aborts the rest of a queued run once a cell raises, and so must this: carrying on
   * would run later cells against state the failed one never produced.
   */
  /**
   * **A range run renders the markdown cells in it rather than skipping them**, which is what
   * `Private.runCells` does: it is handed every widget and decides per cell what running one means,
   * and for markdown that is `rendered = true` (`packages/notebook/src/cellexecutor.ts`).
   *
   * Ours filtered the queue to code cells and left an open markdown editor open, because
   * rendered-ness lived in the composable rather than in the state. It lives here now.
   */
  @Test
  fun runAll_rendersTheMarkdownCellsItPassesAndExecutesOnlyTheCode() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, cells = listOf(codeCell, markdownCell, secondCodeCell))
    viewModel.markdownEditRequested(markdownCell.id)

    viewModel.kernel.run(RunRange.ALL)
    advanceUntilIdle()

    assertTrue("running a markdown cell renders it", viewModel.uiState.value.isMarkdownRendered(markdownCell.id))
    assertEquals("and never reaches the kernel", listOf("c1", "c3"), gateway.executed)
  }

  @Test
  fun runAll_stopsAtTheFirstCellThatRaises() = runTest {
    val gateway = RecordingKernelGateway(raiseOn = "c1")
    val viewModel = loadedEditor(gateway = gateway, cells = listOf(codeCell, secondCodeCell))

    viewModel.kernel.run(RunRange.ALL)
    advanceUntilIdle()

    assertEquals(listOf("c1"), gateway.executed)
  }

  // --- Audit A2: a notebook being looked at never has zero cells ---

  /**
   * JupyterLab's rule, and it lives at the view layer rather than at creation: when a model
   * with no cells is displayed it inserts a default one (`packages/notebook/src/widget.ts:606`).
   * One rule covers a new notebook, an imported one and the last cell being deleted.
   */
  @Test
  fun openingANotebookWithNoCellsGivesItOneToTypeIn() = runTest {
    val viewModel = loadedEditor(cells = emptyList())

    val cell = viewModel.uiState.value.cells.single()
    assertEquals(CellKind.CODE, cell.kind)
    assertEquals("", cell.source)
    assertTrue("the cell needs an id of its own", cell.id.isNotBlank())
  }

  /** Opening a notebook must not rewrite it: that would bump its timestamp and reorder Recents. */
  @Test
  fun openingANotebookWithNoCellsDoesNotWriteItBack() = runTest {
    val repository = FakeNotebookRepository()
    loadedEditor(cells = emptyList(), repository = repository)
    advanceUntilIdle()

    assertTrue(repository.readNotebook("nb-1").cells.isEmpty())
  }

  @Test
  fun openingANotebookWithNoCellsIsNotAnEdit() = runTest {
    assertTrue("nothing was typed", !loadedEditor(cells = emptyList()).uiState.value.dirty)
  }

  // --- Step 4b: the cell-management half ---

  @Test
  fun applyTo_insertsMovesDuplicatesAndDeletesThroughOneEntryPoint() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell))

    viewModel.edits.applyTo("c1", CellAction.Insert(CellKind.MARKDOWN))
    assertEquals(3, viewModel.uiState.value.cells.size)
    assertEquals(CellKind.MARKDOWN, viewModel.uiState.value.cells[1].kind)

    viewModel.edits.applyTo("c1", CellAction.Move(CellMove.DOWN))
    assertEquals(1, viewModel.uiState.value.cells.indexOfFirst { it.id == "c1" })

    viewModel.edits.applyTo("c1", CellAction.Duplicate)
    assertEquals(4, viewModel.uiState.value.cells.size)

    viewModel.edits.applyTo("c1", CellAction.Delete)
    assertTrue(viewModel.uiState.value.cells.none { it.id == "c1" })
  }

  /** Every inserted or duplicated cell needs an id of its own, or the list keys collide. */
  @Test
  fun applyTo_givesEveryNewCellAnIdNobodyElseHas() = runTest {
    val viewModel = loadedEditor()

    repeat(3) { viewModel.edits.applyTo("c1", CellAction.Duplicate) }

    val ids = viewModel.uiState.value.cells.map { it.id }
    assertEquals("ids must be unique, got $ids", ids.size, ids.toSet().size)
  }

  /** Re-ordering a notebook must survive leaving the screen exactly as typing does. */
  @Test
  fun applyTo_savesTheNewOrderOnTheSameDebounceAsAnEdit() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell), repository = repository)

    viewModel.edits.applyTo("c3", CellAction.Move(CellMove.UP))
    advanceUntilIdle()

    assertEquals(listOf("c3", "c1"), repository.readNotebook("nb-1").cells.map { it.id })
  }

  @Test
  fun applyTo_clearOutputsRemovesWhatTheCellHadProduced() = runTest {
    val viewModel = loadedEditor()
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.outputs["c1"].orEmpty().isNotEmpty())

    viewModel.edits.applyTo("c1", CellAction.ClearOutputs)

    assertTrue(viewModel.uiState.value.outputs["c1"].isNullOrEmpty())
    assertEquals(null, viewModel.uiState.value.executionCounts["c1"])
  }

  // --- P2b: splitting a cell ---

  /**
   * A split is an edit to the document like any other, so it reaches disk on the same debounce -
   * and it reaches it holding the **editor's** text, which is what a caret that beat the 150ms
   * report would otherwise have split against a stale copy.
   */
  @Test
  fun split_cutsTheEditorsTextInTwoAndSavesBoth() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = loadedEditor(repository = repository)

    viewModel.edits.applyTo("c1", CellAction.Split, CellEditorText("print(1)\nprint(2)", TextRange(9)))
    advanceUntilIdle()

    val saved = repository.readNotebook("nb-1").cells
    assertEquals(listOf("print(1)", "print(2)"), saved.map { it.source })
    // The original cell is the last fragment, so its id is the one that stayed.
    assertEquals("c1", saved.last().id)
  }

  // --- P2a: taking a cell operation back ---

  /** Undoing is an edit to the document, so it is written on the debounce every other one uses. */
  @Test
  fun undo_putsTheCellBackAndSavesOnTheSameDebounce() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell), repository = repository)

    viewModel.edits.applyTo("c3", CellAction.Delete)
    viewModel.edits.undo()
    advanceUntilIdle()

    assertEquals(listOf("c1", "c3"), viewModel.uiState.value.cells.map { it.id })
    assertEquals(listOf("c1", "c3"), repository.readNotebook("nb-1").cells.map { it.id })
  }

  @Test
  fun redo_replaysWhatTheUndoTookBack() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell))

    viewModel.edits.applyTo("c3", CellAction.Delete)
    viewModel.edits.undo()
    viewModel.edits.redo()
    advanceUntilIdle()

    assertEquals(listOf("c1"), viewModel.uiState.value.cells.map { it.id })
  }

  /** Nothing is offered to press until there is something to take back - the button reads this. */
  @Test
  fun uiState_offersNothingToUndoUntilACellOperationHappens() = runTest {
    val viewModel = loadedEditor(cells = listOf(codeCell, secondCodeCell))
    assertTrue(viewModel.uiState.value.undoable.isEmpty())
    assertTrue(viewModel.uiState.value.redoable.isEmpty())

    viewModel.edits.applyTo("c1", CellAction.Duplicate)

    assertEquals(1, viewModel.uiState.value.undoable.size)
    assertTrue(viewModel.uiState.value.redoable.isEmpty())
  }
}
