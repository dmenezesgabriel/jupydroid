package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.KernelSessionRegistry
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import com.jupydroid.app.domain.port.StaleKernelSessionException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.advanceUntilIdle
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
    val gateway = BaseSpaceKernelGateway(fakeSockets(space), { FakeKernelGateway() }, log = recordingLog)
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

  /**
   * P8f: a session this gateway minted is registered under the server's own session id, so the
   * Running panel - which asks `GET api/sessions` and sees server ids, never client ones - can
   * name it. The registry, not [issuedSessions], is what the panel shares.
   */
  @Test
  fun connect_registersTheSessionUnderItsServerSessionId() = runTest {
    val registry = KernelSessionRegistry()
    val session = gateway(RecordingServerLauncher(), registry).connect(SPACE, "Analysis.ipynb")

    assertEquals(session, registry.trackedBy(session.serverSessionId)?.kernelSession)
    assertEquals("Analysis.ipynb", registry.trackedBy(session.serverSessionId)?.documentPath)
  }

  /**
   * `disconnect` is *not* an end: the socket closes but the session stays on the server and in
   * the registry, so the panel still lists it and a reopening tab still reattaches.
   */
  @Test
  fun disconnect_keepsTheSessionRegistered() = runTest {
    val registry = KernelSessionRegistry()
    val gateway = gateway(RecordingServerLauncher(), registry)
    val session = gateway.connect(SPACE, "Analysis.ipynb")

    gateway.disconnect(session)

    assertEquals(setOf(session.serverSessionId), registry.liveIds)
  }

  @Test
  fun shutdown_emitsEndedForEveryHolderOfTheSession() = runTest {
    val registry = KernelSessionRegistry()
    val gateway = gateway(RecordingServerLauncher(), registry)
    val session = gateway.connect(SPACE, "Analysis.ipynb")
    val heard = async { registry.ended.take(1).toList() }
    advanceUntilIdle()

    gateway.shutdown(session)

    assertEquals(listOf(session.serverSessionId), heard.await())
  }

  /**
   * The replaced-server cascade, told to the registry too: `issuedSessions` alone would leave the
   * old rows tracked forever, and a notebook holding one would claim Idle on a dead kernel.
   */
  @Test
  fun aReplacedServerEndsEverySessionItIssued() = runTest {
    val registry = KernelSessionRegistry()
    val gateway = gateway(RecordingServerLauncher(diesAfterFirstStart = true), registry)
    val stranded = gateway.connect(SPACE, "Analysis.ipynb")
    val heard = async { registry.ended.take(1).toList() }
    advanceUntilIdle()

    gateway.connect(SPACE, "Analysis.ipynb")

    assertEquals(listOf(stranded.serverSessionId), heard.await())
  }

  /**
   * `PATCH api/sessions` keeps the session and swaps the kernel (`handlers.py:patch` shuts the
   * old one down itself), so what the registry tracks - and what `rebound` announces to every
   * other holder of the session - is the *returned* model, kernel id and all (P8g).
   */
  @Test
  fun changeKernel_rebindsTheTrackedSessionAndAnnouncesIt() = runTest {
    val registry = KernelSessionRegistry()
    val gateway = gateway(RecordingServerLauncher(), registry)
    val session = gateway.connect(SPACE, "Analysis.ipynb")
    val heard = async { registry.rebound.take(1).toList() }
    advanceUntilIdle()

    val changed = gateway.changeKernel(session, KernelChoice.Spec("p8g"))

    assertEquals(session.id, changed.id)
    assertEquals(session.serverSessionId, changed.serverSessionId)
    assertEquals("p8g", changed.kernelName)
    assertEquals(changed, registry.trackedBy(session.serverSessionId)?.kernelSession)
    assertEquals(listOf(changed), heard.await())
  }

  /** The P1c rule holds for a kernel change too: a session this gateway never issued is stale. */
  @Test
  fun changeKernel_refusesASessionTheLiveServerNeverIssued() = runTest {
    val gateway = gateway(RecordingServerLauncher())
    gateway.connect(SPACE, "Analysis.ipynb")
    val foreign = KernelSession(
      id = "stranger",
      spaceId = "base",
      kernelId = "k9",
      kernelName = "python3",
      serverSessionId = "ss-9",
    )

    val failure = runCatching { gateway.changeKernel(foreign, KernelChoice.Spec("p8g")) }

    assertTrue(
      "expected a stale session to be refused, got ${failure.exceptionOrNull()}",
      failure.exceptionOrNull() is StaleKernelSessionException,
    )
  }

  private val recordingLog = RecordingSpaceLifecycleLog()

  private fun gateway(
    launcher: RecordingServerLauncher,
    registry: KernelSessionRegistry = KernelSessionRegistry(),
  ): KernelGateway {
    val server = BaseSpaceServer(installSpace = {}, launcher = launcher, log = recordingLog)
    return BaseSpaceKernelGateway(
      sharedSockets = fakeSockets(server),
      gatewayFor = { FakeKernelGateway(registry) },
      registry = registry,
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
