package com.jupydroid.app.ui.editor

import com.jupydroid.app.MainDispatcherRule
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A restart and what the reference composes on top of it - `notebook:restart-run-all`,
 * `notebook:restart-clear-output` and `notebook:restart-and-run-to-selected`.
 *
 * Beside [NotebookKernelShutdownTest] and on the same split: those are the ways a notebook lets a
 * kernel go, these are the ways it replaces one. All three of the compound commands are
 * `await sessionDialogs.restart(…)` followed by something else
 * (`packages/notebook-extension/src/index.ts:3245-3345`), which is why they are one method here
 * taking a continuation rather than three.
 */
class NotebookKernelRestartTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  @Test
  fun restartingReplacesTheKernel() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.kernel.restart()
    advanceUntilIdle()

    assertEquals(1, gateway.restarted.size)
  }

  /**
   * **The continuation runs after the restart, not beside it**, which is the whole shape of these
   * commands: `if (restarted) { … }` after an awaited dialog (`index.ts:3249-3254`). A clear that
   * landed first would be undone by nothing, but a *run* that landed first would execute against
   * the interpreter the restart is about to destroy - and its results would be a lie the user
   * cannot see.
   */
  @Test
  fun theContinuationRunsAfterTheKernelHasBeenReplaced() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, cells = listOf(codeCell), applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    gateway.executed.clear()

    val order = mutableListOf<String>()
    viewModel.kernel.restart(then = { order += "then" })
    advanceUntilIdle()

    assertEquals(listOf("then"), order)
    assertEquals(1, gateway.restarted.size)
  }

  /**
   * **And it runs even when there was no kernel**, which is the same rule [NotebookKernel.shutdown]
   * carries and for the same reason: with nothing to replace there is nothing to wait for, and the
   * continuation is not conditional on there having been one. A `restart-and-run-to-selected` on a
   * notebook nobody has run is simply a run - a fresh kernel is what the restart would have given
   * it anyway.
   */
  @Test
  fun theContinuationRunsEvenWithNoKernelToReplace() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, applicationScope = this)

    var ran = false
    viewModel.kernel.restart(then = { ran = true })
    advanceUntilIdle()

    assertTrue("the continuation is not conditional on there having been a kernel", ran)
    assertTrue("nothing was running, so nothing to replace", gateway.restarted.isEmpty())
  }

  /**
   * `notebook:restart-clear-output` is `restart` then `clearAllOutputs` (`index.ts:3245-3263`), and
   * the second half is a document operation - so it is undoable exactly as the menu row is, while
   * the restart itself is not. Nothing puts an interpreter back.
   */
  @Test
  fun restartThenClearLeavesTheClearOnTheUndoStack() = runTest {
    val gateway = RecordingKernelGateway()
    val viewModel = loadedEditor(gateway = gateway, cells = listOf(codeCell), applicationScope = this)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.kernel.restart(then = { viewModel.edits.clearAllOutputs() })
    advanceUntilIdle()

    assertTrue(viewModel.uiState.value.outputs.isEmpty())
    assertEquals(1, viewModel.uiState.value.undoable.size)
  }

  /**
   * `notebook:restart-and-run-to-selected` slices `(0, activeCellIndex + 1)` and hands it to the
   * *public* `NotebookActions.runCells` (`index.ts:3273-3283`) - so the head does not move and the
   * selection is not dropped, unlike the four `run*` commands. Ours composes the same two pieces.
   */
  @Test
  fun restartAndRunToSelectedRunsTheHeadAndEverythingAboveIt() = runTest {
    val gateway = RecordingKernelGateway()
    val cells = listOf(codeCell, secondCodeCell, Cell3)
    val viewModel = loadedEditor(gateway = gateway, cells = cells, applicationScope = this)
    viewModel.cellFocusChanged(secondCodeCell.id, focused = true)
    advanceUntilIdle()
    gateway.executed.clear()

    viewModel.kernel.restartAndRunTo()
    advanceUntilIdle()

    assertEquals(listOf(codeCell.id, secondCodeCell.id), gateway.executed)
    assertEquals("the head stays where it was", secondCodeCell.id, viewModel.uiState.value.selectedCellId)
  }

  private companion object {
    val Cell3 = com.jupydroid.app.domain.model.Cell(
      id = "c4",
      kind = com.jupydroid.app.domain.model.CellKind.CODE,
      source = "print(3)",
    )
  }
}
