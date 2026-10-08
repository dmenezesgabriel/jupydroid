package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.KernelSpecModels
import com.jupydroid.app.domain.port.KernelSpecs
import com.jupydroid.app.domain.port.KernelSpecsContract
import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.ServerLauncher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [KernelSpecsContract] against the base-Space adapter on a *stopped* server - the half that is
 * contractual either way. The live half is this file's own test: a running server answers from
 * `GET api/kernelspecs` instead of the catalogue it is handed for a stopped one.
 */
class BaseSpaceKernelSpecsTest : KernelSpecsContract() {
  /** The contract's own catalogue, on a server that is never up - the passive half. */
  override fun specs(): KernelSpecs = BaseSpaceKernelSpecs(sockets(NeverStartedLauncher), FakeKernelSpecs())

  @Test
  fun specs_onARunningServerAnswersWhatItReported() = runTest {
    val lifecycle = FakeKernelLifecycle(
      specs = KernelSpecModels(
        default = "xeus-python",
        specs = listOf(KernelSpec(name = "xeus-python", displayName = "XPython", language = "python")),
      ),
    )
    val sockets = sockets(OneStartLauncher(), lifecycle)
    sockets.get() // the server is up, the way a connect would have brought it up

    val models = BaseSpaceKernelSpecs(sockets, FakeKernelSpecs()).specs(SPACE_WITH_SPEC)

    assertEquals("xeus-python", models.default)
    assertEquals("XPython", models.specs.single().displayName)
  }

  /**
   * The whole point of the adapter: asking what a Space can run must never boot one - the same
   * rule [BaseSpaceKernelSessions]' `running` keeps.
   */
  @Test
  fun specs_onAStoppedServerAnswersTheStoppedCatalogueWithoutStartingAnything() = runTest {
    val launcher = OneStartLauncher()
    val onDisk = KernelSpecModels(default = "python3", specs = listOf(PYTHON_SPEC, STATS_SPEC))

    val models = BaseSpaceKernelSpecs(sockets(launcher), FakeKernelSpecs(onDisk)).specs(SPACE_WITH_SPEC)

    assertEquals(0, launcher.startCount)
    assertEquals(onDisk, models)
  }

  private fun sockets(launcher: ServerLauncher, lifecycle: FakeKernelLifecycle = FakeKernelLifecycle()) =
    PerRunningServer(server(launcher)) {
      KernelSockets(ScriptedKernelSocketOpener(reply = { emptyList() }), lifecycle)
    }

  private fun server(launcher: ServerLauncher) = clientTestSpaceServer(launcher)

  private inner class OneStartLauncher : ServerLauncher {
    var startCount = 0
      private set

    override suspend fun start(space: Space): ServerConnection {
      startCount++
      return ServerConnection(baseUrl = "http://127.0.0.1:8901", port = 8901, token = "t")
    }

    override suspend fun stop() = Unit

    override fun isRunning(): Boolean = startCount > 0

    override suspend fun awaitExit() = Unit
  }

  /** Never reports a live server - `liveOrNull` gives null, no start is possible. */
  private object NeverStartedLauncher : ServerLauncher {
    override suspend fun start(space: Space): ServerConnection =
      ServerConnection(baseUrl = "http://127.0.0.1:8901", port = 8901, token = "t")

    override suspend fun stop() = Unit

    override fun isRunning(): Boolean = false

    override suspend fun awaitExit() = Unit
  }

  private companion object {
    val SPACE_WITH_SPEC = Space(id = "base", name = "Base", kernels = listOf(PYTHON_SPEC))
    val STATS_SPEC = KernelSpec(name = "stats", displayName = "Python (stats)", language = "python")
  }
}
