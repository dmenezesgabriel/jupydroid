package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeWorkspaceWatcher
import com.jupydroid.app.domain.model.EditorSettings
import com.jupydroid.app.domain.model.Notebook
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A notebook open with no unsaved work shows the cells something else wrote to its file, by itself.
 *
 * vscode@3f3380c `storedFileWorkingCopyManager.ts` `onDidFilesChange`: the rule a text model
 * follows, over a notebook's working copy - re-read when clean, never when dirty.
 */
class EditorFollowTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val repository = FakeNotebookRepository()
  private val watcher = FakeWorkspaceWatcher()

  /** What a cell or a second client does: a write that asks nobody, then the watcher's report. */
  private suspend fun writtenOutside() {
    val stamp = repository.saveNotebook(
      Notebook("nb-1", "Data", "python3", listOf(codeCell.copy(source = "theirs = 1"))),
    )
    watcher.announceWrite("nb-1", stamp)
  }

  @Test
  fun aCleanNotebookShowsTheCellsWrittenUnderIt() = runTest {
    val viewModel = loadedEditor(repository = repository, watcher = watcher)

    writtenOutside()
    advanceUntilIdle()

    assertEquals(listOf("theirs = 1"), viewModel.uiState.value.cells.map { it.source })
    assertFalse(viewModel.uiState.value.dirty)
  }

  /** The edit stays, and the save that would have replaced their write still asks (ADR-0090). */
  @Test
  fun aDirtyNotebookKeepsItsEdit() = runTest {
    val viewModel = loadedEditor(
      repository = repository,
      watcher = watcher,
      editorSettings = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false)),
    )
    viewModel.editCell(codeCell.id, "ours = 2")

    writtenOutside()
    advanceUntilIdle()

    assertEquals(listOf("ours = 2"), viewModel.uiState.value.cells.map { it.source })
    assertTrue(viewModel.uiState.value.dirty)
    viewModel.saveNow {}
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.saveConflict)
  }

  /** The editor's own save reaches the watcher like anyone's, and must not rebuild its cells. */
  @Test
  fun theEditorsOwnSaveIsNotFollowed() = runTest {
    val viewModel = loadedEditor(repository = repository, watcher = watcher)
    viewModel.editCell(codeCell.id, "ours = 2")
    advanceUntilIdle()
    val afterSave = viewModel.uiState.value.sourceRevisions

    watcher.announceWrite("nb-1", repository.readStampedNotebook("nb-1").stamp)
    advanceUntilIdle()

    assertEquals(listOf("ours = 2"), viewModel.uiState.value.cells.map { it.source })
    assertEquals(afterSave, viewModel.uiState.value.sourceRevisions)
  }
}
