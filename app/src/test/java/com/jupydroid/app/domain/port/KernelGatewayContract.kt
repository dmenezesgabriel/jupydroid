package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.Space
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [KernelGateway] must do, whoever implements it.
 *
 * Run against the fake and the real one alike: a port whose implementations agree only by
 * inspection is a port that will drift, and the fake is what the whole UI runs against on a
 * device with no Space.
 *
 * Deliberately structural. Assertions live at the level both can honestly satisfy - an
 * execution reports its count, produces output, and ends idle - not at the level of what a
 * particular kernel computes, which is the real gateway's own business.
 */
abstract class KernelGatewayContract {
  protected abstract fun gateway(): KernelGateway

  @Test
  fun execute_endsWithIdleAndThenCompletes() = runTest {
    val events = gateway().runCell()

    assertEquals(KernelEvent.Idle, events.last())
    assertEquals("Idle must be terminal, not a status update", 1, events.count { it is KernelEvent.Idle })
  }

  @Test
  fun execute_reportsAnExecutionCountBeforeGoingIdle() = runTest {
    val events = gateway().runCell()

    val counts = events.filterIsInstance<KernelEvent.ExecutionCount>()
    assertEquals("expected exactly one execution count, got $events", 1, counts.size)
    assertTrue(
      "the count must precede Idle",
      events.indexOf(counts.single()) < events.indexOfLast { it is KernelEvent.Idle },
    )
  }

  @Test
  fun execute_producesOutputForTheCell() = runTest {
    val events = gateway().runCell()

    assertTrue("expected some output, got $events", events.any { it is KernelEvent.Output })
  }

  /** A shared kernel serves many cells; an event tagged with the wrong one lands in the wrong place. */
  @Test
  fun execute_tagsEveryEventWithTheCellItRanFor() = runTest {
    val events = gateway().runCell(cellId = "cell-42")

    assertTrue(
      "every non-terminal event must name cell-42, got $events",
      events.all {
        when (it) {
          is KernelEvent.Output -> it.cellId == "cell-42"
          is KernelEvent.ExecutionCount -> it.cellId == "cell-42"
          is KernelEvent.InputRequested -> it.cellId == "cell-42"
          KernelEvent.Idle -> true
        }
      },
    )
  }

  @Test
  fun complete_offersTheObviousMatchForAPrefix() = runTest {
    val gateway = gateway()

    val result = gateway.complete(gateway.connect(SPACE, "Analysis.ipynb"), PREFIX, PREFIX.length)

    assertTrue("completing \"$PREFIX\" must offer print, got $result", result.matches.any { it.insert == "print" })
  }

  /** The caller splices a match into the code by this range, so out-of-bounds is a crash there. */
  @Test
  fun complete_replacesARangeInsideTheCodeItWasGiven() = runTest {
    val gateway = gateway()

    val result = gateway.complete(gateway.connect(SPACE, "Analysis.ipynb"), PREFIX, PREFIX.length)

    assertTrue("range must not run backwards, got $result", result.replacedEnd >= result.replacedStart)
    assertTrue("range must stay in the code, got $result", result.replacedEnd <= PREFIX.length)
    assertTrue("range must not start before the code, got $result", result.replacedStart >= 0)
  }

  /**
   * `history_request` on the shell channel - what the two `access-*-history-entry` commands step
   * through (`packages/notebook/src/history.ts:339-348`).
   *
   * Structural like the rest of this file. *Which* inputs a kernel remembers is its own business -
   * a real one answers out of IPython's sqlite history and so remembers sittings this test never
   * ran - but a gateway that answered nothing after executing would leave both commands with
   * nothing to step through, and the fake and the real one would differ in the one way that
   * decides whether the feature works at all.
   */
  @Test
  fun executionHistory_holdsWhatThisSessionExecuted() = runTest {
    val gateway = gateway()
    val session = gateway.connect(SPACE, "Analysis.ipynb")
    gateway.execute(session, "cell-1", "2 + 2").toList()

    val history = gateway.executionHistory(session, entries = 10)

    assertTrue("history must hold the code just executed, got $history", "2 + 2" in history)
  }

  @Test
  fun connect_returnsASessionBoundToTheSpace() = runTest {
    val session = gateway().connect(SPACE, "Analysis.ipynb")

    assertEquals(SPACE.id, session.spaceId)
    assertTrue("a session needs a kernel to talk to", session.kernelId.isNotBlank())
    assertTrue("a session needs its own id", session.id.isNotBlank())
  }

  /**
   * The server's own session id, which is what `DELETE api/sessions/<id>` addresses.
   *
   * Asserted at the port rather than only on the real gateway because it is the *caller* that
   * needs it: `shutdown` has nothing to send without it, and a fake that left it blank would let
   * every shutdown test pass against an address the server would never accept.
   */
  @Test
  fun connect_returnsTheServersOwnSessionIdAndNotOnlyOurs() = runTest {
    val session = gateway().connect(SPACE, "Analysis.ipynb")

    assertTrue("a session needs the id the server filed it under", session.serverSessionId.isNotBlank())
  }

  /**
   * A kernel picked at open is what the session runs - `kernel.name` in the started model
   * (`session/session.ts:389`), which is also what the picker's row and the file's
   * `metadata.kernelspec` are written from.
   */
  @Test
  fun connect_startsTheKernelItWasAskedFor() = runTest {
    val session = gateway().connect(SPACE, "Analysis.ipynb", KernelChoice.Spec("julia"))

    assertEquals("julia", session.kernelName)
  }

  /**
   * `SessionContext.changeKernel` (`sessioncontext.tsx:956-960` over `session/session.ts:169-190`):
   * the **session** is patched, not replaced - its ids are what a caller keeps addressing it by,
   * and the kernel behind it is a fresh one named by the choice.
   */
  @Test
  fun changeKernel_keepsTheSessionAndBindsANewKernelOfTheNamedSpec() = runTest {
    val gateway = gateway()
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    val changed = gateway.changeKernel(session, KernelChoice.Spec("julia"))

    assertEquals(session.id, changed.id)
    assertEquals(session.serverSessionId, changed.serverSessionId)
    assertEquals("julia", changed.kernelName)
    assertTrue(
      "a *new* kernel, not the old one renamed - '${session.kernelId}' again",
      changed.kernelId != session.kernelId,
    )
  }

  /**
   * `notebook:shutdown-kernel`, and asserted here for the reason [restart_isAcceptedForALiveSession]
   * is: it emits nothing and changes nothing observable, so an implementation that quietly did
   * nothing would look identical to one that worked.
   */
  @Test
  fun shutdown_isAcceptedForALiveSession() = runTest {
    val gateway = gateway()
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.shutdown(session)
  }

  /**
   * Restart is offered by every implementation, and asserted here because it is the one kernel
   * action with no visible result of its own: nothing is emitted, no output changes, and a
   * gateway that silently did nothing would look identical to one that worked.
   */
  @Test
  fun restart_isAcceptedForALiveSession() = runTest {
    val gateway = gateway()
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.restart(session)
  }

  private suspend fun KernelGateway.runCell(cellId: String = "cell-1"): List<KernelEvent> =
    execute(connect(SPACE, "Analysis.ipynb"), cellId, "2 + 2").toList()

  protected companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())

    /** Short enough that every kernel agrees on the completion it produces. */
    const val PREFIX = "pri"
  }
}
