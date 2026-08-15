package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BaseSpaceKernelGatewayTest {
  @Test
  fun connect_installsTheSpaceAndStartsTheServerBeforeTalkingToAKernel() = runTest {
    val launcher = RecordingServerLauncher()
    val gateway = gateway(launcher)

    gateway.connect(SPACE, "Analysis.ipynb")

    assertEquals(listOf("install", "start"), installedThenStarted)
    assertEquals(1, launcher.startCount)
  }

  /** Starting the server takes seconds; doing it per cell would pay that on every run. */
  @Test
  fun connect_startsTheServerOnceHoweverManyTimesItIsUsed() = runTest {
    val launcher = RecordingServerLauncher()
    val gateway = gateway(launcher)

    gateway.connect(SPACE, "Analysis.ipynb")
    gateway.connect(SPACE, "Analysis.ipynb")
    gateway.execute(gateway.connect(SPACE, "Analysis.ipynb"), "c1", "2 + 2").toList()

    assertEquals(1, launcher.startCount)
  }

  /**
   * P1a: a server that has stopped must not hand the caller a gateway that talks to a
   * dead port. The next connect starts a fresh server instead of reusing the stale one.
   */
  @Test
  fun connect_startsAFreshServerWhenThePreviousOneIsNoLongerRunning() = runTest {
    val launcher = RecordingServerLauncher(diesAfterStart = true)
    val gateway = gateway(launcher)

    gateway.connect(SPACE, "Analysis.ipynb")
    gateway.connect(SPACE, "Analysis.ipynb")

    assertEquals(2, launcher.startCount)
  }

  /**
   * Two cells run at once would otherwise each see no server and each start one. The second
   * server would bind a different port and the first would be orphaned.
   */
  @Test
  fun connect_startsOneServerEvenWhenTwoCallersRaceForIt() = runTest {
    val launcher = RecordingServerLauncher()
    val gateway = gateway(launcher)

    val first = async { gateway.connect(SPACE, "Analysis.ipynb") }
    val second = async { gateway.connect(SPACE, "Analysis.ipynb") }
    first.await()
    second.await()

    assertEquals(1, launcher.startCount)
  }

  @Test
  fun connect_surfacesAServerThatWillNotStart() = runTest {
    val gateway = gateway(RecordingServerLauncher(failWith = IllegalStateException("port in use")))

    val failure = runCatching { gateway.connect(SPACE, "Analysis.ipynb") }

    assertTrue("expected the failure to surface, got $failure", failure.isFailure)
  }

  /** The app was silent when the first real-kernel run produced nothing; this is why. */
  @Test
  fun connect_reportsEachStageItReachedSoAFailureNamesOne() = runTest {
    gateway(RecordingServerLauncher()).connect(SPACE, "Analysis.ipynb")

    val stages = recordingLog.stages
    assertTrue("expected the server url to be logged, got $stages", stages.any { it.contains("127.0.0.1:8888") })
    assertTrue("expected the kernel to be logged, got $stages", stages.any { it.contains("kernel") })
  }

  @Test
  fun connect_logsTheStageItFailedAtRatherThanFailingSilently() = runTest {
    val gateway = gateway(RecordingServerLauncher(failWith = IllegalStateException("port in use")))

    runCatching { gateway.connect(SPACE, "Analysis.ipynb") }

    assertTrue("expected the failing stage to be logged", recordingLog.failures.isNotEmpty())
  }

  private val installedThenStarted = mutableListOf<String>()
  private val recordingLog = RecordingSpaceLifecycleLog()

  private fun gateway(launcher: RecordingServerLauncher): KernelGateway = BaseSpaceKernelGateway(
    installSpace = { installedThenStarted += "install" },
    launcher = launcher,
    gatewayFor = { FakeKernelGateway() },
    log = recordingLog,
  )

  private inner class RecordingServerLauncher(
    private val failWith: Exception? = null,
    /** Simulates the server process dying between connects. */
    private val diesAfterStart: Boolean = false,
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

    override fun isRunning(): Boolean = !diesAfterStart && startCount > 0
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
