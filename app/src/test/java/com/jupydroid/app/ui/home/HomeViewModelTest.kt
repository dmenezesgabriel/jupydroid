package com.jupydroid.app.ui.home

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeNotebookRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun uiState_startsEmpty() = runTest {
    val viewModel = HomeViewModel(FakeNotebookRepository())

    assertEquals(HomeUiState(emptyList()), viewModel.uiState.value)
  }

  @Test
  fun uiState_reflectsRepositoryContents() = runTest {
    val repository = FakeNotebookRepository()
    repository.createNotebook(title = "Untitled", kernelId = "python3")
    val viewModel = HomeViewModel(repository)

    val collector = launch { viewModel.uiState.collect {} }
    advanceUntilIdle()

    assertEquals(listOf("Untitled"), viewModel.uiState.value.notebooks.map { it.title })
    collector.cancel()
  }

  @Test
  fun createNotebook_addsRowToUiState() = runTest {
    val viewModel = HomeViewModel(FakeNotebookRepository())
    val collector = launch { viewModel.uiState.collect {} }
    advanceUntilIdle()

    viewModel.createNotebook(title = "Untitled", kernelId = "python3")
    advanceUntilIdle()

    assertEquals(listOf("Untitled"), viewModel.uiState.value.notebooks.map { it.title })
    collector.cancel()
  }
}
