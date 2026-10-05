package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
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

  /**
   * Killed by pid rather than through `stop()`: nothing answers `api/shutdown` at the port these
   * fakes report, so `stop()` would wait out its whole timeout. `JupyterServerLauncherStopTest`
   * is what tests `stop()`.
   */
  @After
  fun killTheServer() {
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
      runtimeFor = { space -> SpaceRuntime(spaceDirIn(spacesDir, space.id), nativeDir) },
      dirsFor = { space -> SpaceDirs(spaceDirIn(spacesDir, space.id)) },
      startTimeout = 10.seconds,
    )
  }
}
