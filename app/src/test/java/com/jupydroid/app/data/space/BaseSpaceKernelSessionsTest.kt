@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [BaseSpaceKernelSessions] over the same per-server plumbing [BaseSpaceKernelGatewayTest] uses:
 * a [PerRunningServer] whose `liveOrNull` is the whole difference between asking (running/shutdown
 * stay passive) and doing (connect may start one). The registry is shared, so a session the
 * gateway minted is one this adapter can end.
 */
class BaseSpaceKernelSessionsTest {
  private val recordingLog = RecordingSpaceLifecycleLog()

  @Test
  fun running_listsTheSessionsTheServerIsHolding() = runTest {
    val lifecycle = FakeKernelLifecycle()
    lifecycle.start("Analysis.ipynb")
    val sockets = sockets(server(OneStartLauncher()), lifecycle = lifecycle)
    val sessions = BaseSpaceKernelSessions(sockets, KernelSessionRegistry())

    sockets.get() // the server is up, the way a connect would have brought it up

    assertEquals("Analysis.ipynb", sessions.running().single().name)
  }

  /**
   * The same rule applied to the panel: listing what is running must not start a runtime to find
   * out. The base Space's server answers for whatever the app holds anyway - stopped means the
   * local answer "nothing", and whatever was tracked is ended so its holders land on No Kernel.
   */
  @Test
  fun running_onAStoppedServerAnswersEmptyAndEndsWhatItTracked() = runTest {
    val registry = KernelSessionRegistry()
    registry.register(session("ss1"), "Analysis.ipynb")
    val sessions = BaseSpaceKernelSessions(sockets(server(OneStartLauncher())), registry)
    val heard = async { registry.ended.take(1).toList() }
    advanceUntilIdle()

    assertEquals(emptyList<Nothing>(), sessions.running())
    assertEquals(listOf("ss1"), heard.await())
  }

  /**
   * `runningChanged`'s "a session went" half, reached through the poll rather than a local call:
   * the server no longer lists `ss-gone`, so it is ended and its socket is closed - the notebook
   * holding it should not keep a dead web socket beside its No Kernel.
   */
  @Test
  fun running_endsAndClosesTheSocketOfASessionTheServerForgot() = runTest {
    val lifecycle = FakeKernelLifecycle()
    val minted = lifecycle.start("Analysis.ipynb")
    val opener = ScriptedKernelSocketOpener(reply = { emptyList() })
    val sockets = sockets(server(OneStartLauncher()), lifecycle = lifecycle, opener = opener)
    val registry = KernelSessionRegistry()
    val sessions = BaseSpaceKernelSessions(sockets, registry)
    registry.register(session(minted.serverSessionId), "Analysis.ipynb")
    val gone = session("ss-gone")
    registry.register(gone, "Gone.ipynb")
    sockets.get().socketFor(gone)
    val heard = async { registry.ended.take(1).toList() }
    advanceUntilIdle()

    sessions.running()

    assertEquals(listOf("ss-gone"), heard.await())
    assertTrue("the forgotten session's socket is closed", opener.closed)
  }

  @Test
  fun shutdown_deletesTheSessionOnTheServerAndEndsItHere() = runTest {
    val lifecycle = FakeKernelLifecycle()
    val registry = KernelSessionRegistry()
    registry.register(session("ss1"), "Analysis.ipynb")
    val sockets = sockets(server(OneStartLauncher()), lifecycle = lifecycle)
    val sessions = BaseSpaceKernelSessions(sockets, registry)
    sockets.get()
    val heard = async { registry.ended.take(1).toList() }
    advanceUntilIdle()

    sessions.shutdown("ss1")

    assertEquals(listOf("ss1"), lifecycle.shutDownSessions)
    assertEquals(listOf("ss1"), heard.await())
  }

  /** The dead-server half of truthful shutdown: nothing to DELETE against, but the id still ends. */
  @Test
  fun shutdown_withNoServerStillEndsTheTrackedSession() = runTest {
    val registry = KernelSessionRegistry()
    registry.register(session("ss1"), "Analysis.ipynb")
    val sessions = BaseSpaceKernelSessions(sockets(server(NeverStartedLauncher)), registry)
    val heard = async { registry.ended.take(1).toList() }
    advanceUntilIdle()

    sessions.shutdown("ss1")

    assertEquals(listOf("ss1"), heard.await())
  }

  /**
   * A rename reaches the server's session and this process's record of it alike, so the panel's
   * next `running()` - and the fake's, built from the registry - lists the new path.
   */
  @Test
  fun setPath_patchesTheSessionAndTracksItUnderTheNewPath() = runTest {
    val lifecycle = FakeKernelLifecycle()
    val minted = lifecycle.start("a.ipynb")
    val registry = KernelSessionRegistry()
    registry.register(session(minted.serverSessionId), "a.ipynb")
    val sockets = sockets(server(OneStartLauncher()), lifecycle = lifecycle)
    val sessions = BaseSpaceKernelSessions(sockets, registry)
    sockets.get()

    sessions.setPath(minted.serverSessionId, "g/b.ipynb")

    assertEquals(listOf(minted.serverSessionId to "g/b.ipynb"), lifecycle.movedSessions)
    assertEquals("g/b.ipynb", registry.trackedBy(minted.serverSessionId)?.documentPath)
    assertEquals("g/b.ipynb", sessions.running().single().path)
  }

  /** Passive like [BaseSpaceKernelSessions.shutdown]: a rename must never boot a runtime. */
  @Test
  fun setPath_onAStoppedServerStartsNothing() = runTest {
    val launcher = OneStartLauncher()
    val lifecycle = FakeKernelLifecycle()
    val sessions = BaseSpaceKernelSessions(sockets(server(launcher), lifecycle = lifecycle), KernelSessionRegistry())

    sessions.setPath("ss1", "b.ipynb")

    assertEquals(0, launcher.startCount)
    assertEquals(emptyList<Pair<String, String>>(), lifecycle.movedSessions)
  }

  private fun session(serverSessionId: String) = KernelSession(
    id = "c-$serverSessionId",
    spaceId = "base",
    kernelId = "k-$serverSessionId",
    kernelName = "python3",
    serverSessionId,
  )

  private fun server(launcher: ServerLauncher) = clientTestSpaceServer(launcher, recordingLog)

  private fun sockets(
    server: SpaceServer,
    lifecycle: FakeKernelLifecycle = FakeKernelLifecycle(),
    opener: ScriptedKernelSocketOpener = ScriptedKernelSocketOpener(reply = { emptyList() }),
  ) = PerRunningServer(server) { KernelSockets(opener, lifecycle) }

  private inner class OneStartLauncher : ServerLauncher {
    var startCount = 0
      private set

    override suspend fun start(space: Space): ServerConnection {
      startCount++
      return ServerConnection(baseUrl = "http://127.0.0.1:8901", port = 8901, token = "t")
    }

    override suspend fun stop() = Unit

    override fun isRunning(): Boolean = startCount > 0

    override suspend fun awaitExit() = Unit
  }

  /** Never reports a live server - `liveOrNull` gives null, no start is possible. */
  private object NeverStartedLauncher : ServerLauncher {
    override suspend fun start(space: Space): ServerConnection =
      ServerConnection(baseUrl = "http://127.0.0.1:8901", port = 8901, token = "t")

    override suspend fun stop() = Unit

    override fun isRunning(): Boolean = false

    override suspend fun awaitExit() = Unit
  }
}
