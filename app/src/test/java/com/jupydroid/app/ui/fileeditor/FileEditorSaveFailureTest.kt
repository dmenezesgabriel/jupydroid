package com.jupydroid.app.ui.fileeditor

import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.data.fake.FakeWorkspaceRepository
import com.jupydroid.app.domain.port.WorkspaceRepository
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.io.FileNotFoundException

/**
 * **A save that cannot be made is reported, not fatal.**
 *
 * The workspace is shared with the kernel, and `WorkspaceRepository.changes` announces only what
 * *this app* moved or removed - it is not a filesystem watcher. So a cell that runs
 * `shutil.rmtree('reports')` takes the file out from under an open editor and nothing is announced:
 * the path stands, the debounce fires, and `writeText` raises `ENOENT` inside `viewModelScope`,
 * where an uncaught exception takes the process down.
 *
 * This file already argues the case for the other direction - *"a shared workspace can lose a file
 * under the user - a cell can `os.remove` it - so a read that fails is an ordinary event to report,
 * not a corrupt state to crash on"* - and the write had no such guard. The reference reports it the
 * same way: `Context.save()`'s failure path is `showErrorMessage`, and the document **stays dirty**
 * (`packages/docregistry/src/context.ts`).
 */
class FileEditorSaveFailureTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /** A tree that lost the directory under it - the exact failure `DiskWorkspaceRepository` raises. */
  private class VanishedWorkspaceRepository(
    private val stillReadable: WorkspaceRepository =
      FakeWorkspaceRepository(textByPath = mapOf("reports/notes.txt" to "cat")),
  ) : WorkspaceRepository by stillReadable {
    override suspend fun writeText(path: String, text: String): Unit =
      throw FileNotFoundException("$path: open failed: ENOENT (No such file or directory)")
  }

  @Test
  fun aSaveIntoAWorkspaceThatMovedIsReportedRatherThanFatal() = runTest {
    val viewModel = FileEditorViewModel("reports/notes.txt", VanishedWorkspaceRepository(), this)
    advanceUntilIdle()

    viewModel.edit("dog")
    advanceUntilIdle()

    val reported = viewModel.uiState.value.error
    assertTrue("expected the failed save to be reported, got $reported", reported?.contains("ENOENT") == true)
  }

  /** And what was typed is still there to try again with - a failed save loses no work. */
  @Test
  fun aFailedSaveKeepsTheTextItCouldNotWrite() = runTest {
    val viewModel = FileEditorViewModel("reports/notes.txt", VanishedWorkspaceRepository(), this)
    advanceUntilIdle()

    viewModel.edit("dog")
    advanceUntilIdle()

    assertEquals("dog", viewModel.uiState.value.text)
  }
}
