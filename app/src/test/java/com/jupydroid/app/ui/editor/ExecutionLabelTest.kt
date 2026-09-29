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
 * told - `[ ]:` never run, `[*]:` working, `[7]:` ran seventh. The header used to show `[7]` or the
 * word "code", so a code cell that had never run and one that was queued looked the same.
 *
 * **The notation is read out of the reference rather than remembered.** `InputPrompt`'s setter is
 * `this.node.textContent = `[${'$'}{value || ' '}]:`` (`packages/cells/src/inputarea.ts:283-290`) -
 * so the trailing colon is part of it, and `[ ]:` is what an unrun cell shows because the empty
 * string falls through `||` to a space. And only `CodeCell` ever calls it: `_setPrompt` has exactly
 * one caller, `CodeCell._updatePrompt` (`packages/cells/src/widget.ts:1693-1700`), while
 * `MarkdownCell` and `RawCell` never do - so their prompt node stays textless and this app's
 * "markdown" and "raw" were invented.
 */
class ExecutionLabelTest {
  @Test
  fun aCodeCellThatHasNeverRunShowsAnEmptyBracket() {
    assertEquals("[ ]:", executionLabel(CellKind.CODE, ExecutionStatus.NOT_STARTED, count = null))
  }

  @Test
  fun aQueuedOrRunningCellShowsTheStar() {
    assertEquals("[*]:", executionLabel(CellKind.CODE, ExecutionStatus.QUEUED, count = null))
    assertEquals("[*]:", executionLabel(CellKind.CODE, ExecutionStatus.RUNNING, count = null))
  }

  @Test
  fun aFinishedCellShowsTheCountTheKernelGaveIt() {
    assertEquals("[7]:", executionLabel(CellKind.CODE, ExecutionStatus.STOPPED_OK, count = 7))
  }

  /**
   * A cell that raised still gets a count from the kernel, and still shows it: the failure is
   * carried by the stripe and the traceback, not by hiding what ran.
   */
  @Test
  fun aFailedCellStillShowsItsCount() {
    assertEquals("[3]:", executionLabel(CellKind.CODE, ExecutionStatus.STOPPED_ERROR, count = 3))
  }

  /**
   * A skipped cell must not read as one nobody ran, which is the whole reason [ExecutionStatus]
   * has a `SKIPPED` at all. Caught in this step's own evidence: the state was being tracked
   * correctly and drawn as `[ ]`, so on screen it was still the state it was meant to replace
   * (before this).
   *
   * `[-]` is ours. Jupyter has no notation for it, because Jupyter's Run All does not stop.
   */
  @Test
  fun aSkippedCellSaysSoRatherThanLookingUnrun() {
    assertEquals("[-]:", executionLabel(CellKind.CODE, ExecutionStatus.SKIPPED, count = null))
  }

  /**
   * **A markdown cell has no prompt at all**, and that is read rather than reasoned:
   * `MarkdownCell` never calls `_setPrompt`, whose only caller is `CodeCell._updatePrompt`
   * (`packages/cells/src/widget.ts:1693-1700`), so `InputPrompt.executionCount` is never set and
   * the node the reference reserves for it stays empty.
   *
   * The word "markdown" was this app's own, and it cost more than a word: it was the only thing
   * in front of a heading cell's collapser, so the collapser pushed it and two markdown cells in
   * one frame started 48dp apart.
   */
  @Test
  fun aMarkdownCellHasNoPromptAtAll() {
    assertNull(executionLabel(CellKind.MARKDOWN, ExecutionStatus.NOT_STARTED, count = null))
  }

  /**
   * A raw cell reaches a kernel even less than markdown does: `runCell`'s switch has no case for
   * it at all - `default: break` (`packages/notebook/src/cellexecutor.ts:140-141`) - so running one
   * is a no-op. `RawCell` never calls `_setPrompt` either, for the same reason.
   */
  @Test
  fun aRawCellHasNoPromptEither() {
    assertNull(executionLabel(CellKind.RAW, ExecutionStatus.NOT_STARTED, count = null))
  }

  /**
   * The status a non-code cell is in cannot bring a prompt back. Markdown does reach
   * [ExecutionStatus.STOPPED_OK] - running one renders it - and the reference still shows nothing.
   */
  @Test
  fun noStatusGivesANonCodeCellAPrompt() {
    for (status in ExecutionStatus.entries) {
      assertNull(status.name, executionLabel(CellKind.MARKDOWN, status, count = 4))
      assertNull(status.name, executionLabel(CellKind.RAW, status, count = 4))
    }
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
