package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import com.jupydroid.app.domain.port.StaleKernelSessionException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseSpaceKernelGatewayTest {
  /**
   * P1c: the other half of a server that stopped. P1a gets a fresh server; the sessions the
   * *previous* one issued still point at kernels that no longer exist, and the fresh server
   * answers the websocket upgrade with 404 - measured on the tablet, in
   * `evidence/p1c-app-death-backstop/stale-session-404.txt`. The gateway knows which server
   * issued what, so it says so rather than letting the caller find out from a protocol error.
   */
  @Test
  fun execute_refusesASessionTheLiveServerNeverIssued() = runTest {
    val gateway = gateway(RecordingServerLauncher(diesAfterFirstStart = true))
    val stale = gateway.connect(SPACE, "Analysis.ipynb")

    val failure = runCatching { gateway.execute(stale, "c1", "2 + 2").toList() }

    assertTrue(
      "expected a stale session to be refused, got ${failure.exceptionOrNull()}",
      failure.exceptionOrNull() is StaleKernelSessionException,
    )
  }

  @Test
  fun connect_issuesASessionTheServerItStartedKnows() = runTest {
    val gateway = gateway(RecordingServerLauncher(diesAfterFirstStart = true))
    gateway.connect(SPACE, "Analysis.ipynb") // stranded when the first server dies

    val fresh = gateway.connect(SPACE, "Analysis.ipynb")

    assertEquals(listOf(KernelEvent.Idle), gateway.execute(fresh, "c1", "2 + 2").toList().takeLast(1))
  }

  /**
   * P1d follows P1c's rule for [KernelGateway.disconnect] exactly: shutting a kernel down must
   * not *start* a server to do it.
   *
   * The kernel died with the server the backstop stopped, so there is nothing left to shut down -
   * and booting a whole runtime to say so would spend seven seconds and ~130 MB undoing the thing
   * P1c did.
   */
  @Test
  fun shutdown_startsNoServerForASessionTheStoppedOneIssued() = runTest {
    val launcher = RecordingServerLauncher(diesAfterFirstStart = true)
    val gateway = gateway(launcher)
    val stale = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.shutdown(stale)

    assertEquals(1, launcher.startCount)
  }

  @Test
  fun connect_surfacesAServerThatWillNotStart() = runTest {
    val gateway = gateway(RecordingServerLauncher(failWith = IllegalStateException("port in use")))

    val failure = runCatching { gateway.connect(SPACE, "Analysis.ipynb") }

    assertTrue("expected the failure to surface, got $failure", failure.isFailure)
  }

  /**
   * The hazard this class's collaborator exists to remove, asserted where it would bite.
   *
   * `JupyterServerLauncher.start` begins with `stop()`, so an export - or a terminal, or a
   * language server - that brought up its own Space would kill the kernel's server and strand
   * the session running on it. Sharing one [BaseSpaceServer] is what stops that, and the session
   * still working afterwards is the half a start count cannot show.
   */
  @Test
  fun connect_survivesASecondClientOfTheSpaceAskingForTheSameServer() = runTest {
    val launcher = RecordingServerLauncher()
    val space = BaseSpaceServer(installSpace = {}, launcher = launcher, log = recordingLog)
    val gateway = BaseSpaceKernelGateway(fakeSockets(space), { FakeKernelGateway() }, recordingLog)
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    PerRunningServer(space) { "an exporter for ${it.port}" }.get()

    assertEquals(1, launcher.startCount)
    assertEquals(listOf(KernelEvent.Idle), gateway.execute(session, "c1", "2 + 2").toList().takeLast(1))
  }

  /** The app was silent when the first real-kernel run produced nothing; this is why. */
  @Test
  fun connect_reportsTheKernelItBoundSoAFailureNamesOne() = runTest {
    gateway(RecordingServerLauncher()).connect(SPACE, "Analysis.ipynb")

    val stages = recordingLog.stages
    assertTrue("expected the kernel to be logged, got $stages", stages.any { it.contains("kernel") })
  }

  private val recordingLog = RecordingSpaceLifecycleLog()

  private fun gateway(launcher: RecordingServerLauncher): KernelGateway {
    val server = BaseSpaceServer(installSpace = {}, launcher = launcher, log = recordingLog)
    return BaseSpaceKernelGateway(
      sharedSockets = fakeSockets(server),
      gatewayFor = { FakeKernelGateway() },
      log = recordingLog,
    )
  }

  /** `gatewayFor` below never reads it - a placeholder is enough wherever `FakeKernelGateway` stands in. */
  private fun fakeSockets(server: BaseSpaceServer): PerRunningServer<KernelSockets> = PerRunningServer(server) {
    KernelSockets(ScriptedKernelSocketOpener(reply = { emptyList() }), FakeKernelLifecycle())
  }

  private inner class RecordingServerLauncher(
    private val failWith: Exception? = null,
    /** Simulates the first server process dying; the one started after it survives. */
    private val diesAfterFirstStart: Boolean = false,
  ) : ServerLauncher {
    var startCount = 0
      private set

    override suspend fun start(space: Space): ServerConnection {
      failWith?.let { throw it }
      startCount++
      return ServerConnection(baseUrl = "http://127.0.0.1:8888/", port = 8888, token = "t")
    }

    override suspend fun stop() = Unit

    override fun isRunning(): Boolean = startCount > if (diesAfterFirstStart) 1 else 0
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
