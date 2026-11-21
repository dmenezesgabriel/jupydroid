package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.model.spaceProcessVariables
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

  /** What the launcher's `defaultKernelFor` answers right now - a test changes it between starts. */
  private var spaceOneDefaultKernel = "python3"

  /** What the launcher's `mcpPortFor` answers right now - a test changes it between starts. */
  private var spaceOneMcpPort = 41234

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

  /** The default is read at each start, as the settings are: Settings may pick another while stopped. */
  @Test
  fun eachStartGivesTheServerTheSpacesDefaultKernelAsItIsThen() = runBlocking {
    val launcher = launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }
    launcher.start(spaceOne)
    val first = serverArgs()
    killSpaceOnesServer()

    spaceOneDefaultKernel = "stats"
    launcher.start(spaceOne)

    assertTrue(first.toString(), "--MappingKernelManager.default_kernel_name=python3" in first)
    assertTrue(serverArgs().toString(), "--MappingKernelManager.default_kernel_name=stats" in serverArgs())
  }

  /**
   * A port is picked at each start, never once per launcher: two Spaces run at once, and a port
   * fixed for both fails the second one's bind.
   */
  @Test
  fun eachStartGivesTheMcpServerThePortPickedThen() = runBlocking {
    val launcher = launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }
    launcher.start(spaceOne)
    val first = serverArgs()
    killSpaceOnesServer()

    spaceOneMcpPort = 41235
    launcher.start(spaceOne)

    assertTrue(first.toString(), "--MCPExtensionApp.mcp_port=41234" in first)
    assertTrue(serverArgs().toString(), "--MCPExtensionApp.mcp_port=41235" in serverArgs())
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

  /**
   * The cap reaches the server as both names OpenBLAS reads, and no cap as neither - beside the
   * derived set rather than clobbered by it.
   */
  @Test
  fun aThreadCapGivesTheServerBothNamesAndNoCapGivesNeither() = runBlocking {
    val launcher = launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }
    spaceOneSettings = SpaceSettings(threadCap = 1)
    launcher.start(spaceOne)
    val capped = serverEnv()
    killSpaceOnesServer()

    spaceOneSettings = SpaceSettings()
    launcher.start(spaceOne)

    assertTrue(capped.toString(), "OPENBLAS_NUM_THREADS=1" in capped && "OMP_NUM_THREADS=1" in capped)
    assertTrue(serverEnv().toString(), serverEnv().none { it.startsWith("OPENBLAS_NUM_THREADS=") })
    assertTrue(serverEnv().toString(), serverEnv().none { it.startsWith("OMP_NUM_THREADS=") })
  }

  /**
   * The script runs **before** the server - no runtime file exists yet - from the workspace, with
   * the Space's own variables, which is what lets it prepare what the server and kernels then find.
   */
  @Test
  fun aStartupScriptRunsBeforeTheServerInTheWorkspaceWithTheSpacesVariables() = runBlocking {
    val launcher = launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }
    spaceOneSettings = SpaceSettings(
      environmentVariables = mapOf("GREETING" to "hello"),
      startupScript = """echo "${'$'}GREETING" > startup.txt
        |[ -e "${'$'}FAKE_PROOT_ROOT${'$'}JUPYTER_RUNTIME_DIR/jpserver-1.json" ] && echo late >> startup.txt
        |exit 0
      """.trimMargin(),
    )

    launcher.start(spaceOne)

    assertEquals("hello", File(spaceOneDir(), "home/jupydroid/startup.txt").readText().trim())
  }

  /** A failed script fails the start in words the home shows: its status and what it printed last. */
  @Test
  fun aFailingStartupScriptFailsTheStartWithItsStatusAndOutputAndStartsNoServer() {
    val launcher = launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }
    spaceOneSettings = SpaceSettings(startupScript = "echo nope\nexit 3")

    val error = assertThrows(IllegalStateException::class.java) { runBlocking { launcher.start(spaceOne) } }

    assertTrue(error.message!!, "status 3" in error.message!! && "nope" in error.message!!)
    assertFalse("no server may start after a failed script", File(spaceOneDir(), "run/pid").exists())
  }

  @Test
  fun aBlankStartupScriptRunsNothing() = runBlocking {
    launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }.start(spaceOne)

    assertFalse(File(spaceOneDir(), "run/startup.log").exists())
  }

  private fun serverArgs(): List<String> = File(spaceOneDir(), "run/args").readLines()

  private fun serverEnv(): List<String> = File(spaceOneDir(), "run/env").readLines()

  private fun killSpaceOnesServer() {
    File(spaceOneDir(), "run/pid").takeIf { it.isFile }?.readText()?.trim()?.toLong()
      ?.let { pid -> ProcessHandle.of(pid).ifPresent { it.destroyForcibly() } }
  }

  /** A killed server removes nothing, so a start begins from an empty `run/` (ADR-0128). */
  @Test
  fun start_clearsWhatAKilledServerLeftInRun() = runBlocking {
    val launcher = launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }
    val stale = listOf("jpserver-9-open.html", "jpserver-mcp-9.json", "kernel-abc.json")
      .map { File(spaceOneDir(), "run/$it").apply { parentFile!!.mkdirs() }.also { f -> f.writeText("stale") } }

    launcher.start(spaceOne)

    assertEquals(emptyList<File>(), stale.filter(File::exists))
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

    listOf("home/jupydroid", "tmp", "pkgs", "pkgs-gnu").forEach { relative ->
      assertTrue("expected $relative under ${spaceOneDir()}", File(spaceOneDir(), relative).isDirectory)
    }
  }

  /** A Space extracted before the file existed gets it on its next start, not only at creation. */
  @Test
  fun start_writesTheSpacesPipConf() = runBlocking {
    launcherWith { FakeJupyterServerScript.listening(it, port = 4321) }.start(spaceOne)

    assertTrue(File(spaceOneDir(), "usr/pip.conf").isFile)
  }

  /**
   * glibc's resolver reads `etc/resolv.conf`, which musl's cannot be redirected to - the file is
   * written on every start so a network change reaches the next kernel, the same freshness the
   * nameserver snapshot gives musl's (ADR-0166).
   */
  @Test
  fun start_writesTheSpacesGnuResolvConf() = runBlocking {
    launcherWith(dnsServers = listOf("8.8.8.8")) { FakeJupyterServerScript.listening(it, port = 4321) }.start(spaceOne)

    assertEquals("nameserver 8.8.8.8\n", File(spaceOneDir(), "etc/resolv.conf").readText())
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
  private fun launcherWith(
    dnsServers: List<String> = emptyList(),
    installInterpreter: (File) -> File,
  ): JupyterServerLauncher {
    installInterpreter(spaceOneDir())
    FakeSpaceShellScript.installIn(spaceOneDir())
    return JupyterServerLauncher(
      runtimeFor = { space, settings ->
        SpaceRuntime(
          spaceDirIn(spacesDir, space.id),
          nativeDir,
          dnsServers = DnsServerSource { dnsServers },
          variables = settings.spaceProcessVariables(),
        )
      },
      dirsFor = { space -> SpaceDirs(spaceDirIn(spacesDir, space.id)) },
      settingsFor = { spaceOneSettings },
      defaultKernelFor = { spaceOneDefaultKernel },
      mcpPortFor = { spaceOneMcpPort },
      startTimeout = 10.seconds,
    )
  }
}
