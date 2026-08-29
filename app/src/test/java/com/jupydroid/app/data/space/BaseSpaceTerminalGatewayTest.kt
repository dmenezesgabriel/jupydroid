package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What this class adds over [RealTerminalGateway]: which calls may start a Space and which may not.
 *
 * The rules are P1b's and P1c's rather than the terminal protocol's, which is why they are asserted
 * here and not in the contract - a gateway with no server under it has no such distinction to make.
 */
class BaseSpaceTerminalGatewayTest {
  @Test
  fun open_startsTheSpaceBecauseAskingForATerminalIsAskingForTheRuntime() = runTest {
    val launcher = RecordingServerLauncher()

    gateway(launcher).open(cwd = null)

    assertEquals(1, launcher.startCount)
  }

  /**
   * **Listing must not boot a runtime**, which is the one call a screen makes before the user has
   * asked for anything.
   *
   * Seven seconds and ~130 MB to answer a question whose honest answer is "none"
   * (`evidence/p1c-app-death-backstop/`): a server that is not running holds no terminals, and a
   * freshly started one would have said the same.
   */
  @Test
  fun running_startsNoServerAndAnswersWithNothing() = runTest {
    val launcher = RecordingServerLauncher()

    val terminals = gateway(launcher).running()

    assertEquals(emptyList<TerminalSession>(), terminals)
    assertEquals("listing a stopped server must not start one", 0, launcher.startCount)
  }

  @Test
  fun running_listsTheTerminalsOfAServerThatIsUp() = runTest {
    val gateway = gateway(RecordingServerLauncher())
    val session = gateway.open(cwd = null)

    assertTrue("expected $session in ${gateway.running()}", session in gateway.running())
  }

  /**
   * Closing a terminal after the backstop stopped the server does nothing, rather than starting one.
   *
   * [BaseSpaceKernelGateway]'s rule for `shutdown`: the shell died with the server, so there is
   * nothing left to end and booting a runtime to say so would undo what P1c did.
   */
  @Test
  fun close_startsNoServerForATerminalTheStoppedOneHeld() = runTest {
    val launcher = RecordingServerLauncher(diesAfterFirstStart = true)
    val gateway = gateway(launcher)
    val stranded = gateway.open(cwd = null)

    gateway.close(stranded)

    assertEquals("closing a dead terminal must not start a server", 1, launcher.startCount)
  }

  private fun gateway(launcher: ServerLauncher): TerminalGateway = BaseSpaceTerminalGateway(
    server = BaseSpaceServer(installSpace = {}, launcher = launcher),
    gatewayFor = { FakeTerminalGateway() },
  )

  private class RecordingServerLauncher(
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
}
