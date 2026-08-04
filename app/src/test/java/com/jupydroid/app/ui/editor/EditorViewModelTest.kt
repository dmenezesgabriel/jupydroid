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
import java.time.Instant
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

  private val codeCell = Cell(id = "c1", kind = CellKind.CODE, source = "print(1)")

  private fun TestScope.loadedEditor(gateway: KernelGateway = FakeKernelGateway()): EditorViewModel {
    val repository = FakeNotebookRepository()
    launch {
      repository.saveNotebook(
        Notebook("nb-1", "Data", "python3", listOf(codeCell), Instant.EPOCH),
      )
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
