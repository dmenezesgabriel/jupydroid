package com.jupydroid.app.ui.home

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.WorkspaceEntry
import com.jupydroid.app.domain.model.WorkspaceChange
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class HomeViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun uiState_startsEmpty() = runTest {
    val viewModel = HomeViewModel(FakeNotebookRepository(), FakeWorkspaceRepository())

    assertEquals(HomeUiState(emptyList()), viewModel.uiState.value)
  }

  @Test
  fun uiState_reflectsRepositoryContents() = runTest {
    val repository = FakeNotebookRepository()
    repository.createNotebook(title = "Untitled", kernelId = "python3")
    val viewModel = HomeViewModel(repository, FakeWorkspaceRepository())

    val collector = launch { viewModel.uiState.collect {} }
    advanceUntilIdle()

    assertEquals(listOf("Untitled"), viewModel.uiState.value.notebooks.map { it.title })
    collector.cancel()
  }

  @Test
  fun createNotebook_addsRowToUiState() = runTest {
    val viewModel = HomeViewModel(FakeNotebookRepository(), FakeWorkspaceRepository())
    val collector = launch { viewModel.uiState.collect {} }
    advanceUntilIdle()

    viewModel.createNotebook(title = "Untitled", kernelId = "python3")
    advanceUntilIdle()

    assertEquals(listOf("Untitled"), viewModel.uiState.value.notebooks.map { it.title })
    collector.cancel()
  }

  @Test
  fun renameNotebook_updatesRowTitle() = runTest {
    val viewModel = HomeViewModel(FakeNotebookRepository(), FakeWorkspaceRepository())
    val collector = launch { viewModel.uiState.collect {} }
    advanceUntilIdle()
    viewModel.createNotebook(title = "Untitled", kernelId = "python3")
    advanceUntilIdle()
    val id = viewModel.uiState.value.notebooks.single().id

    viewModel.renameNotebook(id = id, title = "Math notes")
    advanceUntilIdle()

    assertEquals(listOf("Math notes"), viewModel.uiState.value.notebooks.map { it.title })
    collector.cancel()
  }

  @Test
  fun deleteNotebook_removesRowFromUiState() = runTest {
    val viewModel = HomeViewModel(FakeNotebookRepository(), FakeWorkspaceRepository())
    val collector = launch { viewModel.uiState.collect {} }
    advanceUntilIdle()
    viewModel.createNotebook(title = "Untitled", kernelId = "python3")
    advanceUntilIdle()
    val id = viewModel.uiState.value.notebooks.single().id

    viewModel.deleteNotebook(id = id)
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.notebooks.isEmpty())
    collector.cancel()
  }

  /**
   * "New Python file" creates a *file* named `untitled.py`, not a notebook that only looks like
   * one - the reference's `new_untitled({type: "file", ext})` - and reports the path it made, so
   * the caller can open exactly what was created.
   */
  @Test
  fun createPythonFile_makesTheFileAndHandsBackItsPath() = runTest {
    val workspace = MakingWorkspaceRepository()
    val viewModel = HomeViewModel(FakeNotebookRepository(), workspace)
    var createdPath: String? = null

    viewModel.createPythonFile { createdPath = it }
    advanceUntilIdle()

    assertEquals("", workspace.directory)
    assertEquals("py", workspace.extension)
    assertEquals("untitled.py", createdPath)
  }
}

/** A workspace whose creation succeeds, so Home's "New Python file" has something to report. */
private class MakingWorkspaceRepository : WorkspaceRepository {
  var directory: String? = null
  var extension: String? = null

  override val changes: Flow<WorkspaceChange> = emptyFlow()

  override suspend fun list(path: String): List<WorkspaceEntry> = emptyList()

  override suspend fun readText(path: String): String = error("not read")

  override suspend fun writeText(path: String, text: String) = Unit

  override suspend fun createFile(directory: String, extension: String): String {
    this.directory = directory
    this.extension = extension
    return "untitled.py"
  }

  override suspend fun createFolder(directory: String): String = error("not created")

  override suspend fun rename(path: String, name: String): String = error("not renamed")

  override suspend fun overwrite(path: String, name: String): String = error("not overwritten")

  override suspend fun delete(path: String) = error("not deleted")
}
