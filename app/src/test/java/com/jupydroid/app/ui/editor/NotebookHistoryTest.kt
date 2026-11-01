@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.ui.editor

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.StaleKernelSessionException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `notebook:access-previous-history-entry` and `notebook:access-next-history-entry`, from the cell
 * being edited (`packages/notebook/src/actions.tsx:2613-2643`).
 *
 * What is asserted here is the **session**: when one is opened, what ends it, how it grows, and
 * what reaches the cell. Where in a filtered list the cursor lands is [NotebookHistoryCursorTest]'s.
 */
class NotebookHistoryTest {
  private val space = Space(id = "base", name = "Base", kernels = emptyList())
  private lateinit var gateway: ScriptedHistoryGateway
  private lateinit var history: NotebookHistory

  /** What the focused field was last given, which is the only thing either command produces. */
  private var inCell: String? = null

  /**
   * Typing must not boot a Python runtime nobody asked for - the rule
   * [NotebookKernel.completionsAt] states, applied to the other read-only ask on the socket.
   */
  @Test
  fun previous_asksNothingOfAKernelThatWasNeverOpened() = runTest {
    historyOver(listOf("x = 1"), withAnOpenKernel = false)

    assertNull(previous())
    assertTrue("no kernel may be opened by a history key, got ${gateway.batches}", gateway.batches.isEmpty())
  }

  @Test
  fun previous_putsTheMostRecentEntryInTheCell() = runTest {
    historyOver(listOf("x = 1", "x + 1"))

    assertEquals("x + 1", previous())
  }

  @Test
  fun previous_walksBackOneEntryPerTap() = runTest {
    historyOver(listOf("x = 1", "x + 1"))
    previous()

    assertEquals("x = 1", previous())
  }

  @Test
  fun next_returnsTheWayItCame() = runTest {
    historyOver(listOf("x = 1", "x + 1"))
    previous()
    previous()

    assertEquals("x + 1", next())
  }

  @Test
  fun next_pastTheEndRestoresWhatWasBeingTyped() = runTest {
    historyOver(listOf("x = 1"))
    previous(placeholder = "half typ")

    assertEquals("half typ", next(placeholder = "half typ"))
  }

  /**
   * **Departure, and the reference's guard is a JavaScript accident rather than a decision.**
   * `updateEditor` returns on `!content` (`history.ts:217`), and the comment beside `back()` says
   * what that is for - *"This shouldn't ever be undefined as setFilter will always be run first"*
   * (`:187`). It catches `''` only because the empty string is falsy. Transcribed literally it
   * would mean walking back out of an **empty** cell and never being able to walk forward into it
   * again, which is the commonest way into this feature and a control with no way back. Here
   * `NotebookHistoryCursor.current` is non-null by construction, so there is no null to guard.
   */
  @Test
  fun next_pastTheEndEmptiesACellThatStartedEmpty() = runTest {
    historyOver(listOf("x = 1"))
    previous()

    assertEquals("", next())
  }

  /** `fetchBatch` (`history.ts:247-273`): the tail grows by ten, rather than paging from a cursor. */
  @Test
  fun previous_asksForALongerTailOnceItHasWalkedTheOneItHas() = runTest {
    historyOver((1..25).map { "line $it" })

    repeat(WALKS_PAST_THE_FIRST_BATCH) { previous() }

    assertEquals(listOf(10, 20), gateway.batches)
  }

  @Test
  fun previous_reachesAnEntryOnlyTheLongerTailHeld() = runTest {
    historyOver((1..25).map { "line $it" })

    repeat(WALKS_PAST_THE_FIRST_BATCH) { previous() }

    // Eleven steps back from the newest of twenty-five: the first ten are the batch it started
    // with, and `line 15` is the one the second ask had to fetch.
    assertEquals("line 15", inCell)
  }

  /**
   * Tapping past the oldest entry stays on it rather than walking off the list - and asks the
   * kernel again each time, which is the reference's own behaviour: `fetchBatch` runs on every
   * overshoot (`history.ts:182-184`). A kernel that had nothing more a moment ago may have run
   * something since, and remembering otherwise would be inventing state the reference does not
   * keep. Asserted rather than left implicit, because it is the kind of thing a later reader would
   * otherwise "fix".
   */
  @Test
  fun previous_pastTheOldestEntryStaysOnItAndAsksAgain() = runTest {
    historyOver(listOf("only"))

    repeat(3) { previous() }

    assertEquals("only", inCell)
    assertEquals(listOf(10, 20, 30), gateway.batches)
  }

  /**
   * `onTextChange` -> `reset()` (`history.ts:313-319`): typing starts a new session, so the next
   * tap offers the most recent entry again and filters against what is in the cell *now*.
   */
  @Test
  fun typing_startsAFreshSession() = runTest {
    historyOver(listOf("x = 1", "x + 1"))
    previous()
    previous()

    history.sourceReported("z")

    assertEquals("x + 1", previous(placeholder = "z"))
  }

