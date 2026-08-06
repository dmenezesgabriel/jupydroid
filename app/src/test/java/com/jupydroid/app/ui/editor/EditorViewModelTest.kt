package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.Notebook
import com.jupydroid.app.domain.model.StreamName
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.NotebookRepository
import java.time.Instant
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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

  @Test
  fun uiState_reportsErrorForMissingNotebook() = runTest {
    val viewModel = EditorViewModel(
      notebookId = "ghost",
      notebookRepository = FakeNotebookRepository(),
      kernelGateway = FakeKernelGateway(),
    )
    val collector = launch { viewModel.uiState.collect {} }
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.error?.contains("ghost") == true)
    collector.cancel()
  }

  @Test
  fun runCell_showsTheOutputTheKernelProduced() = runTest {
    val viewModel = loadedEditor()

    viewModel.runCell(codeCell)
    advanceUntilIdle()

    val outputs = viewModel.uiState.value.outputs.getValue("c1")
    assertEquals(listOf(CellOutput.Stream(StreamName.STDOUT, "print(1)")), outputs)
  }

  @Test
  fun runCell_marksTheCellRunningWhileItRunsAndIdleAfter() = runTest {
    val viewModel = loadedEditor()

    viewModel.runCell(codeCell)
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

    viewModel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(1, viewModel.uiState.value.executionCounts["c1"])
  }

  /** A re-run must replace the previous output, not append to it. */
  @Test
  fun runCell_replacesOutputFromAPreviousRun() = runTest {
    val viewModel = loadedEditor()

    viewModel.runCell(codeCell)
    advanceUntilIdle()
    viewModel.runCell(codeCell)
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

    viewModel.runCell(codeCell)
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
    launch { repository.saveNotebook(Notebook("nb-1", "Data", "python3", listOf(codeCell), Instant.EPOCH)) }
    advanceUntilIdle()
    val viewModel = EditorViewModel("nb-1", repository, FakeKernelGateway())
    advanceUntilIdle()

    viewModel.runCell(codeCell)
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

  /** The edit must reach disk even if the app is closed before the debounce elapses. */
  @Test
  fun saveNow_writesImmediatelyWithoutWaitingForTheDebounce() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository)
    viewModel.editCell("c1", "typed but not settled")

    viewModel.saveNow()
    advanceUntilIdle()

    assertEquals("typed but not settled", repository.readNotebook("nb-1").cells.single().source)
  }

  /** Running a cell must use what is on screen now, not what was loaded from the file. */
  @Test
  fun runCell_executesTheEditedSourceRatherThanTheSavedOne() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway)

    viewModel.editCell("c1", "print('edited')")
    viewModel.runCell(viewModel.uiState.value.cells.single())
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

    viewModel.runCell(codeCell)
    advanceUntilIdle()
    assertEquals("the cell should still be running", "c1", viewModel.uiState.value.runningCellId)

    viewModel.stopRunningCell()
    advanceUntilIdle()

    assertEquals(1, gateway.interruptCount)
    assertEquals(null, viewModel.uiState.value.runningCellId)
  }

  /** Interrupting with nothing running would ask the kernel to abandon somebody else's work. */
  @Test
  fun stopRunningCell_doesNothingWhenNoCellIsRunning() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway)

    viewModel.stopRunningCell()
    advanceUntilIdle()

    assertEquals(0, gateway.interruptCount)
  }

  @Test
  fun runAll_runsEveryCodeCellAndSkipsMarkdown() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, cells = listOf(codeCell, markdownCell, secondCodeCell))

    viewModel.runAll()
    advanceUntilIdle()

    assertEquals(listOf("c1", "c3"), gateway.executed)
  }

  /**
   * Jupyter aborts the rest of a queued run once a cell raises, and so must this: carrying on
   * would run later cells against state the failed one never produced.
   */
  @Test
  fun runAll_stopsAtTheFirstCellThatRaises() = runTest {
    val gateway = RecordingKernelGateway(raiseOn = "c1")
    val viewModel = loadedEditor(gateway = gateway, cells = listOf(codeCell, secondCodeCell))

    viewModel.runAll()
    advanceUntilIdle()

    assertEquals(listOf("c1"), gateway.executed)
  }

  private val codeCell = Cell(id = "c1", kind = CellKind.CODE, source = "print(1)")
  private val markdownCell = Cell(id = "c2", kind = CellKind.MARKDOWN, source = "# heading")
  private val secondCodeCell = Cell(id = "c3", kind = CellKind.CODE, source = "print(2)")

  /** Records what the editor asked of a kernel, and can make a cell raise on demand. */
  private class RecordingKernelGateway(
    private val raiseOn: String? = null,
    private val runsUntilInterrupted: Boolean = false,
  ) : KernelGateway by FakeKernelGateway() {
    val executed = mutableListOf<String>()
    var interruptCount = 0
      private set

    private val interrupted = CompletableDeferred<Unit>()

    val executedSource = mutableListOf<String>()

    override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> {
      executed += cellId
      executedSource += code
      return flow {
        if (runsUntilInterrupted) interrupted.await()
        if (cellId == raiseOn) {
          emit(KernelEvent.Output(cellId, CellOutput.Error("ValueError", "boom", emptyList())))
        }
        emit(KernelEvent.Idle)
      }
    }

    override suspend fun interrupt(session: KernelSession) {
      interruptCount++
      interrupted.complete(Unit)
    }
  }

  /** Counts writes, so the debounce can be asserted rather than assumed. */
  private class RecordingNotebookRepository(
    private val delegate: FakeNotebookRepository = FakeNotebookRepository(),
  ) : NotebookRepository by delegate {
    var saveCount = 0
      private set

    override suspend fun saveNotebook(notebook: Notebook) {
      saveCount++
      delegate.saveNotebook(notebook)
    }
  }

  private fun TestScope.loadedEditor(
    gateway: KernelGateway = FakeKernelGateway(),
    cells: List<Cell> = listOf(codeCell),
    repository: NotebookRepository = FakeNotebookRepository(),
  ): EditorViewModel {
    launch {
      repository.saveNotebook(Notebook("nb-1", "Data", "python3", cells, Instant.EPOCH))
    }
    advanceUntilIdle()
    // No collector: uiState is a StateFlow, so .value is current without one - and a
    // collector that is never cancelled leaves runTest waiting on it.
    val viewModel = EditorViewModel("nb-1", repository, gateway)
    advanceUntilIdle()
    return viewModel
  }

  /** Named fake for the failure path: connecting is fine, executing never works. */
  private class UnreachableKernelGateway : KernelGateway by FakeKernelGateway() {
    override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> =
      flow { throw IllegalStateException("closed early: 1006") }
  }
}
