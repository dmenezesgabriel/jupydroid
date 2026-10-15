package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeEditorSettingsRepository
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.domain.model.EditorSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * The notebook's half of Save As: the cells on screen go to another path as a notebook of their
 * own, and this editor stays on its file - `_finishSaveAs` writes the model to the new path and
 * moves nothing (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`).
 */
class EditorSaveAsTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun aCopyHoldsTheUnsavedEditAndTheOriginalKeepsWhatWasSaved() = runTest {
    val repository = FakeNotebookRepository()
    val viewModel = loadedEditor(repository = repository, notebookId = "a.ipynb", editorSettings = autosaveOff())

    viewModel.editCell("c1", "print(99)")
    viewModel.document.writeCopyTo("b.ipynb")

    val copy = repository.readNotebook("b.ipynb")
    assertEquals("print(99)", copy.cells.single().source)
    assertEquals("b.ipynb", copy.title)
    assertEquals(codeCell.source, repository.readNotebook("a.ipynb").cells.single().source)
  }

  /** The copy is not this notebook's save: it still owes its own file the edit, and still lives where it did. */
  @Test
  fun theNotebookStillOwesItsOwnFileTheEdit() = runTest {
    val viewModel = loadedEditor(notebookId = "a.ipynb", editorSettings = autosaveOff())

    viewModel.editCell("c1", "print(99)")
    viewModel.document.writeCopyTo("b.ipynb")

    assertTrue(viewModel.document.dirty.value)
    assertEquals("a.ipynb", viewModel.document.path)
  }

  private fun autosaveOff() = FakeEditorSettingsRepository(EditorSettings(autosaveEnabled = false))
}
