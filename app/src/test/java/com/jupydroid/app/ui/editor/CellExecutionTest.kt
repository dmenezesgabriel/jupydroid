package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * What a cell's execution looks like from the outside, as a state machine.
 *
 * The app used to carry one nullable `runningCellId`, which could say exactly two things: this
 * cell is running, or nothing is. A cell waiting its turn, a cell skipped because an earlier one
 * raised, and a cell the user interrupted were all indistinguishable from a cell nobody had ever
 * run.
 *
 * The states are the reference's `ProgressStatus` (`visualization/ui/ProgressStatus.kt`) and the
 * transitions are its four `ExecutionEvent`s (`visualization/execution/Events.kt`). Only the
 * states this app can actually produce are here - see [ExecutionStatus] for which, and why.
 */
class CellExecutionTest {
  private val start = Instant.parse("2026-08-07T10:00:00Z")
  private val notebook = EditorUiState(
    cells = listOf(
      Cell("c1", CellKind.CODE, "print(1)"),
      Cell("c2", CellKind.CODE, "print(2)"),
      Cell("m1", CellKind.MARKDOWN, "# notes"),
    ),
    loaded = true,
  )

  @Test
  fun aCellNobodyHasRunHasNoExecution() {
    assertNull(notebook.executionOf("c1"))
    assertEquals(ExecutionStatus.NOT_STARTED, notebook.statusOf("c1"))
  }

  @Test
  fun startingARunMarksTheCellRunningAndRecordsWhen() {
    val running = notebook.startingRun("c1", at = start)

    assertEquals(ExecutionStatus.RUNNING, running.statusOf("c1"))
    assertEquals(start, running.executionOf("c1")?.startedAt)
    assertNull("not finished yet", running.executionOf("c1")?.endedAt)
  }

  /** A re-run replaces the previous output; that was true before and stays true. */
  @Test
  fun startingARunClearsTheOutputOfTheLastOne() {
    val withOutput = notebook.copy(outputs = mapOf("c1" to listOf(streamOutput())))

    assertEquals(emptyList<Any>(), withOutput.startingRun("c1", at = start).outputs["c1"].orEmpty())
  }

  @Test
  fun finishingWithoutRaisingStopsTheCellCleanly() {
    val done = notebook.startingRun("c1", at = start).finishedRun("c1", at = start.plusMillis(1200), failed = false)

    assertEquals(ExecutionStatus.STOPPED_OK, done.statusOf("c1"))
    assertEquals(start.plusMillis(1200), done.executionOf("c1")?.endedAt)
  }

  @Test
  fun finishingAfterRaisingStopsTheCellInError() {
    val done = notebook.startingRun("c1", at = start).finishedRun("c1", at = start, failed = true)

    assertEquals(ExecutionStatus.STOPPED_ERROR, done.statusOf("c1"))
  }

  /**
   * Run All marks every code cell before starting any of them, which is what makes "waiting its
   * turn" a state a user can see rather than an absence.
   */
  @Test
  fun runAllQueuesEveryCodeCellAndLeavesMarkdownAlone() {
    val queued = notebook.queueingRun(listOf("c1", "c2"))

    assertEquals(ExecutionStatus.QUEUED, queued.statusOf("c1"))
    assertEquals(ExecutionStatus.QUEUED, queued.statusOf("c2"))
    assertEquals(ExecutionStatus.NOT_STARTED, queued.statusOf("m1"))
  }

  /**
   * Run All stops at the first cell that raises, which is Jupyter's behaviour - and the cells
   * after it are then *skipped*, not merely un-run. Left as NOT_STARTED they would look identical
   * to a fresh notebook, which is the misleading state this whole type exists to remove.
   */
  @Test
  fun stoppingARunAllSkipsTheCellsStillWaiting() {
    val stopped = notebook
      .queueingRun(listOf("c1", "c2"))
      .startingRun("c1", at = start)
      .finishedRun("c1", at = start, failed = true)
      .skippingQueued()

    assertEquals(ExecutionStatus.STOPPED_ERROR, stopped.statusOf("c1"))
    assertEquals(ExecutionStatus.SKIPPED, stopped.statusOf("c2"))
  }

  /**
   * Interrupting is not failing. The kernel reports a `KeyboardInterrupt` either way, so without
   * this the Stop button would tell the user their code was broken.
   */
  @Test
  fun interruptingMarksTheCellCancelledRatherThanFailed() {
    val cancelled = notebook.startingRun("c1", at = start).cancellingRun("c1", at = start.plusMillis(500))

    assertEquals(ExecutionStatus.CANCELLED, cancelled.statusOf("c1"))
  }

  /** The single running cell, which the app bar and the Stop control both ask for. */
  @Test
  fun theRunningCellIsWhicheverOneIsRunning() {
    assertNull(notebook.runningCellId)
    assertEquals("c1", notebook.startingRun("c1", at = start).runningCellId)
    assertNull(notebook.startingRun("c1", at = start).finishedRun("c1", at = start, failed = false).runningCellId)
  }

  /** A queued cell is not a running one: Stop has nothing to interrupt until one actually starts. */
  @Test
  fun aQueuedCellIsNotYetTheRunningCell() {
    assertNull(notebook.queueingRun(listOf("c1")).runningCellId)
  }

  /**
   * A restart replaces the interpreter, so anything mid-flight is not merely finished - it never
   * happened. Left alone, a cell that was RUNNING when the kernel was restarted would spin for
   * ever, because the events that would have ended it died with the process.
   */
  @Test
  fun restartingAbandonsWhateverWasInFlight() {
    val busy = notebook.queueingRun(listOf("c1", "c2")).startingRun("c1", at = start)

    val restarted = busy.abandoningRun()

    assertEquals(ExecutionStatus.NOT_STARTED, restarted.statusOf("c1"))
    assertEquals(ExecutionStatus.NOT_STARTED, restarted.statusOf("c2"))
    assertNull(restarted.runningCellId)
  }

  /**
   * What already finished is history and stays: JupyterLab keeps outputs and counts across a
   * plain restart and offers "restart and clear" as a separate command. Wiping them here would
   * make the two indistinguishable.
   */
  @Test
  fun restartingKeepsWhatAlreadyFinished() {
    val done = notebook.startingRun("c1", at = start).finishedRun("c1", at = start, failed = false)

    val restarted = done.abandoningRun()

    assertEquals(ExecutionStatus.STOPPED_OK, restarted.statusOf("c1"))
  }

  private fun streamOutput() = com.jupydroid.app.domain.model.CellOutput.Stream(
    name = com.jupydroid.app.domain.model.StreamName.STDOUT,
    text = "old",
  )
}
