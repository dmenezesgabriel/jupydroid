package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.SpaceSettings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * **`start(space)` launches the Space it is handed**, not a Space baked in at construction: the
 * server's runtime dir, root, cwd and log all come from that Space's own directory, provisioned on
 * the spot from a directory holding nothing but `config.json`.
 *
 * Real processes, real files: [FakeProotScript] stands in for proot and [FakeJupyterServerScript]
 * for the Space's interpreter, so what is asserted is what the launcher did to the filesystem and
 * the process, not what it meant to do.
 * `runBlocking`, not `runTest`: the launcher polls with `delay`, which virtual time would skip
 * straight past a script that has not written its file yet.
 */
class JupyterServerLauncherSpaceTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val spacesDir by lazy { tempFolder.newFolder("spaces") }
  private val baseDir by lazy { spaceDirIn(spacesDir, "base").apply { mkdirs() } }
  private val nativeDir by lazy { FakeProotScript.installIn(tempFolder.newFolder("native")) }
  private val spaceOne = Space(id = "space-1", name = "One", kernels = emptyList())

  /** What the launcher's `settingsFor` answers right now - a test changes it between starts. */
  private var spaceOneSettings = SpaceSettings()

  /**
   * Killed by pid rather than through `stop()`: nothing answers `api/shutdown` at the port these
   * fakes report, so `stop()` would wait out its whole timeout. `JupyterServerLauncherStopTest`
   * is what tests `stop()`.
   */
  @After
  fun killTheServer() {
    killSpaceOnesServer()
  }

  /**
   * A setting is read **at each start**, never at construction: a launcher lives for the process,
   * and a Space configured on the home after its first run must start differently next time. Both
   * of a Space's start paths - the home's Run and a cell's restart after an idle stop - go through
   * `start`, so this is the one place both are held to it.
   */
  @Test
  fun eachStartReadsTheSpacesIdleShutdownAsItIsThen() = runBlocking {
    val launcher = launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }
    spaceOneSettings = SpaceSettings(idleShutdownMinutes = 2)
    launcher.start(spaceOne)
    val first = serverArgs()
    killSpaceOnesServer()

    spaceOneSettings = SpaceSettings(idleShutdownMinutes = 0)
    launcher.start(spaceOne)

    assertTrue(first.toString(), "--ServerApp.shutdown_no_activity_timeout=120" in first)
    assertTrue(serverArgs().toString(), "--ServerApp.shutdown_no_activity_timeout=0" in serverArgs())
  }

  /**
   * The Space's own variables reach the server as they are stored at that start - and through it
   * every kernel and terminal, which `jupyter_client` and terminado start from a copy of the
   * server's `os.environ`.
   */
  @Test
  fun eachStartGivesTheServerTheSpacesVariablesAsTheyAreThen() = runBlocking {
    val launcher = launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }
    spaceOneSettings = SpaceSettings(environmentVariables = mapOf("GREETING" to "hello"))
    launcher.start(spaceOne)
    val first = serverEnv()
    killSpaceOnesServer()

    spaceOneSettings = SpaceSettings(environmentVariables = mapOf("GREETING" to "bye"))
    launcher.start(spaceOne)

    assertTrue(first.toString(), "GREETING=hello" in first)
    assertTrue(serverEnv().toString(), "GREETING=bye" in serverEnv())
  }

  private fun serverArgs(): List<String> = File(spaceOneDir(), "run/args").readLines()

  private fun serverEnv(): List<String> = File(spaceOneDir(), "run/env").readLines()

  private fun killSpaceOnesServer() {
    File(spaceOneDir(), "run/pid").takeIf { it.isFile }?.readText()?.trim()?.toLong()
      ?.let { pid -> ProcessHandle.of(pid).ifPresent { it.destroyForcibly() } }
  }

  @Test
  fun start_connectsToTheServerTheSpaceItWasHandedReported() = runBlocking {
    val connection = launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }.start(spaceOne)

    assertEquals(4321, connection.port)
    assertTrue(File(spaceOneDir(), "run/jpserver-1.json").isFile)
    assertFalse("base's run/ must stay untouched", File(baseDir, "run").exists())
  }

  @Test
  fun start_provisionsASpaceThatHoldsOnlyItsConfig() = runBlocking {
    launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }.start(spaceOne)

    listOf("home/jupydroid", "tmp", "pkgs").forEach { relative ->
      assertTrue("expected $relative under ${spaceOneDir()}", File(spaceOneDir(), relative).isDirectory)
    }
  }

  @Test
  fun start_runsTheServerInTheSpacesOwnWorkspace() = runBlocking {
    launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }.start(spaceOne)

    assertEquals("/home/jupydroid", File(spaceOneDir(), "run/cwd").readText().trim())
  }

  @Test
  fun start_reportsAFailureFromTheSpacesOwnLog() {
    val dying = launcherWith { FakeJupyterServerScript.dying(it, last = "space-1 went down") }

    val error = assertThrows(IllegalStateException::class.java) { runBlocking { dying.start(spaceOne) } }

    assertTrue(error.message!!, "space-1 went down" in error.message!!)
  }

  private fun spaceOneDir(): File = spaceDirIn(spacesDir, spaceOne.id).apply {
    mkdirs()
    File(this, "config.json").takeUnless { it.exists() }?.writeText("{}")
  }

  /** [installInterpreter] puts the fake server into space-1's root, where the launcher runs it. */
  private fun launcherWith(installInterpreter: (File) -> File): JupyterServerLauncher {
    installInterpreter(spaceOneDir())
    return JupyterServerLauncher(
      runtimeFor = { space, settings ->
        SpaceRuntime(spaceDirIn(spacesDir, space.id), nativeDir, variables = settings.environmentVariables)
      },
      dirsFor = { space -> SpaceDirs(spaceDirIn(spacesDir, space.id)) },
      settingsFor = { spaceOneSettings },
      startTimeout = 10.seconds,
    )
  }
}
