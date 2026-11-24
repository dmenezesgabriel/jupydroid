@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.editor

import com.jupydroid.app.DocumentOpening
import com.jupydroid.app.MainDispatcherRule
import com.jupydroid.app.domain.model.ActiveCellRuns
import com.jupydroid.app.domain.model.BASE_SPACE
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * A run in flight is handed to [NotebookExecutions] when its screen clears, and the holder ends
 * it when the last run finishes with nobody left to show it.
 *
 * The screen's death used to be the run's end: `onCleared` closed the kernel's lifetime and
 * disconnected the socket, so a cell still executing lost its collection mid-stream. What is
 * asserted here is the retained half of that contract - the kernel, the state flow and the
 * checkpoint all keep working on the application scope, and only the drain ends them.
 */
class NotebookExecutionRetentionTest {
  @get:Rule val mainDispatcherRule = MainDispatcherRule()

  private val key = NotebookExecutionKey(BASE_SPACE.id, DocumentOpening("nb-1"))

  @Test
  fun leavingMidRunHandsTheKernelToTheRegistry() = runTest {
    val run = detachedRun()

    assertNotNull("the run was handed to the registry", run.retained)
    assertTrue("the socket stays open while the run does", run.gateway.disconnected.isEmpty())
    assertEquals("the run is still counted in flight", 1, run.runs.inFlight.value)
  }

  /**
   * The retained run is not a snapshot: its events still land in the state flow, its end still
   * checkpoints the document, and its drain still closes the socket and drops the entry.
   */
  @Test
  fun theRetainedRunEndsAndTheHolderDisconnects() = runTest {
    val run = detachedRun()
    val retained = checkNotNull(run.retained) { "the run was handed to the registry" }
    val writesBefore = run.repository.saveCount

    run.viewModel.kernel.stopRunningCell()
    advanceUntilIdle()

    assertNull("the drained run left the registry", run.executions.at(key))
    assertEquals(ExecutionStatus.CANCELLED, retained.uiState.value.executions["c1"]?.status)
    assertTrue("the finished run checkpointed the document", run.repository.saveCount > writesBefore)
    assertEquals(0, run.runs.inFlight.value)
    assertEquals(1, run.gateway.disconnected.size)
  }

  /** A run that already ended is not a run in flight - leaving closes the kernel as it always did. */
  @Test
  fun aFinishedRunIsNotRetained() = runTest {
    val gateway = RecordingKernelGateway()
    val executions = NotebookExecutions()
    val viewModel =
      loadedEditor(gateway = gateway, applicationScope = this, notebookExecutions = executions)
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()

    viewModel.onCleared()
    advanceUntilIdle()

    assertNull(executions.at(key))
    assertEquals(1, gateway.disconnected.size)
  }

  /**
   * An editor whose screen has just cleared mid-run: the pieces a test needs to say what the
   * detached run did next. [retained] is the registry's own answer, null when the handoff did
   * not happen - which is what the red run of the first two tests measured.
   */
  private class DetachedRun(
    val gateway: RecordingKernelGateway,
    val executions: NotebookExecutions,
    val repository: RecordingNotebookRepository,
    val viewModel: EditorViewModel,
    val retained: NotebookExecution?,
    val runs: ActiveCellRuns,
  )

  private fun TestScope.detachedRun(
    gateway: RecordingKernelGateway = RecordingKernelGateway(runsUntilInterrupted = true),
  ): DetachedRun {
    val runs = ActiveCellRuns()
    val executions = NotebookExecutions()
    val repository = RecordingNotebookRepository()
    val viewModel =
      loadedEditor(
        gateway = gateway,
        repository = repository,
        activeCellRuns = runs,
        applicationScope = this,
        notebookExecutions = executions,
      )
    viewModel.kernel.runCell(codeCell)
    advanceUntilIdle()
    viewModel.onCleared()
    advanceUntilIdle()
    return DetachedRun(gateway, executions, repository, viewModel, executions.at(key), runs)
  }
}
