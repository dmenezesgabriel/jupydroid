package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.domain.model.Notebook
import java.time.Instant
import kotlinx.coroutines.launch
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
    val repository = FakeNotebookRepository()
    repository.saveNotebook(
      Notebook(
        id = "nb-1",
        title = "Data",
        kernelId = "python3",
        cells = listOf(Cell(id = "c1", kind = CellKind.CODE, source = "print(1)")),
        updatedAt = Instant.EPOCH,
      ),
    )
    val viewModel = EditorViewModel(notebookId = "nb-1", notebookRepository = repository)
    val collector = launch { viewModel.uiState.collect {} }
    advanceUntilIdle()

    assertEquals("Data", viewModel.uiState.value.title)
    assertEquals(listOf("print(1)"), viewModel.uiState.value.cells.map { it.source })
    assertTrue(viewModel.uiState.value.loaded)
    collector.cancel()
  }

  @Test
  fun uiState_reportsErrorForMissingNotebook() = runTest {
    val viewModel = EditorViewModel(notebookId = "ghost", notebookRepository = FakeNotebookRepository())
    val collector = launch { viewModel.uiState.collect {} }
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.error?.contains("ghost") == true)
    collector.cancel()
  }
}
