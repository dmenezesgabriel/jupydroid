package com.jupydroid.app.ui.fileeditor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.syntax.JsonSyntax
import com.jupydroid.app.syntax.PythonSyntax
import java.io.File
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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

  /**
   * A rename made somewhere else moves the open file, and the next write follows it.
   *
   * This is `Context._onFileChanged` -> `_updatePath`: the reference's open document subscribes to
   * the contents layer and retargets itself, because a rename can come from the file browser or
   * from another client entirely. Without it the editor keeps writing to a path that no longer
   * exists, which **re-creates** the file under its old name - one document, two files, and the
   * edits in the one nobody is looking at.
   */
  @Test
  fun aRenameMadeElsewhereMovesTheOpenFile() = runTest {
    val temp = TemporaryFolder().also { it.create() }
    val workspace = DiskWorkspaceRepository(temp.root, UnconfinedTestDispatcher(testScheduler))
    File(temp.root, "notes.txt").writeText("before")
    val viewModel = FileEditorViewModel("notes.txt", workspace, this)
    advanceUntilIdle()

    workspace.rename("notes.txt", "kept.txt")
    advanceUntilIdle()
    viewModel.edit("after")
    advanceUntilIdle()

    assertEquals("kept.txt", viewModel.uiState.value.path)
    assertEquals("kept.txt", viewModel.uiState.value.name)
    assertEquals("after", File(temp.root, "kept.txt").readText())
    assertEquals(listOf("kept.txt"), temp.root.list()?.toList())
  }

  /** A folder rename moves everything under it, which is the case the prefix rule exists for. */
  @Test
  fun aRenamedFolderMovesTheFileInsideIt() = runTest {
    val temp = TemporaryFolder().also { it.create() }
    val workspace = DiskWorkspaceRepository(temp.root, UnconfinedTestDispatcher(testScheduler))
    File(temp.root, "reports").mkdirs()
    File(temp.root, "reports/data.csv").writeText("a,b")
    val viewModel = FileEditorViewModel("reports/data.csv", workspace, this)
    advanceUntilIdle()

    workspace.rename("reports", "archive")
    advanceUntilIdle()

    assertEquals("archive/data.csv", viewModel.uiState.value.path)
  }

  private fun kotlinx.coroutines.test.TestScope.editorFor(
    file: Pair<String, String>,
    open: String = file.first,
  ) = FileEditorViewModel(open, FakeWorkspaceRepository(textByPath = mapOf(file)), this)
}
