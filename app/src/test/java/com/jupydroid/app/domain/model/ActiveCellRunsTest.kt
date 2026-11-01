@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.domain.model

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How many cell runs the app has in flight - the question the idle backstop asks before it stops the
 * server, and the one that has to stay truthful for a backgrounded computation to survive.
 */
class ActiveCellRunsTest {
  @Test
  fun nothingIsRunningToBeginWith() {
    assertEquals(0, ActiveCellRuns().inFlight.value)
  }

  @Test
  fun aRunCountsForAsLongAsItLasts() = runTest {
    val runs = ActiveCellRuns()
    val finish = CompletableDeferred<Unit>()

    launch { runs.whileRunning { finish.await() } }
    advanceUntilIdle()
    assertEquals("still running", 1, runs.inFlight.value)

    finish.complete(Unit)
    advanceUntilIdle()
    assertEquals("finished", 0, runs.inFlight.value)
  }

  /**
   * The counter that never comes back down is the failure mode that matters: the backstop would
   * be told a cell is running for ever and the server would never stop.
   */
  @Test
  fun aRunThatRaisesStillStopsCounting() = runTest {
    val runs = ActiveCellRuns()

    val raised = runCatching { runs.whileRunning { throw IllegalStateException("boom") } }

    assertEquals("boom", raised.exceptionOrNull()?.message)
    assertEquals(0, runs.inFlight.value)
  }

  @Test
  fun runsCountIndependentlyOfEachOther() = runTest {
    val runs = ActiveCellRuns()
    val first = CompletableDeferred<Unit>()
    val second = CompletableDeferred<Unit>()

    launch { runs.whileRunning { first.await() } }
    launch { runs.whileRunning { second.await() } }
    advanceUntilIdle()
    assertEquals(2, runs.inFlight.value)

    first.complete(Unit)
    advanceUntilIdle()
    assertEquals("one of the two is still going", 1, runs.inFlight.value)

    second.complete(Unit)
    advanceUntilIdle()
    assertEquals(0, runs.inFlight.value)
  }

  @Test
  fun aRunHandsBackWhateverItProduced() = runTest {
    assertEquals("42", ActiveCellRuns().whileRunning { "42" })
  }
}
