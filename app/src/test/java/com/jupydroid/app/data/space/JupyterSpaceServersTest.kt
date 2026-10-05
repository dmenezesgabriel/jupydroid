package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeServerLauncher
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.SpaceServers
import com.jupydroid.app.domain.port.SpaceServersContract
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class JupyterSpaceServersTest : SpaceServersContract() {
  private val built = mutableListOf<String>()

  override fun TestScope.servers(): SpaceServers = jupyterSpaceServers()

  /**
   * One per Space, and the same one every time: base's clients hold base's entry, and a second
   * entry would be a second launcher whose `start` knows nothing of the first one's server.
   */
  @Test
  fun server_isBuiltOncePerSpace() = runTest {
    val servers = jupyterSpaceServers()

    assertSame(servers.server("base"), servers.server("base"))
    servers.server("space-1")

    assertEquals(listOf("base", "space-1"), built)
  }

  /** A start from a cell goes through base's entry, not through [SpaceServers.run], and shows. */
  @Test
  fun aStartThroughASpacesOwnEntryReadsRunning() = runTest {
    val servers = jupyterSpaceServers()

    servers.server("base").connection()

    assertEquals(setOf("base"), servers.running.value)
  }

  @Test
  fun stop_neverBuildsAServerOnlyToStopIt() = runTest {
    jupyterSpaceServers().stop("space-1")

    assertEquals(emptyList<String>(), built)
  }

  private fun TestScope.jupyterSpaceServers() = JupyterSpaceServers { id, reportRunning ->
    built += id
    SpaceServer(
      space = Space(id = id, name = id, kernels = emptyList()),
      installSpace = {},
      launcher = FakeServerLauncher(),
      watchScope = backgroundScope,
      reportRunning = reportRunning,
    )
  }
}
