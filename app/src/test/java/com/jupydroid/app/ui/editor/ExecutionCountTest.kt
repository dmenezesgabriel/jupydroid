package com.jupydroid.app.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What a code cell's gutter says about its run, in the reference's own notation - P2v-2 moved it
 * to the gutter's foot and retired the header prompt with it, because `[7]:` above the editor and
 * `[7]` at its foot are one fact twice.
 *
 * The notation is read out of the reference rather than remembered: `cellExecution.ts` draws
 * `[N]` from the kernel's count and `[ ]` when a cell has never run
 * (`view/cellParts/cellExecution.ts:94,133,181,194`). **No trailing colon** - that was JupyterLab's
 * `InputPrompt`, which this app drew until the count replaced it; VS Code's execution count is the
 * bracket alone (`media/notebook.css:256-266`).
 *
 * `[-]` is ours. Jupyter has no notation for skipped, because Jupyter's Run All does not stop;
 * without it a skipped cell draws as `[ ]` and is indistinguishable from one nobody ran - which is
 * precisely the state SKIPPED exists to tell apart.
 */
class ExecutionCountTest {
  @Test
  fun aCodeCellThatHasNeverRunShowsAnEmptyBracket() {
    assertEquals("[ ]", executionCountLabel(ExecutionStatus.NOT_STARTED, count = null))
  }

  @Test
  fun aQueuedOrRunningCellShowsTheStar() {
    assertEquals("[*]", executionCountLabel(ExecutionStatus.QUEUED, count = null))
    assertEquals("[*]", executionCountLabel(ExecutionStatus.RUNNING, count = null))
  }

  @Test
  fun aFinishedCellShowsTheCountTheKernelGaveIt() {
    assertEquals("[7]", executionCountLabel(ExecutionStatus.STOPPED_OK, count = 7))
  }

  /**
   * A cell that raised still gets a count from the kernel, and still shows it: the failure is
   * carried by the stripe and the traceback, not by hiding what ran.
   */
  @Test
  fun aFailedCellStillShowsItsCount() {
    assertEquals("[3]", executionCountLabel(ExecutionStatus.STOPPED_ERROR, count = 3))
  }

  /** A skipped cell must not read as one nobody ran, which is why SKIPPED exists at all. */
  @Test
  fun aSkippedCellSaysSoRatherThanLookingUnrun() {
    assertEquals("[-]", executionCountLabel(ExecutionStatus.SKIPPED, count = null))
  }
}
