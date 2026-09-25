package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.EditorSettings
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.FileNotFoundException

/**
 * The document's save machinery on its own, where the cases are cheap to state.
 *
 * Two halves are pinned here rather than inferred from a ViewModel test: the debounce half
 * (typing stops -> one write, owed or not on the way out) is what `NotebookSaveDebounce` already
 * proved, and the poll half - write the dirty document every `autosaveIntervalSeconds` - is what
 * JupyterLab's `SaveHandler` does (`docmanager/src/savehandler.ts`). Ours waits for dirt rather
 * than ticking blindly, because a periodic delay on the test scheduler never lets
 * `advanceUntilIdle` return; the observable behaviour is the reference's: no work queued on a
 * clean document, a write at most one interval stale while typing never stops.
 */
@OptIn(ExperimentalCoroutinesApi::class) // `testScheduler` - the poll must share the test clock
class DocumentAutosaveTest {
  /** The debounce half: what `NotebookSaveDebounce` pinned before it grew into this class. */
  @Test
  fun aBurstOfEditsCostsOneWrite() = runTest {
    var dirty = false
    var writes = 0
    val save = autosave(dirty = { dirty }, writes = {
      writes++
      dirty = false
    })

    dirty = true
    save.schedule()
    save.schedule()
    save.schedule()
    advanceUntilIdle()

    assertEquals(1, writes)
  }

  @Test
  fun nothingIsWrittenBeforeTypingStops() = runTest {
    var writes = 0
    val save = autosave(writes = { writes++ })

    save.schedule()
    advanceTimeBy(HALF_A_DEBOUNCE)
    runCurrent()

    assertEquals(0, writes)
  }

  /** The half `onCleared` needs: there is a write owed, so it has to be flushed. */
  @Test
  fun cancelPendingReportsAWriteThatHadNotHappenedYet() = runTest {
    val save = autosave()

    save.schedule()

    assertTrue(save.cancelPending())
  }

  /** And the other half: nothing was edited, so leaving must not write and bump the mtime. */
  @Test
  fun cancelPendingReportsNothingWhenNothingWasScheduled() = runTest {
    val save = autosave()

    assertFalse(save.cancelPending())
  }

  @Test
  fun cancelPendingReportsNothingOnceTheWriteHasHappened() = runTest {
    var dirty = false
    val save = autosave(dirty = { dirty }, writes = { dirty = false })

    dirty = true
    save.schedule()
    advanceUntilIdle()

    assertFalse(save.cancelPending())
  }

  /**
   * Cancelling has to actually stop it, or the flush and the debounce both write. The owed write
   * is still owed - the document is still dirty - so the poll the same edit armed picks it up at
   * the interval; what this pins is only that the debounce job itself never runs.
   */
  @Test
  fun aCancelledWriteNeverRuns() = runTest {
    var dirty = false
    var writes = 0
    val save = autosave(dirty = { dirty }, writes = {
      writes++
      dirty = false
    })

    dirty = true
    save.schedule()
    save.cancelPending()
    advanceTimeBy(A_DEBOUNCE)
    runCurrent()

    assertEquals(0, writes)
  }

  /** The poll half: a document nobody edited is not a document that needs writing. */
  @Test
  fun aCleanDocumentIsNeverWritten() = runTest {
    var writes = 0
    autosave(dirty = { false }, writes = { writes++ })

    advanceTimeBy(AN_INTERVAL * 3)
    runCurrent()

    assertEquals(0, writes)
  }

  /**
   * What the debounce alone cannot do: typing that never pauses keeps the debounce armed forever,
   * so the interval is the only bound on how stale the file can get - `SaveHandler`'s whole job.
   * `dirty` never clears here precisely because the writer keeps typing between ticks.
   */
  @Test
  fun thePollWritesWhileTypingNeverStops() = runTest {
    var writes = 0
    val save = autosave(dirty = { true }, writes = { writes++ })

    save.schedule()
    advanceTimeBy(A_DEBOUNCE)
    runCurrent()
    val debounced = writes
    advanceTimeBy(AN_INTERVAL)
    runCurrent()

    assertEquals(1, debounced)
    assertEquals(2, writes)
  }

  /**
   * `SaveHandler` re-arms its timer after a failure, so a write that failed is tried again.
   * Ours retries at the next poll - the debounce's failure here is picked up by the tick that
   * the same edit already armed.
   */
  @Test
  fun aFailedWriteIsRetriedByThePoll() = runTest {
    var calls = 0
    var dirty = true
    val save = autosave(dirty = { dirty }) {
      calls++
      if (calls == 1) throw FileNotFoundException("first write fails")
      dirty = false
    }

    save.schedule()
    advanceTimeBy(A_DEBOUNCE)
    runCurrent()
    assertEquals(1, calls)
    advanceTimeBy(AN_INTERVAL)
    runCurrent()

    assertEquals(2, calls)
  }

