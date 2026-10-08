package com.jupydroid.app.data.space

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.seconds

/**
 * **A Space is its own system**: with an image in its own directory, its server, kernel and
 * language server run under proot with that directory as `/` (ADR-0073).
 *
 * `space-1` is seeded the way creating a Space leaves it - the image extracted into
 * `files/spaces/space-1`, plus the `config.json` `DiskSpaceRepository.create` writes - and then
 * driven through the same composition the app uses: [spaceServerLauncher] for the server, kernel
 * and terminals, [startPylsp] for the language server, [spaceRuntime] for a shell. Each is asked,
 * from inside, where it writes; every answer has to be space-1's own name inside its root, never
 * base's.
 *
 * In the app process, where the seccomp filter applies - see [SpaceProcessRun] for why `run-as`
 * would answer a different question. `Assume`-skipped where no rootfs ships.
 *
 * ```
 * adb shell am instrument -w -e class com.jupydroid.app.data.space.PerSpaceRuntimeDeviceTest \
 *   com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
@RunWith(AndroidJUnit4::class)
class PerSpaceRuntimeDeviceTest {
  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no Space to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
    check(spaceOneImage) { "space-1's image did not land" }
  }

  /** The server boots out of the Space's own image, and logs into that Space's own `run/`. */
  @Test
  fun aSpaceWithItsOwnImageBootsItsOwnServer() {
    assertTrue("space-1's server reported no port", spaceOneServer.port > 0)
    assertTrue("no server log in ${spaceOneDirs.runtime}", jupyterServerLogFile(spaceOneDirs.runtime).isFile)
  }

  /** Its runtime file is in its own `run/`, and base's `run/` advertises some other server or none. */
  @Test
  fun theServerAnswersAndAdvertisesItselfOnlyInItsOwnRuntimeDir() {
    assertEquals(HTTP_OK, spaceOneServer.get("api/status").first)
    assertEquals(spaceOneServer.port, JupyterRuntimeFile.read(spaceOneDirs.runtime)?.port)
    assertNotEquals(spaceOneServer.port, JupyterRuntimeFile.read(File(baseDir, "run"))?.port)
  }

  /**
   * A kernel on that server sees the Space the way a process inside it does: `HOME`, the temp
   * dir, the user base and the runtime dir all name space-1's own directories *inside the root*,
   * the interpreter is the image's own, and the connection file lands in space-1's `run/`.
   */
  @Test(timeout = 120_000)
  fun aKernelOnThatServerSeesTheSpaceAsItsOwnRoot() {
    val placed = Json.parseToJsonElement(runInSpaceOneKernel(KERNEL_PROBE)).jsonObject
    Log.i(SPACE_LOG_TAG, "space-1 kernel: $placed")

    val expected = mapOf(
      "home" to "/home/jupydroid",
      "tmp" to "/tmp",
      "user_base" to "/pkgs",
      "runtime_dir" to "/run",
      "executable" to "/usr/bin/python3",
    )
    expected.forEach { (key, inRoot) ->
      assertEquals("$key names outside space-1's root", inRoot, placed.getValue(key).jsonPrimitive.content)
    }
    val connectionFileSeen = placed.getValue("connection_file_seen").jsonPrimitive.content.toBoolean()
    assertTrue("no kernel connection file in ${spaceOneDirs.runtime}", connectionFileSeen)
  }

  /** The terminals service answers on space-1's server, so a shell can be spawned rooted there. */
  @Test
  fun theSpacesServerOffersTerminals() {
    assertEquals(HTTP_OK, spaceOneServer.get("api/terminals").first)
  }

  /** A shell process in space-1's root resolves space-1's user base, and nothing was refused. */
  @Test
  fun aShellInTheSpaceResolvesItsOwnUserBase() {
    val run = runInSpace(
      SPACE_SHELL,
      "-c",
      "python3 -c 'import site; print(site.USER_BASE)'",
      runtime = spaceRuntime(context, SPACE_ONE),
    )

    assertEquals("${run.command} exited ${run.exitCode}: ${run.output}", 0, run.exitCode)
    assertEquals("/pkgs", run.output.trim())
    assertEquals(emptyList<String>(), seccompRefusalsIn(spaceOneDir))
  }

  /** `pylsp` started for space-1, as the port starts it, serves a session and exits cleanly. */
  @Test(timeout = 120_000)
  fun theSpacesLanguageServerServes() {
    val process = startPylsp(spaceRuntime(context, SPACE_ONE), spaceOneDirs)

    LspStdioSession(process).use { session ->
      assertTrue("no capabilities; stderr: ${session.stderr}", "\"capabilities\"" in session.initialize())
      assertEquals("pylsp did not exit 0; stderr: ${session.stderr}", 0, session.shutdownAndExit())
    }
  }

  /**
   * Base is unaffected by space-1 existing - or running. Asked through [BaseSpaceUnderTest], the
   * one owner of base's `run/` in this process, rather than a second launcher that would clear its
   * runtime file.
   */
  @Test
  fun baseStillRunsBesideIt() {
    assertTrue("space-1's server reported no port", spaceOneServer.port > 0)
    val base = runBlocking { BaseSpaceUnderTest.require().connection() }.connection

    assertEquals(HTTP_OK, base.get("api/status").first)
    assertEquals(base.port, JupyterRuntimeFile.read(File(baseDir, "run"))?.port)
    assertNotEquals(spaceOneServer.port, base.port)
  }

  /** Stdout of [code] run in a fresh kernel on space-1's server; the kernel is shut down after. */
  private fun runInSpaceOneKernel(code: String): String = runBlocking {
    val gateway = RealKernelGateway(
      KernelSockets(OkHttpKernelSocketOpener(spaceOneServer), OkHttpKernelLifecycle(spaceOneServer)),
    )
    val session = gateway.connect(SPACE_ONE, "per-space-probe.ipynb")
    try {
      withTimeout(KERNEL_TIMEOUT) {
        gateway.execute(session, "probe", code).takeWhile { it !is KernelEvent.Idle }
          .filterIsInstance<KernelEvent.Output>().map { it.output }.toList()
      }.filterIsInstance<CellOutput.Stream>().joinToString("") { it.text }
    } finally {
      gateway.shutdown(session)
    }
  }

  private companion object {
    const val HTTP_OK = 200
    val KERNEL_TIMEOUT = 60.seconds

    /** A Space nothing but this test knows of, so no other suite's state can be mistaken for it. */
    val SPACE_ONE = Space(id = "space-1", name = "Space 1", kernels = BASE_SPACE.kernels)

    /**
     * Where the kernel says it is, as JSON on stdout - and into `kernel-env.out` in its cwd, the
     * Space's workspace, so the driver can pull the same answer as evidence.
     */
    val KERNEL_PROBE = """
      import glob, json, os, site, sys, tempfile
      runtime_dir = os.environ["JUPYTER_RUNTIME_DIR"]
      placed = json.dumps({
          "executable": sys.executable, "home": os.environ["HOME"], "tmp": tempfile.gettempdir(),
          "user_base": site.USER_BASE, "runtime_dir": runtime_dir,
          "connection_file_seen": str(bool(glob.glob(os.path.join(runtime_dir, "kernel-*.json")))).lower(),
      })
      open("kernel-env.out", "w").write(placed)
      print(placed)
    """.trimIndent()

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val baseDir: File get() = baseSpaceDir(context)
    val spaceOneDir: File get() = spaceDirIn(spacesDir(context), SPACE_ONE.id)
    val spaceOneDirs: SpaceDirs get() = spaceDirs(context, SPACE_ONE)

    /** One launcher for the class: `JupyterServerLauncher.start` begins with `stop()`. */
    val spaceOneLauncher by lazy {
      spaceServerLauncher(context, settingsFor = { SpaceSettings() }, defaultKernelFor = { DEFAULT_KERNEL_ID })
    }

    /**
     * space-1's own image, extracted once for the class, with the kernelspec base's installer
     * writes too: the image's own spec launches stock `ipykernel_launcher`, which dies on
     * Android before it can serve (tools/rootfs/jupydroid_kernel.py). A server started before
     * this lands finds no `/usr/bin/python3` to run, which is why every test forces it.
     */
    val spaceOneImage: Boolean by lazy {
      spaceOneRoot.extract()
      seedConfigOnlySpace()
      runBlocking { SpaceKernelSpec.install(spaceOneDir) }
      true
    }

    val spaceOneRoot by lazy { PrivateRootUnderTest(context, SPACE_ONE.id) }

    val spaceOneServer: ServerConnection by lazy {
      check(spaceOneImage) { "space-1's image did not land" }
      runBlocking { spaceOneLauncher.start(SPACE_ONE) }
    }

    /** `config.json` only - what `DiskSpaceRepository.create` writes, and all its scan needs. */
    fun seedConfigOnlySpace() {
      spaceOneDir.mkdirs()
      val config = File(spaceOneDir, "config.json")
      if (!config.isFile) config.writeText("""{"name":"${SPACE_ONE.name}"}""")
    }

    @JvmStatic
    @AfterClass
    fun stopSpaceOneAndRemoveItsImage() {
      runBlocking { spaceOneLauncher.stop() }
      spaceOneRoot.delete()
    }

    /** `GET` [path] with token auth; the status and body. */
    fun ServerConnection.get(path: String): Pair<Int, String> {
      val url = baseUrl.toHttpUrl().newBuilder().addPathSegments(path).build()
      val request = Request.Builder().url(url).header("Authorization", "token $token").build()
      return OkHttpClient().newCall(request).execute().use { it.code to it.body.string() }
    }
  }
}
