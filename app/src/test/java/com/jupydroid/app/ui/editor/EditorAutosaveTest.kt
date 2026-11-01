@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.domain.model.EditorSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The notebook editor under `autosave: false`, and the close-time verbs the setting makes real.
 *
 * JupyterLab with autosave off starts no `SaveHandler`: the document goes dirty and stays dirty
 * until something else writes it. What "something else" is here: the close path flushes owed work
 * (an open beside it sweeping this ViewModel away must never lose edits), and the close dialog's
 * Save and Discard answers drive [EditorViewModel.saveNow] and [EditorViewModel.discardChanges].
 */
class EditorAutosaveTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun autosaveOff_anEditIsKeptDirtyAndUnwritten() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository, editorSettings = autosaveOff())
    val written = repository.saveCount

    viewModel.editCell("c1", "print(99)")
    advanceUntilIdle()

    assertEquals("autosave off must not write", written, repository.saveCount)
    assertTrue(viewModel.uiState.value.dirty)
  }

  /**
   * Running a cell changes the document - outputs and execution counts are part of the file - so
   * under autosave off it is held dirty like any edit, not written through the user's choice.
   * The run's own completion is the `SaveHandler` question: nothing polls, so nothing writes.
   */
  @Test
  fun autosaveOff_aFinishedRunMarksDirtyRatherThanWriting() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository, editorSettings = autosaveOff())
    val written = repository.saveCount

    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    assertEquals(written, repository.saveCount)
    assertTrue(viewModel.uiState.value.dirty)
  }

  /**
   * Closing with unsaved work still writes: the sweep path (a notebook opening beside this one)
   * has no dialog to answer, so losing the edits is not on the table. The dialog's Discard is
   * what skips this flush - see the case below.
   */
  @Test
  fun closingADirtyNotebookFlushesTheOwedWrite() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository, editorSettings = autosaveOff())

    viewModel.editCell("c1", "print(99)")
    advanceUntilIdle()
    val written = repository.saveCount
    viewModel.onCleared()
    advanceUntilIdle()

    assertEquals(written + 1, repository.saveCount)
  }

  /** The close dialog's Discard: the edits are the user's to throw away, so nothing writes. */
  @Test
  fun discardChangesClosesWithoutWriting() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository, editorSettings = autosaveOff())

    viewModel.editCell("c1", "print(99)")
    advanceUntilIdle()
    val written = repository.saveCount
    viewModel.discardChanges()
    viewModel.onCleared()
    advanceUntilIdle()

    assertEquals(written, repository.saveCount)
  }

  /** The close dialog's Save: the owed write is made first, and only then is the screen let go. */
  @Test
  fun saveNowWritesThenCallsBack() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository, editorSettings = autosaveOff())

    viewModel.editCell("c1", "print(99)")
    var saved = false
    viewModel.saveNow { saved = true }
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.dirty)
    assertTrue(saved)
  }

  /**
   * A save that cannot be made does not close the document: `saveNow`'s callback is the screen
   * leaving, and a failed write must not be followed out - the reference's `saveBeforeClose`
   * keeps the widget open on a failed save the same way (`docmanager/src/manager.ts`).
   */
  /** The file editor's case, on a cell: a re-report of the saved source is no edit. */
  @Test
  fun anEditThatChangesNothingLeavesASavedNotebookClean() = runTest {
    val viewModel = loadedEditor(editorSettings = autosaveOff())
    viewModel.editCell("c1", "print(99)")
    viewModel.saveNow {}
    advanceUntilIdle()

    viewModel.editCell("c1", "print(99)")

    assertFalse(viewModel.uiState.value.dirty)
  }

  @Test
  fun saveNowOnAFailedWriteDoesNotCallBack() = runTest {
    val viewModel = editorWhoseWorkspaceWentAway(editorSettings = autosaveOff())

    viewModel.editCell("c1", "print(99)")
    var saved = false
    viewModel.saveNow { saved = true }
    advanceUntilIdle()

    assertFalse(saved)
    assertTrue(viewModel.uiState.value.dirty)
  }

  /** A document that owed nothing has nothing to discard - closing it is just closing it. */
  @Test
  fun closingACleanNotebookWritesNothing() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository, editorSettings = autosaveOff())
    val written = repository.saveCount

    viewModel.onCleared()
    advanceUntilIdle()

    assertEquals(written, repository.saveCount)
  }

  private fun autosaveOff() = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false))
}
