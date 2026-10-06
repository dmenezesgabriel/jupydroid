package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.SpaceSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * **`stop()` asks the server to stop through `POST api/shutdown`**, because under proot no signal
 * stops a Space: proot ignores SIGTERM, and SIGKILL orphans what it traces
 * (`TwoSpacesAtOnceDeviceTest`).
 *
 * [FakeJupyterServerScript.ignoringSigterm] plays that server: deaf to `Process.destroy()`, gone
 * once [ShutdownLikeJupyterServer] has answered `api/shutdown`. Real processes, so whether one is
 * still alive is asked of the OS by pid rather than of the launcher.
 */
class JupyterServerLauncherStopTest {
  @get:Rule val tempFolder = TemporaryFolder()

  @get:Rule val serverRule = MockWebServerRule()

  private val spacesDir by lazy { tempFolder.newFolder("spaces") }
  private val nativeDir by lazy { FakeProotScript.installIn(tempFolder.newFolder("native")) }
  private val space = Space(id = "space-1", name = "One", kernels = emptyList())
  private val runDir by lazy { File(spaceDirIn(spacesDir, space.id), "run") }
  private val log = RecordingSpaceLifecycleLog()
  private val pidsSeen = mutableListOf<Long>()

  @After
  fun killWhatTheTestLeft() = pidsSeen.forEach { pid -> ProcessHandle.of(pid).ifPresent { it.destroyForcibly() } }

  @Test
  fun stop_endsAServerThatIgnoresSigterm() = runBlocking {
    val launcher = launcher(shutsDown = true)
    launcher.start(space)
    val pid = serverPid()

    launcher.stop()

    assertFalse("the server outlived stop()", isAlive(pid))
    assertFalse(launcher.isRunning())
  }

  @Test
  fun stop_postsApiShutdownWithTheServersToken() = runBlocking {
    val launcher = launcher(shutsDown = true)
    val connection = launcher.start(space)

    launcher.stop()

    val request = serverRule.server.takeRequest(TIMEOUT_SECONDS, TimeUnit.SECONDS)!!
    assertEquals("POST", request.method)
    assertEquals("/api/shutdown", request.target)
    assertEquals("token ${connection.token}", request.headers["Authorization"])
  }

  @Test
  fun start_stopsTheServerItReplaces() = runBlocking {
    val launcher = launcher(shutsDown = true)
    launcher.start(space)
    val first = serverPid()

    launcher.start(space)

    assertFalse("the replaced server is still running beside the new one", isAlive(first))
    launcher.stop()
  }

  @Test
  fun stop_logsAndKeepsAServerThatOutlivesTheTimeout() = runBlocking {
    val launcher = launcher(shutsDown = false, stopTimeout = 300.milliseconds)
    launcher.start(space)
    val pid = serverPid()

    launcher.stop()

    assertTrue(isAlive(pid))
    assertTrue("isRunning() must not forget a server that is still there", launcher.isRunning())
    assertEquals("one failure, for the server that outlived its stop", 1, log.failures.size)
  }

  /** What lets a Space's row flip when its server stops itself: the exit is waited on, not polled. */
  @Test
  fun awaitExit_returnsOnceTheServerProcessHasExited() = runBlocking {
    val launcher = launcher(shutsDown = true)
    launcher.start(space)
    val pid = serverPid()
    val exited = async(Dispatchers.IO) { launcher.awaitExit() }

    File(runDir, "shut-down").createNewFile() // the server leaves on its own, as an idle one does
    withTimeout(TIMEOUT_SECONDS.seconds) { exited.await() }

    assertFalse(isAlive(pid))
  }

  @Test
  fun awaitExit_returnsAtOnceWhenNoServerWasStarted() = runBlocking {
    withTimeout(TIMEOUT_SECONDS.seconds) { launcher(shutsDown = true).awaitExit() }
  }

  private fun launcher(shutsDown: Boolean, stopTimeout: Duration = 10.seconds): JupyterServerLauncher {
    val root = spaceDirIn(spacesDir, space.id).apply { mkdirs() }
    File(root, "config.json").writeText("{}")
    FakeJupyterServerScript.ignoringSigterm(root, serverRule.server.port)
    serverRule.server.dispatcher = ShutdownLikeJupyterServer(runDir.takeIf { shutsDown })
    return JupyterServerLauncher(
      runtimeFor = { _, _ -> SpaceRuntime(root, nativeDir) },
      dirsFor = { SpaceDirs(root) },
      settingsFor = { SpaceSettings() },
      startTimeout = 10.seconds,
      stopTimeout = stopTimeout,
      log = log,
    )
  }

  private fun serverPid(): Long = File(runDir, "pid").readText().trim().toLong().also { pidsSeen += it }

  private fun isAlive(pid: Long): Boolean = ProcessHandle.of(pid).map { it.isAlive }.orElse(false)

  private companion object {
    const val TIMEOUT_SECONDS = 5L
  }
}

/**
 * Answers `POST api/shutdown` the way jupyter_server does, and then, standing in for the server's
 * exit, writes `shut-down` into [runDir] for the fake to see. A null [runDir] is a server that
 * answers and never stops.
 */
private class ShutdownLikeJupyterServer(private val runDir: File?) : Dispatcher() {
  override fun dispatch(request: RecordedRequest): MockResponse {
    if (request.method == "POST" &&
      request.target == "/api/shutdown"
    ) {
      runDir?.let { File(it, "shut-down").createNewFile() }
    }
    return MockResponse(code = 200)
  }
}
