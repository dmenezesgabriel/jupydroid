package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * What has to happen when the notebook screen goes away.
 *
 * Until the editor's ViewModel was scoped to its back-stack entry it was never cleared at all, so
 * none of this ran and none of it was missed. Scoping it makes `onCleared` real, and two things
 * break the moment it is: a save still inside its 500 ms debounce dies with `viewModelScope`, and
 * the kernel socket is never closed.
 *
 * JupyterLab does both, in this order - `DocumentWidgetManager.onClose` awaits `context.save()`
 * before `widget.dispose()`, and disposing the context disposes the session
 * (`packages/docmanager/src/widgetmanager.ts:335-388`, `packages/docregistry/src/context.ts:205-220`).
 */
class EditorTeardownTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  /**
   * The typed character must reach disk even though the debounce never got its chance.
   *
   * **`runCurrent`, not `advanceUntilIdle`, and that is the whole test.** Written with
   * `advanceUntilIdle` it passed against an `onCleared` that did nothing at all - virtual time
   * ran past the 500 ms debounce and the ordinary scheduled save did the writing. `runCurrent`
   * advances no time, so only work queued *now* runs, and the write it asserts is the flush or
   * it is nothing. Verified by mutation: emptying `onCleared` fails this.
   */
  @Test
  fun leavingWithAPendingEditStillWritesIt() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository, applicationScope = this)
    viewModel.editCell("c1", "typed but not settled")

    viewModel.onCleared()
    runCurrent()

    assertEquals("typed but not settled", repository.readNotebook("nb-1").cells.single().source)
  }

  /**
   * Opening a notebook and leaving it is not an edit.
   *
   * A write is what makes a notebook "updated" here - `updatedAt` is the file's mtime, the way
   * Jupyter's `last_modified` is - so writing on the way out would push a notebook to the top of
   * Recents for having been looked at. JupyterLab's autosave is gated the same way, on
   * `context.model.dirty` (`packages/docmanager/src/savehandler.ts:117-121`).
   *
   * This one is a guard rather than a proof: it is green against an `onCleared` that does
   * nothing. What it catches is the plausible wrong fix - flushing unconditionally.
   */
  @Test
  fun leavingAnUntouchedNotebookWritesNothing() = runTest {
    val repository = RecordingNotebookRepository()
    val viewModel = loadedEditor(repository = repository, applicationScope = this)
    val writesBefore = repository.saveCount

    viewModel.onCleared()
    advanceUntilIdle()

    assertEquals(writesBefore, repository.saveCount)
  }

  @Test
  fun leavingDisconnectsTheKernel() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.onCleared()
    advanceUntilIdle()

    assertEquals(1, gateway.disconnected.size)
  }

  /** A notebook nobody ran never opened a socket, so there is nothing to close. */
  @Test
  fun leavingANotebookThatNeverRanDisconnectsNothing() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)

    viewModel.onCleared()
    advanceUntilIdle()

    assertTrue("nothing connected, so nothing to disconnect", gateway.disconnected.isEmpty())
  }
}
