@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.fileeditor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
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
 * The file editor under `autosave: false` - the same semantics the notebook pins in
 * [com.jupydroid.app.ui.editor.EditorAutosaveTest], on the document kind that had no `dirty` flag
 * until this step. JupyterLab's file editor is the same `Context`/`SaveHandler` pair the notebook
 * uses (`packages/fileeditor`), so the gate, the dirty flag and the close verbs are one mechanism.
 */
class FileEditorAutosaveTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /** The flag the close gate reads: an edit dirties, the save clears - not the edit. */
  @Test
  fun anEditDirtiesAndTheWriteBackCleans() = runTest {
    val viewModel = editorFor()

    viewModel.edit("after")
    assertTrue(viewModel.uiState.value.dirty)
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.dirty)
  }

  @Test
  fun autosaveOff_anEditIsHeldDirtyRatherThanWritten() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.txt" to "before"))
    val viewModel = editorFor(workspace = workspace, editorSettings = autosaveOff())

    viewModel.edit("after")
    advanceUntilIdle()

    assertEquals("before", workspace.readText("notes.txt"))
    assertTrue(viewModel.uiState.value.dirty)
  }

  /**
   * Leaving still flushes: there is no dialog on the sweep path, so owed work is written on the
   * way out rather than lost. The dialog's Discard is what opts out - the case below.
   */
  @Test
  fun autosaveOff_leavingFlushesTheOwedWrite() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.txt" to "before"))
    val viewModel = editorFor(workspace = workspace, editorSettings = autosaveOff())

    viewModel.edit("after")
    viewModel.onCleared()
    advanceUntilIdle()

    assertEquals("after", workspace.readText("notes.txt"))
  }

  /** The close dialog's Discard: owed work is dropped, and the flush must not put it back. */
  @Test
  fun discardChangesLeavesTheFileUntouched() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.txt" to "before"))
    val viewModel = editorFor(workspace = workspace, editorSettings = autosaveOff())

    viewModel.edit("after")
    viewModel.discardChanges()
    viewModel.onCleared()
    advanceUntilIdle()

    assertEquals("before", workspace.readText("notes.txt"))
  }

  /** The close dialog's Save: write first, then let the screen go. */
  @Test
  fun saveNowWritesThenCallsBack() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.txt" to "before"))
    val viewModel = editorFor(workspace = workspace, editorSettings = autosaveOff())

    viewModel.edit("after")
    var saved = false
    viewModel.saveNow { saved = true }
    advanceUntilIdle()

    assertTrue(saved)
    assertEquals("after", workspace.readText("notes.txt"))
  }

  /**
   * The field reports its text once more as it leaves the screen - a tab going beneath another -
   * and that text is what was just saved. It changes nothing, so it must not mark the file dirty
   * and undo the save the strip's icon reported.
   */
  @Test
  fun anEditThatChangesNothingLeavesASavedFileClean() = runTest {
    val viewModel = editorFor(editorSettings = autosaveOff())
    viewModel.edit("after")
    viewModel.saveNow {}
    advanceUntilIdle()

    viewModel.edit("after")

    assertFalse(viewModel.uiState.value.dirty)
  }

  private fun TestScope.editorFor(
    workspace: FakeWorkspaceRepository = FakeWorkspaceRepository(textByPath = mapOf("notes.txt" to "before")),
    editorSettings: FakeEditorSettingsRepository = FakeEditorSettingsRepository(),
  ) = FileEditorViewModel("notes.txt", workspace, this, editorSettings = editorSettings).also {
    advanceUntilIdle()
  }

  private fun autosaveOff() = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false))
}
