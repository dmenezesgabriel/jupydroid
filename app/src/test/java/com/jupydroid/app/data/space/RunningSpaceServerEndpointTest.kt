package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeServerLauncher
import com.jupydroid.extension.api.ServerAddress
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What an extension is handed for the entered Space's server (ADR-0104). */
class RunningSpaceServerEndpointTest {
  @Test
  fun theAddressIsTheRunningServersOwn() = runTest {
    val endpoint = RunningSpaceServerEndpoint(clientTestSpaceServer(FakeServerLauncher()))

    assertEquals(ServerAddress(baseUrl = "http://127.0.0.1:8888", token = ""), endpoint.address())
  }

  /** An extension asks when it is about to send, so a Space that is not up is brought up then. */
  @Test
  fun askingStartsAServerThatIsNotRunning() = runTest {
    val launcher = FakeServerLauncher()

    RunningSpaceServerEndpoint(clientTestSpaceServer(launcher)).address()

    assertTrue(launcher.isRunning())
  }
}
