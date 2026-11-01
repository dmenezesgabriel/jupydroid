@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.domain.model

import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The bookkeeping behind `KernelSessions.ended` - the registry is what knows this process minted
 * a session, and its `ended` flow is the `session.disposed` signal the reference's
 * `SessionContext` reacts to (`packages/services/src/session/manager.ts`'s runningChanged).
 */
class KernelSessionRegistryTest {
  @Test
  fun endingASessionEmitsItsServerSessionId() = runTest {
    val registry = KernelSessionRegistry()
    registry.register(SESSION, "Analysis.ipynb")
    val heard = async { registry.ended.take(1).toList() }
    advanceUntilIdle() // the collector subscribes before the emit - `ended` replays nothing

    val ended = registry.end(SESSION.serverSessionId)

    assertEquals("the session comes back so its socket can be closed", SESSION, ended?.kernelSession)
    assertEquals(listOf(SESSION.serverSessionId), heard.await())
    assertNull("a second end is not a second emission", registry.end(SESSION.serverSessionId))
  }

  @Test
  fun endingAnUnknownSessionEmitsNothing() = runTest {
    val registry = KernelSessionRegistry()

    assertNull(registry.end("nobody's"))
    assertEquals(emptySet<String>(), registry.liveIds)
  }

  /**
   * The replaced-server case: `jupyter_server` stopped behind the app's back means every
   * session it issued is dead, and every notebook still holding one deserves to hear it - the
   * cascade, not one row at a time.
   */
  @Test
  fun endAllEmitsEveryTrackedId() = runTest {
    val registry = KernelSessionRegistry()
    registry.register(SESSION, "Analysis.ipynb")
    registry.register(SESSION.copy(id = "c2", serverSessionId = "ss2"), "scratch.py")
    val heard = async { registry.ended.take(2).toList() }
    advanceUntilIdle()

    registry.endAll()

    assertEquals(listOf("ss1", "ss2"), heard.await().sorted())
    assertEquals(emptySet<String>(), registry.liveIds)
  }

  /**
   * The `running()` diff: a session the server forgot - culled while the app held it - is ended
   * here rather than on the row's next use, and its socket gets closed by whoever reconciles.
   */
  @Test
  fun droppedEmitsOnlyWhatTheServerNoLongerHolds() = runTest {
    val registry = KernelSessionRegistry()
    registry.register(SESSION, "Analysis.ipynb")
    registry.register(SESSION.copy(id = "c2", serverSessionId = "ss2"), "scratch.py")
    val heard = async { registry.ended.take(1).toList() }
    advanceUntilIdle() // the collector subscribes before the emit - `ended` replays nothing

    val dropped = registry.dropped(liveIds = setOf("ss2"))

    assertEquals(listOf("ss1"), dropped.map { it.kernelSession.serverSessionId })
    assertEquals(listOf("ss1"), heard.await())
    assertEquals(setOf("ss2"), registry.liveIds)
  }

  /**
   * `kernelChanged` beside `disposed` (`session/session.ts`): a `PATCH api/sessions` keeps the
   * session's own id and swaps the kernel under it, so the tracked entry is *replaced*, not
   * ended - and every other holder of the same session (a console sharing it) hears the rebound
   * rather than discovering a stale kernel id on its next use.
   */
  @Test
  fun rebindingASessionReplacesItAndAnnouncesTheNewKernel() = runTest {
    val registry = KernelSessionRegistry()
    registry.register(SESSION, "Analysis.ipynb")
    val heard = async { registry.rebound.take(1).toList() }
    advanceUntilIdle()
    val rebound = SESSION.copy(kernelId = "k2", kernelName = "p8g")

    registry.rebind(rebound)

    assertEquals(rebound, registry.trackedBy(SESSION.serverSessionId)?.kernelSession)
    assertEquals(
      "the document the session was minted for stays with it",
      "Analysis.ipynb",
      registry.trackedBy(SESSION.serverSessionId)?.documentPath,
    )
    assertEquals(listOf(rebound), heard.await())
    assertEquals(setOf("ss1"), registry.liveIds)
  }

  /**
   * `setPath` (`session/default.ts`): a renamed notebook keeps its session, so the tracked entry
   * takes the new path and stays live - a move is not an end, and nothing is announced.
   */
  @Test
  fun movingASessionTracksItUnderTheNewPathAndEndsNothing() = runTest {
    val registry = KernelSessionRegistry()
    registry.register(SESSION, "a.ipynb")
    val heard = async { registry.ended.take(1).toList() }
    advanceUntilIdle()

    registry.moved(SESSION.serverSessionId, "f/b.ipynb")

    assertEquals("f/b.ipynb", registry.trackedBy(SESSION.serverSessionId)?.documentPath)
    assertEquals(SESSION, registry.trackedBy(SESSION.serverSessionId)?.kernelSession)
    assertEquals(setOf("ss1"), registry.liveIds)
    assertEquals("a move is no end", false, heard.isCompleted)
    heard.cancel()
  }

  @Test
  fun movingAnUnknownSessionTracksNothing() {
    val registry = KernelSessionRegistry()

    registry.moved("nobody's", "b.ipynb")

    assertEquals(emptySet<String>(), registry.liveIds)
  }

  private companion object {
    val SESSION = KernelSession(
      id = "c1",
      spaceId = "base",
      kernelId = "k1",
      kernelName = "python3",
      serverSessionId = "ss1",
    )
  }
}
