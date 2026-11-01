@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.fileeditor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 * `docmanager:reload` on a text file: the file is read back into the open editor.
 *
 * `context.revert()` (jupyterlab@7f0e690 `packages/docregistry/src/context.ts`) re-reads the file
 * and leaves the model clean. "Someone else" here is a write with no stamp, as a cell's or a
 * shell's is to the repository.
 */
class FileEditorReloadTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val workspace = FakeWorkspaceRepository(textByPath = mapOf(NOTES to "opened"))

  /** The revision is what tells the field its text was rewritten by something other than typing. */
  @Test
  fun aCleanFileReloadsWhatWasWrittenUnderIt() = runTest {
    val viewModel = openedEditor()
    val before = viewModel.uiState.value.revision
    workspace.writeText(NOTES, THEIRS)

    viewModel.reloadFromDisk()
    advanceUntilIdle()

    assertEquals(THEIRS, viewModel.uiState.value.text)
    assertEquals(before + 1, viewModel.uiState.value.revision)
    assertFalse(viewModel.uiState.value.dirty)
  }

  /** The reload took the disk's stamp, so the next save is an ordinary one. */
  @Test
  fun anEditAfterAReloadIsSavedWithoutAsking() = runTest {
    val viewModel = openedEditor()
    workspace.writeText(NOTES, THEIRS)
    viewModel.reloadFromDisk()
    advanceUntilIdle()

    viewModel.edit("theirs, and then mine")
    advanceUntilIdle()

    assertFalse(viewModel.uiState.value.saveConflict)
    assertEquals("theirs, and then mine", workspace.readText(NOTES))
  }

  /**
   * The edit a reload drops was owed a debounced write, and that write must not follow the reload:
   * it would rewrite the bytes just read under a new modification time, which every other holder
   * of the file then reads as a change (ADR-0090).
   */
  @Test
  fun aReloadDropsTheWriteTheEditOwed() = runTest {
    val viewModel = openedEditor()
    viewModel.edit(OURS)
    workspace.writeText(NOTES, THEIRS)
    val theirStamp = workspace.readStampedText(NOTES).stamp

    viewModel.reloadFromDisk()
    advanceUntilIdle()

    assertEquals(THEIRS, viewModel.uiState.value.text)
    assertFalse(viewModel.uiState.value.dirty)
    assertFalse(viewModel.uiState.value.saveConflict)
    assertEquals("nothing may be written after a reload", theirStamp, workspace.readStampedText(NOTES).stamp)
  }

  private suspend fun TestScope.openedEditor(): FileEditorViewModel {
    val viewModel = FileEditorViewModel(NOTES, workspace, this)
    advanceUntilIdle()
    return viewModel
  }

  private companion object {
    const val NOTES = "notes.txt"
    const val THEIRS = "changed by someone else"
    const val OURS = "typed here"
  }
}
