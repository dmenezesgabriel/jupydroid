package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Duration

/**
 * What a cell says about its own run: the bracket beside it, and how long it took.
 *
 * The bracket is Jupyter's, and it is the notation every Jupyter user already reads without being
 * told - `[ ]` never run, `[*]` working, `[7]` ran seventh. The header used to show `[7]` or the
 * word "code", so a code cell that had never run and one that was queued looked the same.
 */
class ExecutionLabelTest {
  @Test
  fun aCodeCellThatHasNeverRunShowsAnEmptyBracket() {
    assertEquals("[ ]", executionLabel(CellKind.CODE, ExecutionStatus.NOT_STARTED, count = null))
  }

  @Test
  fun aQueuedOrRunningCellShowsTheStar() {
    assertEquals("[*]", executionLabel(CellKind.CODE, ExecutionStatus.QUEUED, count = null))
    assertEquals("[*]", executionLabel(CellKind.CODE, ExecutionStatus.RUNNING, count = null))
  }

  @Test
  fun aFinishedCellShowsTheCountTheKernelGaveIt() {
    assertEquals("[7]", executionLabel(CellKind.CODE, ExecutionStatus.STOPPED_OK, count = 7))
  }

  /**
   * A cell that raised still gets a count from the kernel, and still shows it: the failure is
   * carried by the stripe and the traceback, not by hiding what ran.
   */
  @Test
  fun aFailedCellStillShowsItsCount() {
    assertEquals("[3]", executionLabel(CellKind.CODE, ExecutionStatus.STOPPED_ERROR, count = 3))
  }

  /**
   * A skipped cell must not read as one nobody ran, which is the whole reason [ExecutionStatus]
   * has a `SKIPPED` at all. Caught in this step's own evidence: the state was being tracked
   * correctly and drawn as `[ ]`, so on screen it was still the state it was meant to replace
   * (`evidence/u4-execution-status/03-finished-failed-and-skipped.png`, before this).
   *
   * `[-]` is ours. Jupyter has no notation for it, because Jupyter's Run All does not stop.
   */
  @Test
  fun aSkippedCellSaysSoRatherThanLookingUnrun() {
    assertEquals("[-]", executionLabel(CellKind.CODE, ExecutionStatus.SKIPPED, count = null))
  }

  /** Markdown never reaches a kernel, so a bracket would be claiming something that cannot happen. */
  @Test
  fun aMarkdownCellIsNamedRatherThanNumbered() {
    assertEquals("markdown", executionLabel(CellKind.MARKDOWN, ExecutionStatus.NOT_STARTED, count = null))
  }

  /**
   * A raw cell reaches a kernel even less than markdown does: `runCell`'s switch has no case for
   * it at all - `default: break` (`packages/notebook/src/cellexecutor.ts:140-141`) - so running one
   * is a no-op. A bracket beside it would promise a count that can never arrive.
   */
  @Test
  fun aRawCellIsNamedRatherThanNumbered() {
    assertEquals("raw", executionLabel(CellKind.RAW, ExecutionStatus.NOT_STARTED, count = null))
  }

  @Test
  fun subSecondRunsReadInTenths() {
    assertEquals("ran in 0.8 s", runDurationLabel(Duration.ofMillis(840)))
  }

  @Test
  fun secondsReadInTenthsToo() {
    assertEquals("ran in 1.2 s", runDurationLabel(Duration.ofMillis(1234)))
  }

  /** Past a minute, tenths of a second stop being the interesting digit. */
  @Test
  fun longRunsReadInMinutesAndSeconds() {
    assertEquals("ran in 1 m 05 s", runDurationLabel(Duration.ofSeconds(65)))
    assertEquals("ran in 12 m 00 s", runDurationLabel(Duration.ofMinutes(12)))
  }

  /**
   * A clock that went backwards - the device's time changing mid-run - must not print a negative
   * duration. Nothing is better than nonsense.
   */
  @Test
  fun aNegativeDurationIsNotReported() {
    assertNull(runDurationLabel(Duration.ofMillis(-5)))
  }
}
