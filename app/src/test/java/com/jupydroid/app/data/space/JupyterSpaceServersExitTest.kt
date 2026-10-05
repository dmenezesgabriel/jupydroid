package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * **A Space reads Stopped once its server process exits, whoever ended it.** The server here stops
 * the way an idle `jupyter_server` does as far as the app can see: its process goes, and nothing in
 * the app asked. Real processes under [FakeProotScript], so the exit is the OS's, not a flag's.
 */
class JupyterSpaceServersExitTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val watchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val root by lazy { spaceDirIn(tempFolder.newFolder("spaces"), SPACE.id).apply { mkdirs() } }
  private val nativeDir by lazy { FakeProotScript.installIn(tempFolder.newFolder("native")) }

  @After
  fun stopWatching() = watchScope.cancel()

  @Test
  fun aServerThatExitsOnItsOwnLeavesRunning() = runBlocking<Unit> {
    FakeJupyterServerScript.listening(root, port = 1)
    val servers = JupyterSpaceServers { _, reportRunning ->
      SpaceServer(SPACE, installSpace = {}, launcher(), watchScope, reportRunning)
    }
    servers.run(SPACE.id)
    assertEquals(setOf(SPACE.id), servers.running.value)

    ProcessHandle.of(serverPid()).ifPresent { it.destroyForcibly() }

    withTimeout(EXIT_TIMEOUT) { servers.running.first { it.isEmpty() } }
  }

  private fun launcher() = JupyterServerLauncher(
    runtimeFor = { SpaceRuntime(root, nativeDir) },
    dirsFor = { SpaceDirs(root) },
    startTimeout = EXIT_TIMEOUT,
  )

  private fun serverPid(): Long = File(root, "run/pid").readText().trim().toLong()

  private companion object {
    val SPACE = Space(id = "space-1", name = "One", kernels = emptyList())
    val EXIT_TIMEOUT = 10.seconds
  }
}
