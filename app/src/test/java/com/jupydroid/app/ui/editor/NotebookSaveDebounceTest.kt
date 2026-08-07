package com.jupydroid.app.ui.editor

import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The debounce on its own, where the cases are cheap to state.
 *
 * [EditorViewModel] drives it through `editCell` and `onCleared`, but the two things a caller
 * closing the screen depends on - that a pending write is *stopped*, and that it is *reported* -
 * are one function's return value, and that is worth pinning here rather than inferring from a
 * ViewModel test.
 */
class NotebookSaveDebounceTest {
  @Test
  fun aBurstOfEditsCostsOneWrite() = runTest {
    var writes = 0
    val save = NotebookSaveDebounce(this) { writes++ }

    save.schedule()
    save.schedule()
    save.schedule()
    advanceUntilIdle()

    assertEquals(1, writes)
  }

  @Test
  fun nothingIsWrittenBeforeTypingStops() = runTest {
    var writes = 0
    val save = NotebookSaveDebounce(this) { writes++ }

    save.schedule()
    advanceTimeBy(HALF_A_DEBOUNCE)
    runCurrent()

    assertEquals(0, writes)
  }

  /** The half `onCleared` needs: there is a write owed, so it has to be flushed. */
  @Test
  fun cancelPendingReportsAWriteThatHadNotHappenedYet() = runTest {
    val save = NotebookSaveDebounce(this) {}

    save.schedule()

    assertTrue(save.cancelPending())
  }

  /** And the other half: nothing was edited, so leaving must not write and bump the mtime. */
  @Test
  fun cancelPendingReportsNothingWhenNothingWasScheduled() = runTest {
    val save = NotebookSaveDebounce(this) {}

    assertFalse(save.cancelPending())
  }

  @Test
  fun cancelPendingReportsNothingOnceTheWriteHasHappened() = runTest {
    val save = NotebookSaveDebounce(this) {}

    save.schedule()
    advanceUntilIdle()

    assertFalse(save.cancelPending())
  }

  /** Cancelling has to actually stop it, or the flush and the debounce both write. */
  @Test
  fun aCancelledWriteNeverRuns() = runTest {
    var writes = 0
    val save = NotebookSaveDebounce(this) { writes++ }

    save.schedule()
    save.cancelPending()
    advanceUntilIdle()

    assertEquals(0, writes)
  }

  private companion object {
    /** Under the 500 ms the debounce waits, so the write is still owed at this point. */
    const val HALF_A_DEBOUNCE = 250L
  }
}
