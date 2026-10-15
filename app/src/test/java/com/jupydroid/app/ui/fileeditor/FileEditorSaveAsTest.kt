package com.jupydroid.app.ui.fileeditor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.EditorSettings
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The file editor's half of Save As: what is on screen goes to another path, and this editor stays
 * what it was - `_finishSaveAs` writes the model to the new path and nothing else
 * (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`).
 */
class FileEditorSaveAsTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun aCopyHoldsTheUnsavedEditAndTheOriginalKeepsWhatWasSaved() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.txt" to "before"))
    val viewModel = editorFor(workspace)

    viewModel.edit("after")
    viewModel.writeCopyTo("notes-copy.txt")
    advanceUntilIdle()

    assertEquals("after", workspace.readText("notes-copy.txt"))
    assertEquals("before", workspace.readText("notes.txt"))
  }

  /** The copy is not this document's save: it still owes its own file the edit, and still lives where it did. */
  @Test
  fun theEditorStillOwesItsOwnFileTheEdit() = runTest {
    val viewModel = editorFor(FakeWorkspaceRepository(textByPath = mapOf("notes.txt" to "before")))

    viewModel.edit("after")
    viewModel.writeCopyTo("notes-copy.txt")

    assertTrue(viewModel.dirty.value)
    assertEquals("notes.txt", viewModel.path)
  }

  /** Asking before a taken path is replaced is the caller's; once asked, the copy replaces it. */
  @Test
  fun aCopyReplacesWhatWasAtItsPath() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.txt" to "before", "other.txt" to "theirs"))
    val viewModel = editorFor(workspace)

    viewModel.edit("after")
    viewModel.writeCopyTo("other.txt")

    assertEquals("after", workspace.readText("other.txt"))
  }

  private fun TestScope.editorFor(workspace: FakeWorkspaceRepository) = FileEditorViewModel(
    "notes.txt",
    workspace,
    this,
    editorSettings = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false)),
  ).also { advanceUntilIdle() }
}