  /**
   * `_setByHistory` (`history.ts:314-317`): the edit history itself just made must not reset the
   * session, or every tap would be the first and the walk could never leave the newest entry.
   */
  @Test
  fun theWriteHistoryItselfMadeDoesNotResetTheSession() = runTest {
    historyOver(listOf("x = 1", "x + 1"))
    previous()

    // What `CodeCellField` reports back once its debounce elapses - the very text just written.
    history.sourceReported("x + 1")

    assertEquals("x = 1", previous())
  }

  /**
   * Leaving the cell ends the session - a **narrowing** of the reference, which binds its editor
   * once and keeps `_hasSession` across a cell change (`history.ts:160-170`), so walking forward
   * to the end writes the *first* cell's placeholder into whichever cell has the caret now. The
   * placeholder belongs to a cell, and restoring it into a different one is a control that lies.
   */
  @Test
  fun leavingTheCell_startsAFreshSession() = runTest {
    historyOver(listOf("x = 1", "x + 1"))
    previous()
    previous()

    history.reset()

    assertEquals("x + 1", previous())
  }

  /** A kernel that has run nothing offers what the cell already holds, so nothing changes. */
  @Test
  fun previous_offersWhatIsAlreadyThereForAKernelThatHasRunNothing() = runTest {
    historyOver(emptyList())

    assertEquals("typed", previous(placeholder = "typed"))
  }

  /**
   * The idle backstop stops the server while the app lives on, so a session the notebook holds can outlive
   * the process that knew about it. A history key must then do **nothing** rather than crash or
   * start a runtime: `StaleKernelSessionException` is an `IllegalStateException` precisely so a
   * caller with nothing to say about it keeps working (its own docstring says so), and
   * reconnecting here would boot a server to answer a keystroke.
   */
  @Test
  fun previous_isSilentWhenTheServerBehindTheSessionHasStopped() = runTest {
    historyOver(listOf("x = 1"))
    gateway.stale = true

    assertNull(previous())
  }

  /**
   * And a failure **mid-walk** ends the walk rather than leaving it half open, which is the only
   * path that needs the reset - a walk that never opened has nothing to drop.
   *
   * Asserted on the tail lengths asked for, because that is what separates the two behaviours: a
   * kept cursor would go on asking for ever-longer tails of a kernel that is gone (10, 20, 30),
   * where an ended one starts again from the first batch.
   */
  @Test
  fun aFailureMidWalkEndsTheWalkRatherThanLeavingItHalfOpen() = runTest {
    historyOver(listOf("only"))
    previous()
    gateway.stale = true
    previous()

    gateway.stale = false
    previous()

    assertEquals(listOf(10, 20, 10), gateway.batches)
  }

  /**
   * Two taps before the first answer comes back must still walk two entries.
   *
   * Not hypothetical and not about the fake: `executionHistory` is a websocket round trip, so it
   * suspends, and walking back several entries by tapping quickly is *the* way this feature is
   * used. Without serialisation both taps see no walk in progress, both open one, and the second
   * overwrites the first - so two taps move one entry and the second is silently lost.
   */
  @Test
  fun twoTapsBeforeTheFirstAnswerStillWalkTwoEntries() = runTest {
    historyOver(listOf("x = 1", "x + 1"))

    history.recallPrevious("") { inCell = it }
    history.recallPrevious("") { inCell = it }
    advanceUntilIdle()

    assertEquals("x = 1", inCell)
  }

  // --- the doubles -------------------------------------------------------------------------

  private suspend fun TestScope.historyOver(entries: List<String>, withAnOpenKernel: Boolean = true) {
    gateway = ScriptedHistoryGateway(entries)
    val session = NotebookKernelSession(gateway, space) { "n.ipynb" }
    // What running a cell would already have done. History never opens one itself.
    if (withAnOpenKernel) session.open()
    history = NotebookHistory(session, gateway, this)
  }

  /** Taps the key and settles, since the fetch and the write happen on a launched job. */
  private fun TestScope.previous(placeholder: String = ""): String? {
    history.recallPrevious(placeholder) { inCell = it }
    advanceUntilIdle()
    return inCell
  }

  private fun TestScope.next(placeholder: String = ""): String? {
    history.recallNext(placeholder) { inCell = it }
    advanceUntilIdle()
    return inCell
  }

  /** Answers a fixed history, records the tail lengths it was asked for, and can refuse. */
  private class ScriptedHistoryGateway(
    private val recorded: List<String>,
    /** Whether the server behind this session has stopped, as the idle backstop can make it. */
    var stale: Boolean = false,
  ) : KernelGateway by FakeKernelGateway() {
    val batches = mutableListOf<Int>()

    override suspend fun executionHistory(session: KernelSession, entries: Int): List<String> {
      // Recorded before the refusal, not after: what was *asked for* is what says whether a failed
      // walk was ended or kept, and an ask that throws still says which.
      batches += entries
      // The real one is a websocket round trip and suspends; a double that answered without ever
      // suspending would hide every ordering question this class has.
      yield()
      if (stale) throw StaleKernelSessionException(session.id)
      return recorded.takeLast(entries)
    }
  }

  private companion object {
    /** One tap onto the newest entry, then ten more - the eleventh runs off the first batch of ten. */
    const val WALKS_PAST_THE_FIRST_BATCH = 11
  }
}
