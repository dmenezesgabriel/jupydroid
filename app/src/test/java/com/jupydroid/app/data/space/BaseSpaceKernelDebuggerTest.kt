package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeKernelDebugger
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import com.jupydroid.app.domain.port.StaleKernelSessionException
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors [BaseSpaceKernelGatewayTest]'s stale-session coverage: the debugger has no `connect()`
 * of its own to issue a session, so [attach][com.jupydroid.app.domain.port.KernelDebugger.attach]
 * is the call that "issues" one here, the same way `connect` does for the gateway.
 */
class BaseSpaceKernelDebuggerTest {
  @Test
  fun configurationDone_refusesASessionNeverAttachedToTheLiveServer() = runTest {
    val debugger = debugger(RecordingServerLauncher())

    val failure = runCatching { debugger.configurationDone(SESSION) }

    assertTrue(
      "expected an unattached session to be refused, got ${failure.exceptionOrNull()}",
      failure.exceptionOrNull() is StaleKernelSessionException,
    )
  }

  @Test
  fun configurationDone_worksOnceTheSessionHasAttached() = runTest {
    val debugger = debugger(RecordingServerLauncher())
    debugger.attach(SESSION)

    debugger.configurationDone(SESSION) // must not throw
  }

  /** The debugger's own version: a session attached before a server restart is stale after it. */
  @Test
  fun attach_issuesASessionTheServerItStartedKnows() = runTest {
    val debugger = debugger(RecordingServerLauncher(diesAfterFirstStart = true))
    debugger.attach(STRANDED_SESSION) // stranded when the first server dies

    debugger.attach(SESSION)

    debugger.configurationDone(SESSION) // does not throw
    val failure = runCatching { debugger.configurationDone(STRANDED_SESSION) }
    assertTrue(
      "expected the stranded session to be refused, got ${failure.exceptionOrNull()}",
      failure.exceptionOrNull() is StaleKernelSessionException,
    )
  }

  /**
   * The reason this class shares [PerRunningServer]&lt;[KernelSockets]&gt; with
   * [BaseSpaceKernelGateway] rather than building its own: a `debug_request` reuses the identical
   * per-session socket `execute`/`complete`/`history` already share, not a second one.
   */
  @Test
  fun attach_survivesTheGatewayHavingAlreadyBuiltTheSharedSockets() = runTest {
    val launcher = RecordingServerLauncher()
    val space = BaseSpaceServer(installSpace = {}, launcher = launcher, log = RecordingSpaceLifecycleLog())
    val sockets = fakeSockets(space)
    val gateway = BaseSpaceKernelGateway(sockets, { FakeKernelGateway() })
    val debugger = BaseSpaceKernelDebugger(sockets, { FakeKernelDebugger() })

    gateway.connect(SPACE, "Analysis.ipynb")
    debugger.attach(SESSION)

    assertEquals(1, launcher.startCount)
  }

  private fun debugger(launcher: RecordingServerLauncher): BaseSpaceKernelDebugger {
    val server = BaseSpaceServer(installSpace = {}, launcher = launcher, log = RecordingSpaceLifecycleLog())
    return BaseSpaceKernelDebugger(
      sharedSockets = fakeSockets(server),
      debuggerFor = { FakeKernelDebugger() },
    )
  }

  /** `debuggerFor` above never reads it - a placeholder is enough wherever `FakeKernelDebugger` stands in. */
  private fun fakeSockets(server: BaseSpaceServer): PerRunningServer<KernelSockets> = PerRunningServer(server) {
    KernelSockets(ScriptedKernelSocketOpener(reply = { emptyList() }), FakeKernelLifecycle())
  }

  private inner class RecordingServerLauncher(
    /** Simulates the first server process dying; the one started after it survives. */
    private val diesAfterFirstStart: Boolean = false,
  ) : ServerLauncher {
    var startCount = 0
      private set

    override suspend fun start(space: Space): ServerConnection {
      startCount++
      return ServerConnection(baseUrl = "http://127.0.0.1:8888/", port = 8888, token = "t")
    }

    override suspend fun stop() = Unit

    override fun isRunning(): Boolean = startCount > if (diesAfterFirstStart) 1 else 0
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
    val SESSION = KernelSession(
      id = "session-1",
      spaceId = "base",
      kernelId = "kernel-1",
      kernelName = "python3",
      serverSessionId = "s-1",
    )
    val STRANDED_SESSION =
      KernelSession(
        id = "session-0",
        spaceId = "base",
        kernelId = "kernel-0",
        kernelName = "python3",
        serverSessionId = "s-0",
      )
  }
}
