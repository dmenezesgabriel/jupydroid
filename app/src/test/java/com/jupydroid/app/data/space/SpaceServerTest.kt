package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceServerTest {
  @Test
  fun connection_installsTheSpaceAndStartsTheServerBeforeHandingOneOut() = runTest {
    val launcher = RecordingServerLauncher()

    server(launcher).connection()

    assertEquals(listOf("install", "start"), installedThenStarted)
    assertEquals(1, launcher.startCount)
  }

  /** Starting the server takes seconds; doing it per caller would pay that every time. */
  @Test
  fun connection_startsTheServerOnceHoweverManyTimesItIsAskedFor() = runTest {
    val launcher = RecordingServerLauncher()
    val server = server(launcher)

    repeat(3) { server.connection() }

    assertEquals(1, launcher.startCount)
  }

  /**
   * **The reason this class exists.** `JupyterServerLauncher.start` begins with `stop()`, so a
   * second lazy starter beside the kernel gateway would kill the kernel's server on the first
   * export. One object owns the start and both clients ask it.
   */
  @Test
  fun connection_isOneServerHoweverManyClientsOfTheSpaceThereAre() = runTest {
    val launcher = RecordingServerLauncher()
    val server = server(launcher)
    val kernels = PerRunningServer(server) { "kernel gateway for ${it.port}" }
    val exports = PerRunningServer(server) { "exporter for ${it.port}" }

    kernels.get()
    exports.get()
    kernels.get()

    assertEquals(1, launcher.startCount)
  }

  /** Two cells run at once would otherwise each see no server and each start one. */
  @Test
  fun connection_startsOneServerEvenWhenTwoCallersRaceForIt() = runTest {
    val launcher = RecordingServerLauncher()
    val server = server(launcher)

    val first = async { server.connection() }
    val second = async { server.connection() }
    first.await()
    second.await()

    assertEquals(1, launcher.startCount)
  }

  /**
   * A server that has stopped must not be handed out. The next ask starts a fresh one.
   */
  @Test
  fun connection_startsAFreshServerWhenThePreviousOneIsNoLongerRunning() = runTest {
    val launcher = RecordingServerLauncher(diesAfterFirstStart = true)
    val server = server(launcher)

    server.connection()
    server.connection()

    assertEquals(2, launcher.startCount)
  }

  /**
   * The generation is what tells a client that its cached connection belongs to a server that is
   * gone. Nothing else can: a relaunched `JupyterServerLauncher` reuses its token, so two
   * [ServerConnection]s can compare equal across a restart if the OS hands back the same port.
   */
  @Test
  fun connection_countsTheServerItStartedSoAClientCanTellAReplacementApart() = runTest {
    val server = server(RecordingServerLauncher(diesAfterFirstStart = true))

    val first = server.connection()
    val second = server.connection()

    assertNotEquals(first.generation, second.generation)
  }

  @Test
  fun connection_keepsOneGenerationWhileTheSameServerIsRunning() = runTest {
    val server = server(RecordingServerLauncher())

    assertEquals(server.connection().generation, server.connection().generation)
  }

  @Test
  fun connection_surfacesAServerThatWillNotStart() = runTest {
    val server = server(RecordingServerLauncher(failWith = IllegalStateException("port in use")))

    val failure = runCatching { server.connection() }

    assertTrue("expected the failure to surface, got $failure", failure.isFailure)
  }

  /** The app was silent when the first real-kernel run produced nothing; this is why. */
  @Test
  fun connection_reportsEachStageItReachedSoAFailureNamesOne() = runTest {
    server(RecordingServerLauncher()).connection()

    val stages = recordingLog.stages
    assertTrue("expected the server url to be logged, got $stages", stages.any { it.contains("127.0.0.1:8888") })
  }

  @Test
  fun connection_logsTheStageItFailedAtRatherThanFailingSilently() = runTest {
    val server = server(RecordingServerLauncher(failWith = IllegalStateException("port in use")))

    runCatching { server.connection() }

    assertTrue("expected the failing stage to be logged", recordingLog.failures.isNotEmpty())
  }

  /** The launcher decides, not a flag this class sets when it starts something. */
  @Test
  fun isRunning_answersForTheProcessRatherThanForWhatWasEverStarted() = runTest {
    val server = server(RecordingServerLauncher(diesAfterFirstStart = true))

    server.connection() // the first process, which this launcher reports as already gone
    assertFalse("a dead server must not report itself running", server.isRunning())
    server.connection() // its replacement, which survives
    assertTrue("the replacement is running", server.isRunning())
  }

  @Test
  fun perRunningServer_buildsItsClientOnceWhileTheServerLives() = runTest {
    val clients = PerRunningServer(server(RecordingServerLauncher())) { Any() }

    assertSame(clients.get(), clients.get())
  }

  @Test
  fun perRunningServer_buildsAFreshClientWhenTheServerIsReplaced() = runTest {
    val clients = PerRunningServer(server(RecordingServerLauncher(diesAfterFirstStart = true))) { Any() }

    assertNotSame(clients.get(), clients.get())
  }

  /**
   * The rule the exporter needs as much as the gateway does: closing a websocket
   * the server has already forgotten must not boot a whole runtime to do it.
   */
  @Test
  fun perRunningServer_offersNothingRatherThanStartingAServerThatIsNotRunning() = runTest {
    val launcher = RecordingServerLauncher()
    val clients = PerRunningServer(server(launcher)) { Any() }

    assertNull(clients.liveOrNull())
    assertEquals(0, launcher.startCount)
  }

  /** What lets the home say Running: the start is reported, not inferred later by asking. */
  @Test
  fun connection_reportsTheSpaceRunningOnceItsServerHasStarted() = runTest {
    server(RecordingServerLauncher()).connection()

    assertEquals(listOf(true), reported)
  }

  /**
   * A server that stops itself when idle is reported stopped by its own exit - nothing asked it
   * to stop, so nothing else could know.
   */
  @Test
  fun connection_reportsTheSpaceStoppedWhenItsServerProcessExits() = runTest {
    val launcher = RecordingServerLauncher()
    server(launcher).connection()

    launcher.exit()
    runCurrent()

    assertEquals(listOf(true, false), reported)
  }

  /** A Space pre-starts its default kernel once its server is up (ADR-0080). */
  @Test
  fun connection_prestartsOneSpareKernelOnTheServerItJustStarted() = runTest {
    val server = server(RecordingServerLauncher(), prestart = { BareKernel("spare-${it.port}", "python3") })

    repeat(2) { server.connection() }

    assertEquals(BareKernel("spare-8888", "python3"), server.claimSpareKernel())
    assertEquals(1, prestarts)
    assertEquals("spare-8888", recordingLog.fieldsOf(KernelLog.SPARE_STARTED)["kernelId"])
  }

  /** At most one spare per running Space, so a second session starts its kernel by name. */
  @Test
  fun claimSpareKernel_handsTheSpareOutOnce() = runTest {
    val server = server(RecordingServerLauncher(), prestart = { BareKernel("spare", "python3") })
    server.connection()

    server.claimSpareKernel()

    assertNull(server.claimSpareKernel())
  }

  /** A claim that the spare does not suit leaves it for a session it does. */
  @Test
  fun claimSpareKernel_leavesASpareItsCallerDoesNotAccept() = runTest {
    val server = server(RecordingServerLauncher(), prestart = { BareKernel("spare", "python3") })
    server.connection()

    assertNull(server.claimSpareKernel { it.kernelName == "julia" })
    assertEquals("spare", server.claimSpareKernel()?.kernelId)
  }

  /** A spare is an optimisation, never a reason for a start or a session to fail. */
  @Test
  fun claimSpareKernel_isNothingWhenThePrestartFailed() = runTest {
    val server = server(RecordingServerLauncher(), prestart = { throw KernelLifecycleException("HTTP 500") })

    server.connection()

    assertNull(server.claimSpareKernel())
    assertEquals(listOf(KernelLog.SPARE_FAILED), recordingLog.failures)
  }

  /** The start never waits on its spare: the kernel's own start runs while the user walks. */
  @Test
  fun connection_doesNotWaitForTheSpareToStart() = runTest {
    val spareStarted = CompletableDeferred<BareKernel>()
    val server = server(RecordingServerLauncher(), prestart = { spareStarted.await() })

    server.connection()
    val claim = async { server.claimSpareKernel() }
    runCurrent()
    spareStarted.complete(BareKernel("spare", "python3"))

    assertEquals("spare", claim.await()?.kernelId)
  }

  /** A replaced server's spare died with it; the new server gets its own. */
  @Test
  fun connection_prestartsAFreshSpareForAReplacementServer() = runTest {
    val server = server(RecordingServerLauncher(diesAfterFirstStart = true), prestart = {
      BareKernel("spare-$prestarts", "python3")
    })

    server.connection()
    runCurrent() // the first server's spare starts before that server is replaced
    server.connection()

    assertEquals("spare-2", server.claimSpareKernel()?.kernelId)
    assertNull(server.claimSpareKernel())
  }

  @Test
  fun claimSpareKernel_isNothingWhenTheSpaceHasNoPrestart() = runTest {
    val server = server(RecordingServerLauncher())

    server.connection()

    assertNull(server.claimSpareKernel())
  }

  @Test
  fun stop_asksTheLauncherToStopTheServer() = runTest {
    val launcher = RecordingServerLauncher()
    val server = server(launcher)
    server.connection()

    server.stop()
    runCurrent()

    assertEquals(1, launcher.stopCount)
    assertEquals(listOf(true, false), reported)
  }

  private val installedThenStarted = mutableListOf<String>()
  private val recordingLog = RecordingSpaceLifecycleLog()
  private val reported = mutableListOf<Boolean>()
  private var prestarts = 0

  private fun TestScope.server(
    launcher: RecordingServerLauncher,
    prestart: (suspend (ServerConnection) -> BareKernel)? = null,
  ) = SpaceServer(
    space = SPACE,
    installSpace = { installedThenStarted += "install" },
    launcher = launcher,
    watchScope = backgroundScope,
    reportRunning = { reported += it },
    log = recordingLog,
    prestartKernel = prestart?.let { start ->
      { connection ->
        prestarts++
        start(connection)
      }
    },
  )

  private inner class RecordingServerLauncher(
    private val failWith: Exception? = null,
    /** Simulates the first server process dying; the one started after it survives. */
    private val diesAfterFirstStart: Boolean = false,
  ) : ServerLauncher {
    var startCount = 0
      private set

    override suspend fun start(space: Space): ServerConnection {
      failWith?.let { throw it }
      installedThenStarted += "start"
      startCount++
      exited = CompletableDeferred() // a fresh process, which has not exited yet
      return ServerConnection(baseUrl = "http://127.0.0.1:8888/", port = 8888, token = "t")
    }

    var stopCount = 0
      private set

    private var exited = CompletableDeferred<Unit>()

    /** The process leaving on its own, as an idle server does. */
    fun exit() {
      exited.complete(Unit)
    }

    override suspend fun stop() {
      stopCount++
      exit()
    }

    override fun isRunning(): Boolean = !exited.isCompleted && startCount > if (diesAfterFirstStart) 1 else 0

    override suspend fun awaitExit() = exited.await()
  }

  private companion object {
    val SPACE = Space(id = "space-1", name = "One", kernels = emptyList())
  }
}