  /**
   * After a failure the poll goes back to sleep until the next edit - the signal it waits on is
   * `schedule()`, which every writer already calls. A failed write with nothing new to say does
   * not spin: locally a write failure is a filesystem fact, not a transient blip, and a retry
   * with identical bytes fails identically.
   */
  @Test
  fun aFailedPollWaitsForTheNextEditRatherThanSpinning() = runTest {
    var calls = 0
    val save = autosave(dirty = { true }) {
      calls++
      if (calls <= 2) throw FileNotFoundException("writes keep failing")
    }

    save.schedule()
    advanceTimeBy(A_DEBOUNCE + AN_INTERVAL)
    runCurrent()
    assertEquals(2, calls)
    // Both the debounce and the poll have now failed - the queue must be empty enough for
    // advanceUntilIdle to return, which is the whole reason the poll waits on edits.
    advanceUntilIdle()
    // The next edit re-arms both halves: the debounce retries at its delay, the poll at the
    // interval.
    save.schedule()
    advanceTimeBy(A_DEBOUNCE)
    runCurrent()
    assertEquals(3, calls)
    advanceTimeBy(AN_INTERVAL)
    runCurrent()

    assertEquals(4, calls)
  }

  /**
   * `autosave: false` stops the machinery outright - no debounced write, no poll. The document
   * is dirty and stays dirty until something else (a close flush) writes it, which is exactly
   * the reference's semantics: the `SaveHandler` is never started.
   */
  @Test
  fun disablingAutosaveWritesNothing() = runTest {
    var writes = 0
    val save = autosave(enabled = false, dirty = { true }, writes = { writes++ })

    save.schedule()
    advanceUntilIdle()
    advanceTimeBy(AN_INTERVAL * 3)
    runCurrent()

    assertEquals(0, writes)
  }

  /** Turning it off mid-flight stops the armed poll, not just the next edit - `collectLatest`. */
  @Test
  fun turningAutosaveOffStopsTheArmedPoll() = runTest {
    var writes = 0
    val settings = MutableStateFlow(EditorSettings(autosaveIntervalSeconds = AN_INTERVAL_SECONDS))
    val save = autosave(settings = settings, dirty = { true }, writes = { writes++ })

    save.schedule()
    advanceTimeBy(A_DEBOUNCE)
    runCurrent()
    settings.value = EditorSettings(autosaveEnabled = false, autosaveIntervalSeconds = AN_INTERVAL_SECONDS)
    advanceTimeBy(AN_INTERVAL * 3)
    runCurrent()

    assertEquals(1, writes)
  }

  /** The close-time verb for "Save" in the close dialog: write now, don't wait for the debounce. */
  @Test
  fun flushWritesWithoutWaiting() = runTest {
    var writes = 0
    val save = autosave(writes = { writes++ })

    save.flush()

    assertEquals(1, writes)
  }

  /** The flush stands in for the debounced write, so the two together cost one write. */
  @Test
  fun flushCountsThePendingWriteOnce() = runTest {
    var dirty = false
    var writes = 0
    val save = autosave(dirty = { dirty }, writes = {
      writes++
      dirty = false
    })

    dirty = true
    save.schedule()
    save.flush()
    advanceUntilIdle()

    assertEquals(1, writes)
  }

  /** "Discard" in the close dialog: the owed write is dropped, not made on the way out. */
  @Test
  fun discardDropsThePendingWrite() = runTest {
    var dirty = false
    var writes = 0
    val save = autosave(dirty = { dirty }, writes = {
      writes++
      dirty = false
    })

    dirty = true
    save.schedule()
    save.discard()
    advanceUntilIdle()
    advanceTimeBy(AN_INTERVAL * 3)
    runCurrent()

    assertEquals(0, writes)
  }

  /** `SaveHandler.dispose` - after discard the document is closed, so nothing writes again. */
  @Test
  fun schedulingAfterDiscardIsIgnored() = runTest {
    var writes = 0
    val save = autosave(dirty = { true }, writes = { writes++ })

    save.discard()
    save.schedule()
    advanceUntilIdle()

    assertEquals(0, writes)
  }

  /** A flush after discard is a caller bug, but the object still holds the line. */
  @Test
  fun flushAfterDiscardWritesNothing() = runTest {
    var writes = 0
    val save = autosave(writes = { writes++ })

    save.discard()
    save.flush()

    assertEquals(0, writes)
  }

  /**
   * An autosave on a scope sharing this test's scheduler, so `advanceTimeBy` still drives the
   * debounce and the poll, but the poll's sleep between edits - which never ends on its own -
   * is not a child runTest waits on. `backgroundScope` is not the answer: its tasks are skipped
   * by `advanceUntilIdle`, which is why the debounce would never have fired at all.
   */
  private fun TestScope.autosave(
    enabled: Boolean = true,
    dirty: () -> Boolean = { true },
    settings: MutableStateFlow<EditorSettings> =
      MutableStateFlow(EditorSettings(autosaveEnabled = enabled, autosaveIntervalSeconds = AN_INTERVAL_SECONDS)),
    writes: suspend () -> Unit = {},
  ) = DocumentAutosave(TestScope(testScheduler), settings, dirty, writes)

  private companion object {
    /** Under the 500 ms the debounce waits, so the write is still owed at this point. */
    const val HALF_A_DEBOUNCE = 250L
    const val A_DEBOUNCE = 501L
    const val AN_INTERVAL_SECONDS = 5
    const val AN_INTERVAL = 5_001L
  }
}
