package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Renaming a notebook from the notebook itself.
 *
 * Renaming while the document is open is exactly what JupyterLab allows - no rename path checks
 * the dirty flag, and the context simply retargets, so the next save writes the pending content
 * under the new name (`packages/docregistry/src/context.ts:266-277`, `441-487`). The trap here
 * is the mirror of that: this editor holds its own copy of the notebook and writes it back on a
 * debounce, so a rename that only reaches the repository gets overwritten by the next keystroke.
 */
class EditorRenameTest {
  // Read back under the *new* id throughout: X1b made the name the file, so renaming moves it and
  // "nb-1" stops naming anything. That is the contract, not an accident of these tests.
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun renameWritesThroughTheRepository() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository)

    viewModel.renameNotebook("Math notes")
    advanceUntilIdle()

    assertEquals("Math notes", repository.readNotebook("Math notes").title)
  }

  @Test
  fun renameShowsInTheTitleBarImmediately() = runTest {
    val viewModel = loadedEditor()

    viewModel.renameNotebook("Math notes")

    assertEquals("Math notes", viewModel.uiState.value.title)
  }

  /**
   * The in-memory copy has to move with the rename, or the next debounced save writes the old
   * title straight back over it. This is the test that forces it; without the `notebook = …`
   * line the rename survives exactly until the next keystroke.
   */
  @Test
  fun renamingSurvivesTheNextCellEdit() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository)

    viewModel.renameNotebook("Math notes")
    viewModel.editCell("c1", "print(2)")
    advanceUntilIdle()

    val saved = repository.readNotebook("Math notes")
    assertEquals("Math notes", saved.title)
    assertEquals("print(2)", saved.cells.single().source)
  }
}
