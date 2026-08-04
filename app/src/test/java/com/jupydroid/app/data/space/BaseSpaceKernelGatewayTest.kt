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

    gateway.connect(SPACE)

    assertEquals(listOf("install", "start"), installedThenStarted)
    assertEquals(1, launcher.startCount)
  }

  /** Starting the server takes seconds; doing it per cell would pay that on every run. */
  @Test
  fun connect_startsTheServerOnceHoweverManyTimesItIsUsed() = runTest {
    val launcher = RecordingServerLauncher()
    val gateway = gateway(launcher)

    gateway.connect(SPACE)
    gateway.connect(SPACE)
    gateway.execute(gateway.connect(SPACE), "c1", "2 + 2").toList()

    assertEquals(1, launcher.startCount)
  }

  /**
   * Two cells run at once would otherwise each see no server and each start one. The second
   * server would bind a different port and the first would be orphaned.
   */
  @Test
  fun connect_startsOneServerEvenWhenTwoCallersRaceForIt() = runTest {
    val launcher = RecordingServerLauncher()
    val gateway = gateway(launcher)

    val first = async { gateway.connect(SPACE) }
    val second = async { gateway.connect(SPACE) }
    first.await()
    second.await()

    assertEquals(1, launcher.startCount)
  }

  @Test
  fun connect_surfacesAServerThatWillNotStart() = runTest {
    val gateway = gateway(RecordingServerLauncher(failWith = IllegalStateException("port in use")))

    val failure = runCatching { gateway.connect(SPACE) }

    assertTrue("expected the failure to surface, got $failure", failure.isFailure)
  }

  private val installedThenStarted = mutableListOf<String>()

  private fun gateway(launcher: RecordingServerLauncher): KernelGateway = BaseSpaceKernelGateway(
    installSpace = { installedThenStarted += "install" },
    launcher = launcher,
    gatewayFor = { FakeKernelGateway() },
  )

  private inner class RecordingServerLauncher(private val failWith: Exception? = null) : ServerLauncher {
    var startCount = 0
      private set

    override suspend fun start(space: Space): ServerConnection {
      failWith?.let { throw it }
      installedThenStarted += "start"
      startCount++
      return ServerConnection(baseUrl = "http://127.0.0.1:8888/", port = 8888, token = "t")
    }

    override suspend fun stop() = Unit

    override fun isRunning(): Boolean = startCount > 0
  }

  private companion object {
    val SPACE = Space(id = "base", name = "Base", kernels = emptyList())
  }
}
