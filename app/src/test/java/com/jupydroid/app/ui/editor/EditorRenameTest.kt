package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.domain.model.WorkspaceChange
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

  /**
   * A notebook in a folder is titled by its name alone.
   *
   * `DocumentWidget` labels itself `PathExt.basename(sender.localPath)`
   * (`packages/docregistry/src/default.ts:618-621`) - the path is what the document *is*, the
   * basename is what it is *called*. Showing "reports/Draft" would also prefill the rename dialog
   * with a string containing a `/`, which `isValidFileName` refuses, so the user would have to
   * delete the folder out of their own notebook's name before they could rename it.
   */
  @Test
  fun aNotebookInAFolderIsTitledByItsNameAlone() = runTest {
    val viewModel = loadedEditor(notebookId = "reports/Draft", title = "reports/Draft")

    assertEquals("Draft", viewModel.uiState.value.title)
  }

  @Test
  fun renamingANotebookInAFolderKeepsItThereAndShowsTheNewName() = runTest {
    val viewModel = loadedEditor(notebookId = "reports/Draft", title = "reports/Draft")

    viewModel.renameNotebook("Final")
    advanceUntilIdle()

    assertEquals("Final", viewModel.uiState.value.title)
    assertEquals("reports/Final", viewModel.uiState.value.notebookId)
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

  /**
   * A rename made from the file browser moves the open notebook, and its edits go to the new file.
   *
   * `Context._onFileChanged` -> `_updatePath`: the open document subscribes to the contents layer
   * and retargets itself, because the rename can come from anywhere - the browser beside it on a
   * tablet, or another client. Without this the next debounced save writes the *old* path, which
   * re-creates the notebook under the name the user just renamed away from: two files, and the
   * edits in the one they are not looking at.
   */
  @Test
  fun aRenameMadeInTheBrowserMovesTheOpenNotebook() = runTest {
    val workspace = AnnouncingWorkspaceRepository()
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository, workspace = workspace)

    workspace.announce(from = "nb-1", to = "Renamed elsewhere")
    viewModel.editCell("c1", "print(2)")
    advanceUntilIdle()

    assertEquals("Renamed elsewhere", viewModel.uiState.value.notebookId)
    assertEquals("print(2)", repository.readNotebook("Renamed elsewhere").cells.single().source)
  }

  /** A folder rename moves what is open inside it - the case the prefix rule exists for. */
  @Test
  fun aRenamedFolderMovesTheNotebookInsideIt() = runTest {
    val workspace = AnnouncingWorkspaceRepository()
    val viewModel = loadedEditor(notebookId = "reports/Draft", title = "Draft", workspace = workspace)

    workspace.announce(from = "reports", to = "archive")
    advanceUntilIdle()

    assertEquals("archive/Draft", viewModel.uiState.value.notebookId)
  }

  /**
   * A notebook deleted from the browser is not written back by the editor still showing it.
   *
   * Our teardown flushes the last edit and our debounce fires 500ms after any keystroke; the
   * reference's `SaveHandler` is *disposed* when the document goes and writes nothing again
   * (`savehandler.ts:29,62-69`). Without that, deleting a notebook from the pane beside it and
   * touching a cell puts the file straight back.
   */
  @Test
  fun aNotebookDeletedElsewhereIsNotWrittenBack() = runTest {
    val workspace = AnnouncingWorkspaceRepository()
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository, workspace = workspace)
    val savesBefore = repository.saveCount

    workspace.announce(WorkspaceChange.Deleted("nb-1"))
    viewModel.editCell("c1", "typed after the delete")
    advanceUntilIdle()
    viewModel.onCleared()
    advanceUntilIdle()

    assertEquals("nothing may be written after the file is gone", savesBefore, repository.saveCount)
  }

  /**
   * And the editor says so, so the screen showing it can close.
   *
   * `DocumentManager.deleteFile` closes every widget open on the path (`manager.ts:351-363`).
   * Ours arrives at the same place from the other end - the document hears that its file went and
   * reports it - which also covers the delete the reference's path never sees: a cell calling
   * `os.remove` on the notebook it is running in.
   */
  @Test
  fun aDeletedNotebookReportsThatItIsGone() = runTest {
    val workspace = AnnouncingWorkspaceRepository()
    val viewModel = loadedEditor(workspace = workspace)

    workspace.announce(WorkspaceChange.Deleted("nb-1"))
    advanceUntilIdle()

    assertEquals(true, viewModel.uiState.value.gone)
  }

  /** A rename of something else must leave this notebook exactly where it is. */
  @Test
  fun aRenameOfAnotherFileLeavesThisOneAlone() = runTest {
    val workspace = AnnouncingWorkspaceRepository()
    val viewModel = loadedEditor(workspace = workspace)

    workspace.announce(from = "nb-2", to = "Something else")
    advanceUntilIdle()

    assertEquals("nb-1", viewModel.uiState.value.notebookId)
  }
}
