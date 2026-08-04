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
 * inspection is a port that will drift, and the fake is what the whole UI is built on until
 * Step 10.5 swaps it out.
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
          KernelEvent.Idle -> true
        }
      },
    )
  }

  @Test
  fun connect_returnsASessionBoundToTheSpace() = runTest {
    val session = gateway().connect(SPACE)

    assertEquals(SPACE.id, session.spaceId)
    assertTrue("a session needs a kernel to talk to", session.kernelId.isNotBlank())
    assertTrue("a session needs its own id", session.id.isNotBlank())
  }

  private suspend fun KernelGateway.runCell(cellId: String = "cell-1"): List<KernelEvent> =
    execute(connect(SPACE), cellId, "2 + 2").toList()

  protected companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
