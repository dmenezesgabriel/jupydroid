@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.fileeditor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.data.fake.FakeWorkspaceWatcher
import com.jupydroid.app.domain.model.EditorSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A text file open with no unsaved work shows what something else wrote to it, by itself.
 *
 * vscode@3f3380c `textFileEditorModelManager.ts` `onDidFilesChange`: a clean model is re-read on a
 * change, and "never reload dirty models". "Something else" is a write with no stamp, as a cell's or
 * a shell's is to the repository, announced as the watcher would see it.
 */
class FileEditorFollowTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val workspace = FakeWorkspaceRepository(textByPath = mapOf(NOTES to "opened"))
  private val watcher = FakeWorkspaceWatcher()

  @Test
  fun aCleanFileShowsWhatWasWrittenUnderIt() = runTest {
    val viewModel = openedEditor()
    val before = viewModel.uiState.value.revision

    writtenOutside(THEIRS)
    advanceUntilIdle()

    assertEquals(THEIRS, viewModel.uiState.value.text)
    assertEquals(before + 1, viewModel.uiState.value.revision)
    assertFalse(viewModel.uiState.value.dirty)
  }

  /** The edit stays, and the save that would have replaced their write still asks (ADR-0090). */
  @Test
  fun aDirtyFileKeepsItsEdit() = runTest {
    val viewModel = openedEditor(EditorSettings(autosaveEnabled = false))
    viewModel.edit(OURS)

    writtenOutside(THEIRS)
    advanceUntilIdle()

    assertEquals(OURS, viewModel.uiState.value.text)
    assertTrue(viewModel.uiState.value.dirty)
    viewModel.saveNow {}
    advanceUntilIdle()
    assertTrue(viewModel.uiState.value.saveConflict)
  }

  /**
   * The editor's own save reaches the watcher like anyone's - a rename over the file - and must
   * not re-read it: the field would be told its text was replaced, and the caret would jump.
   */
  @Test
  fun theEditorsOwnSaveIsNotFollowed() = runTest {
    val viewModel = openedEditor()
    viewModel.edit(OURS)
    advanceUntilIdle()
    val afterSave = viewModel.uiState.value.revision

    watcher.announceWrite(NOTES, workspace.readStampedText(NOTES).stamp)
    advanceUntilIdle()

    assertEquals(OURS, viewModel.uiState.value.text)
    assertEquals(afterSave, viewModel.uiState.value.revision)
  }

  private suspend fun writtenOutside(text: String) {
    watcher.announceWrite(NOTES, workspace.writeText(NOTES, text))
  }

  private suspend fun TestScope.openedEditor(settings: EditorSettings = EditorSettings()): FileEditorViewModel {
    val viewModel = FileEditorViewModel(
      NOTES,
      workspace,
      this,
      editorSettings = FakeEditorSettingsRepository(settings),
      workspaceWatcher = watcher,
    )
    advanceUntilIdle()
    return viewModel
  }

  private companion object {
    const val NOTES = "notes.txt"
    const val THEIRS = "changed by someone else"
    const val OURS = "typed here"
  }
}
