package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseSpaceServerTest {
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
   * P1a: a server that has stopped must not be handed out. The next ask starts a fresh one.
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
   * P1c's rule, and the one the exporter needs as much as the gateway does: closing a websocket
   * the server has already forgotten must not boot a whole runtime to do it.
   */
  @Test
  fun perRunningServer_offersNothingRatherThanStartingAServerThatIsNotRunning() = runTest {
    val launcher = RecordingServerLauncher()
    val clients = PerRunningServer(server(launcher)) { Any() }

    assertNull(clients.liveOrNull())
    assertEquals(0, launcher.startCount)
  }

  private val installedThenStarted = mutableListOf<String>()
  private val recordingLog = RecordingSpaceLifecycleLog()

  private fun server(launcher: RecordingServerLauncher) = BaseSpaceServer(
    installSpace = { installedThenStarted += "install" },
    launcher = launcher,
    log = recordingLog,
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
      return ServerConnection(baseUrl = "http://127.0.0.1:8888/", port = 8888, token = "t")
    }

    override suspend fun stop() = Unit

    override fun isRunning(): Boolean = startCount > if (diesAfterFirstStart) 1 else 0
  }
}
