package com.jupydroid.app.ui.fileeditor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.model.TextEdit
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A text file rewritten under its editor: the save asks instead of writing over it.
 *
 * The workspace is the shell's and the kernel's too, so `echo changed > notes.txt` under an open
 * `notes.txt` is an ordinary event. `Context._maybeSave` raises **File Changed** for it
 * (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`), with Overwrite, Revert and Cancel.
 * "Someone else" here is a write with no stamp, which is what theirs is to the repository.
 */
class FileEditorSaveConflictTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val workspace = FakeWorkspaceRepository(textByPath = mapOf(NOTES to "opened"))

  @Test
  fun aSaveOverAFileWrittenSinceItWasOpenedAsksAndWritesNothing() = runTest {
    val viewModel = refusedEditor()

    assertTrue(viewModel.uiState.value.saveConflict)
    assertTrue("a refused save is still owed", viewModel.uiState.value.dirty)
    assertEquals(THEIRS, workspace.readText(NOTES))
  }

  /** A conflict is a question, so it is not also reported as a failure. */
  @Test
  fun aRefusedSaveIsNotReportedAsAnError() = runTest {
    val viewModel = refusedEditor()

    assertEquals(null, viewModel.uiState.value.error)
  }

  @Test
  fun overwriteWritesTheEditorsTextOverTheirs() = runTest {
    val viewModel = refusedEditor()

    viewModel.conflict.overwrite()
    advanceUntilIdle()

    assertEquals(OURS, workspace.readText(NOTES))
    assertFalse(viewModel.uiState.value.saveConflict)
    assertFalse(viewModel.uiState.value.dirty)
  }

  /** The revision is what tells the field its text was rewritten by something other than typing. */
  @Test
  fun revertShowsTheirsAndOwesNothing() = runTest {
    val viewModel = refusedEditor()
    val before = viewModel.uiState.value.revision

    viewModel.conflict.revert()
    advanceUntilIdle()

    assertEquals(THEIRS, viewModel.uiState.value.text)
    assertEquals(before + 1, viewModel.uiState.value.revision)
    assertFalse(viewModel.uiState.value.saveConflict)
    assertFalse(viewModel.uiState.value.dirty)
    assertEquals(THEIRS, workspace.readText(NOTES))
  }

  /** The re-read's stamp is adopted, or every save after a revert would ask again. */
  @Test
  fun anEditAfterARevertIsSavedWithoutAsking() = runTest {
    val viewModel = refusedEditor()
    viewModel.conflict.revert()
    advanceUntilIdle()

    viewModel.edit("theirs, and then mine")
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.saveConflict)
    assertEquals("theirs, and then mine", workspace.readText(NOTES))
  }

  @Test
  fun cancelKeepsBothAndTheNextSaveAsksAgain() = runTest {
    val viewModel = refusedEditor()

    viewModel.conflict.cancel()

    assertFalse(viewModel.uiState.value.saveConflict)
    assertTrue(viewModel.uiState.value.dirty)
    assertEquals(OURS, viewModel.uiState.value.text)
    assertEquals(THEIRS, workspace.readText(NOTES))

    viewModel.edit("ours, typed on")
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.saveConflict)
    assertEquals(THEIRS, workspace.readText(NOTES))
  }

  /** Each save's stamp is the next one's, so the editor's own writes never look like someone else's. */
  @Test
  fun editsWithNobodyElseWritingNeverAsk() = runTest {
    val viewModel = openedEditor()

    viewModel.edit("first")
    advanceUntilIdle()
    viewModel.edit("second, longer")
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.saveConflict)
    assertEquals("second, longer", workspace.readText(NOTES))
  }

  /** The close gate saves first, and a refused save keeps the document open with the question over it. */
  @Test
  fun aRefusedSaveOnTheWayOutDoesNotClose() = runTest {
    val viewModel = openedEditor()
    workspace.writeText(NOTES, THEIRS)
    viewModel.edit(OURS)
    var closed = false

    viewModel.saveNow { closed = true }
    advanceUntilIdle()

    assertFalse("a refused save must keep the document open", closed)
    assertTrue(viewModel.uiState.value.saveConflict)
  }

  /** A workspace replace reaches an open file through its editor, so its save is refused like a typed one. */
  @Test
  fun aReplaceAppliedOverAChangedFileAsksToo() = runTest {
    val viewModel = openedEditor()
    workspace.writeText(NOTES, THEIRS)

    viewModel.applyEdits(cell = null, edits = listOf(TextEdit(0..5, "replaced")))
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.saveConflict)
    assertEquals(THEIRS, workspace.readText(NOTES))
  }

  private suspend fun TestScope.openedEditor(): FileEditorViewModel {
    val viewModel = FileEditorViewModel(NOTES, workspace, this)
    advanceUntilIdle()
    return viewModel
  }

  /** Opened, written under, then typed in: the debounced save has just been refused. */
  private suspend fun TestScope.refusedEditor(): FileEditorViewModel {
    val viewModel = openedEditor()
    workspace.writeText(NOTES, THEIRS)
    viewModel.edit(OURS)
    advanceUntilIdle()
    return viewModel
  }

  private companion object {
    const val NOTES = "notes.txt"
    const val THEIRS = "changed by someone else"
    const val OURS = "typed here"
  }
}
