package com.jupydroid.app.ui.fileeditor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.syntax.JsonSyntax
import com.jupydroid.app.syntax.PythonSyntax
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A file that is not a notebook, in one editor.
 *
 * JupyterLab opens anything its notebook factory does not claim in the *file editor* - one
 * CodeMirror over the whole document, with the language chosen from the file type
 * (`packages/fileeditor`). Ours is the cell editor's own field over the whole file, so line
 * numbers, highlighting, bracket matching and the symbol row are the same code and not a second
 * implementation of them.
 */
class FileEditorViewModelTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun itLoadsTheFileItWasGiven() = runTest {
    val viewModel = editorFor("notes.txt" to "hello\nthere")
    advanceUntilIdle()

    assertEquals("hello\nthere", viewModel.uiState.value.text)
    assertTrue(viewModel.uiState.value.loaded)
  }

  /** The name, not the path - `PathExt.basename`, the same thing the notebook editor shows. */
  @Test
  fun itIsTitledByTheFilesName() = runTest {
    val viewModel = editorFor("reports/data.csv" to "a,b")
    advanceUntilIdle()

    assertEquals("data.csv", viewModel.uiState.value.name)
  }

  @Test
  fun theHighlighterFollowsTheFileType() = runTest {
    val python = editorFor("train.py" to "x = 1")
    val json = editorFor("config.json" to "{}")
    advanceUntilIdle()

    assertTrue(python.uiState.value.syntax is PythonSyntax)
    assertTrue(json.uiState.value.syntax is JsonSyntax)
  }

  /**
   * An edit reaches the disk, debounced.
   *
   * Same rule the notebook editor follows and for the same reason: a write per keystroke rewrites
   * the file on every character. `NotebookSaveDebounce` is shared rather than reimplemented.
   */
  @Test
  fun anEditIsWrittenBack() = runTest {
    val workspace = FakeWorkspaceRepository(textByPath = mapOf("notes.txt" to "before"))
    val viewModel = FileEditorViewModel("notes.txt", workspace, this)
    advanceUntilIdle()

    viewModel.edit("after")
    advanceUntilIdle()

    assertEquals("after", workspace.readText("notes.txt"))
  }

  @Test
  fun aFileThatCannotBeReadIsReportedRatherThanCrashing() = runTest {
    val viewModel = editorFor("other.txt" to "x", open = "missing.txt")
    advanceUntilIdle()

    assertTrue("expected the failure to be reported", viewModel.uiState.value.error != null)
    assertEquals(false, viewModel.uiState.value.loaded)
  }

  private fun kotlinx.coroutines.test.TestScope.editorFor(
    file: Pair<String, String>,
    open: String = file.first,
  ) = FileEditorViewModel(open, FakeWorkspaceRepository(textByPath = mapOf(file)), this)
}
