package com.jupydroid.app.data.space

import android.content.Context
import android.os.Build
import android.os.Process.sendSignal
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource
import android.os.Process.SIGNAL_KILL as SIGKILL

/**
 * **Two Spaces' servers at once**, each with a kernel, and the three ways one of them could be
 * stopped while the other keeps answering. This is what running several Spaces from the home rests on.
 *
 * Only `POST api/shutdown` stops a Space, and that is what `JupyterServerLauncher.stop()` sends.
 * proot ignores SIGTERM, which is what `Process.destroy()` sends, and SIGKILL to proot orphans the
 * server and kernel it was tracing. Each of the three is a test, so a device run re-checks the
 * reason as well as the answer.
 *
 * Base's server is [BaseSpaceUnderTest]'s, the one owner of base's `run/` in this process. The
 * probe Space gets a launcher of its own. That is allowed because its `run/` is its own directory,
 * and the rule against a second launcher is about two servers sharing **one** `run/`
 * (`JupyterRuntimeFile.clear` deletes every runtime file it finds there). The probe's id is one no
 * user Space can have, so a run on a device that holds real Spaces touches none of them.
 *
 * What it logs under `adb logcat -s JupydroidSpace` is the measurement: each Space's process tree
 * with every process's RSS, and how long the stopped tree took to disappear.
 *
 * ```
 * adb shell am instrument -w -e class com.jupydroid.app.data.space.TwoSpacesAtOnceDeviceTest \
 *   com.jupydroid.app.test/androidx.test.runner.AndroidJUnitRunner
 * ```
 */
@RunWith(AndroidJUnit4::class)
class TwoSpacesAtOnceDeviceTest {
  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no Space to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    check(probeImage) { "the probe Space's image did not land" }
  }

  /** Two servers, two ports, and each one admits its own token and refuses the other's. */
  @Test
  fun eachSpaceServesOnItsOwnPortBehindItsOwnToken() {
    val base = baseServer()
    val probeServer = probeServer()
    assertNotEquals(base.port, probeServer.port)
    assertEquals(HTTP_OK, base.get("api/status", base.token).first)
    assertEquals(HTTP_OK, probeServer.get("api/status", probeServer.token).first)
    assertEquals(HTTP_FORBIDDEN, base.get("api/status", probeServer.token).first)
  }

  /**
   * **SIGTERM to proot stops nothing.** `Process.destroy()` sends exactly this: the tracer, the
   * server and its kernel all outlive it. Kept as a test so that the reason the launcher stops
   * through `api/shutdown` is something a device run checks.
   */
  @Test
  fun sigtermToProotLeavesTheWholeTreeRunning() {
    val (_, tree) = runningProbeWithAKernel()
    val left = stopAndMeasure("sigterm", tree) { sendSignal(prootOf(tree), SIGTERM) }
    assertEquals("SIGTERM took part of the tree", tree.map { it.pid }.toSet(), left.map { it.pid }.toSet())
  }

  /**
   * **SIGKILL to proot orphans what it traced.** The tracer goes, but the server and its kernel run
   * on, re-parented away from the app's tree, so `destroyForcibly()` is no stop either.
   */
  @Test
  fun sigkillToProotOrphansTheServerAndItsKernel() {
    val (_, tree) = runningProbeWithAKernel()
    val tracer = prootOf(tree)
    val left = stopAndMeasure("sigkill", tree) { sendSignal(tracer, SIGKILL) }
    assertEquals(
      "SIGKILL left other than its tracees",
      tree.map {
        it.pid
      }.toSet() - tracer,
      left.map { it.pid }.toSet(),
    )
  }

  /**
   * The launcher's own `stop()`, which is `POST api/shutdown`, JupyterLab's own stop, ends the
   * server, its kernel and the tracer, and leaves the other Space answering.
   */
  @Test
  fun launcherStopTakesTheWholeTreeAndLeavesTheOtherSpace() = withBaseKernel { base, baseKernel ->
    val (_, tree) = runningProbeWithAKernel()
    val left = stopAndMeasure("launcher-stop", tree) { runBlocking { probeLauncher.stop() } }
    assertTrue("processes outlived api/shutdown: $left", left.isEmpty())
    assertEquals(HTTP_OK, base.get("api/kernels/$baseKernel", base.token).first)
  }

  /**
   * The app's own `SpaceServers`: both Spaces read Running, and stopping the probe leaves base alone
   * in the set while base still answers. The probe leaves the set because its process exited, not
   * because `stop` was called.
   */
  @Test
  fun spaceServersRunBothAndAStopLeavesTheOtherRunning() {
    val servers = requireNotNull(BaseSpaceUnderTest.servers) { "no Space runs on this device" }
    runBlocking {
      servers.run(BASE_SPACE.id)
      servers.run(PROBE_SPACE.id)
    }
    probeProcessesSeen += spaceProcessTree(probeRoot.dir)
    assertEquals(setOf(BASE_SPACE.id, PROBE_SPACE.id), servers.running.value)

    runBlocking {
      servers.stop(PROBE_SPACE.id)
      withTimeout(STOP_TIMEOUT) { servers.running.first { it == setOf(BASE_SPACE.id) } }
    }

    val base = baseServer()
    assertEquals(HTTP_OK, base.get("api/status", base.token).first)
  }

  /**
   * Every probe process a test saw is killed by pid, so no test inherits another's tree. By pid
   * rather than by walking down from proot, because an orphaned tracee is no longer under it.
   */
  @After
  fun killWhatTheProbeLeft() {
    probeProcessesSeen.forEach { sendSignal(it.pid, SIGKILL) }
    awaitGone(probeProcessesSeen)
    probeProcessesSeen.clear()
  }

  /** A fresh probe server with a booted kernel, its tree logged with every process's RSS. */
  private fun runningProbeWithAKernel(): Pair<ServerConnection, List<LiveProcess>> {
    val probe = probeServer()
    probe.bootKernel(PROBE_SPACE)
    val tree = logTree("probe", probeRoot.dir).also { probeProcessesSeen += it }
    check(tree.any { "jupydroid_kernel" in it.command }) { "no kernel under the probe's root: $tree" }
    return probe to tree
  }

  /**
   * Base's server with a kernel of its own for [check], shut down afterwards. Started over REST
   * with no websocket, because all [check] asks is that it is still there: a websocket's first
   * `kernel_info` was measured taking longer than OkHttp's 10 s read timeout on a kernel cold-booting
   * beside a second Space.
   */
  private fun withBaseKernel(check: (ServerConnection, String) -> Unit) {
    val base = baseServer()
    val kernel = base.startKernel()
    logTree("base", baseDir)
    try {
      check(base, kernel)
    } finally {
      base.delete("api/kernels/$kernel")
    }
  }

  /** Runs [stop], then logs how long [tree] took to go and what of it did not. */
  private fun stopAndMeasure(label: String, tree: List<LiveProcess>, stop: () -> Unit): List<LiveProcess> {
    val start = TimeSource.Monotonic.markNow()
    stop()
    val left = awaitGone(tree)
    Log.i(SPACE_LOG_TAG, "two-spaces stop=$label ms=${start.elapsedNow().inWholeMilliseconds} left=${left.size}")
    return left
  }

  private fun prootOf(tree: List<LiveProcess>): Int = tree.single { "proot" in it.command }.pid

  private fun baseServer(): ServerConnection = runBlocking { BaseSpaceUnderTest.require().connection() }.connection

  /** The tree, one log line per process and a total, so the RSS of each is the measurement. */
  private fun logTree(label: String, spaceDir: File): List<LiveProcess> = spaceProcessTree(spaceDir).also { tree ->
    tree.forEach { Log.i(SPACE_LOG_TAG, "two-spaces $label pid=${it.pid} rss_kb=${it.residentKb} cmd=${it.command}") }
    Log.i(SPACE_LOG_TAG, "two-spaces $label processes=${tree.size} rss_kb_total=${tree.sumOf { it.residentKb }}")
  }

  /** The processes of [tree] still alive once they have had [STOP_TIMEOUT] to go. */
  private fun awaitGone(tree: List<LiveProcess>): List<LiveProcess> {
    val deadline = TimeSource.Monotonic.markNow() + STOP_TIMEOUT
    var alive = tree.filter(::isAlive)
    while (alive.isNotEmpty() && deadline.hasNotPassedNow()) {
      Thread.sleep(POLL_MS)
      alive = alive.filter(::isAlive)
    }
    return alive
  }

  /** A zombie is gone for this purpose: it holds no memory, and only its parent's reap is left. */
  private fun isAlive(process: LiveProcess): Boolean {
    val status = File("/proc/${process.pid}/status").takeIf { it.isFile } ?: return false
    return runCatching { status.readLines().none { it.startsWith("State:") && "Z" in it } }.getOrDefault(false)
  }

  private companion object {
    const val HTTP_OK = 200
    const val HTTP_CREATED = 201
    const val HTTP_FORBIDDEN = 403
    const val POLL_MS = 100L
    val STOP_TIMEOUT = 10.seconds
    val KERNEL_TIMEOUT = 60.seconds

    /** `android.os.Process` names SIGKILL, SIGQUIT and SIGUSR1 only. */
    const val SIGTERM = 15

    /** Not `space-<n>`, so no Space a user made can collide with it. */
    val PROBE_SPACE = Space(id = "two-spaces-probe", name = "Two Spaces probe", kernels = BASE_SPACE.kernels)

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val baseDir: File get() = baseSpaceDir(context)
    val probeRoot by lazy { PrivateRootUnderTest(context, PROBE_SPACE.id) }
    val probeLauncher by lazy { spaceServerLauncher(context) { SpaceSettings() } }

    /** What [killWhatTheProbeLeft] kills: every process of every probe tree a test logged. */
    val probeProcessesSeen = mutableListOf<LiveProcess>()

    /** The image and the kernelspec base's installer writes, as `PerSpaceRuntimeDeviceTest` seeds them. */
    val probeImage: Boolean by lazy {
      runBlocking { baseSpaceInstaller(context).installIfNeeded() }
      probeRoot.extract()
      runBlocking { SpaceKernelSpec.install(probeRoot.dir) }
      true
    }

    /** A server started for the asking test, so no test depends on another having stopped it or not. */
    fun probeServer(): ServerConnection = runBlocking { probeLauncher.start(PROBE_SPACE) }
      .also { probeProcessesSeen += spaceProcessTree(probeRoot.dir) }

    @JvmStatic
    @AfterClass
    fun stopTheProbeAndRemoveItsImage() {
      runBlocking { probeLauncher.stop() }
      probeRoot.delete()
    }

    /** `GET` [path] presenting [token]; the status and body. */
    fun ServerConnection.get(path: String, token: String): Pair<Int, String> =
      call(Request.Builder().url(urlOf(path)).header("Authorization", "token $token").build())

    fun ServerConnection.delete(path: String): Pair<Int, String> =
      call(Request.Builder().url(urlOf(path)).header("Authorization", "token $token").delete().build())

    /**
     * Starts a kernel in [space] on this server and runs one statement on it, the way a notebook
     * does, and answers with the kernel's id. Over REST alone the kernel never leaves `starting`,
     * because jupyter_server tracks its state only once a client's websocket is attached.
     */
    fun ServerConnection.bootKernel(space: Space): String = runBlocking {
      val opener = OkHttpKernelSocketOpener(this@bootKernel)
      val gateway = RealKernelGateway(KernelSockets(opener, OkHttpKernelLifecycle(this@bootKernel)))
      // A path of its own each time: a reused one rejoins the session whose kernel the last test deleted.
      val session = gateway.connect(space, "two-spaces-probe-${UUID.randomUUID()}.ipynb")
      withTimeout(KERNEL_TIMEOUT) {
        gateway.execute(session, "probe", "import sys").takeWhile { it !is KernelEvent.Idle }.toList()
      }
      session.kernelId
    }

    /** `POST api/kernels`; the new kernel's id. */
    fun ServerConnection.startKernel(): String {
      val spec = """{"name":"${BASE_SPACE.kernels.first().name}"}""".toRequestBody("application/json".toMediaType())
      val (status, answer) = call(
        Request.Builder().url(urlOf("api/kernels")).header("Authorization", "token $token").post(spec).build(),
      )
      check(status == HTTP_CREATED) { "kernel start answered $status, expected $HTTP_CREATED: $answer" }
      return Json.parseToJsonElement(answer).jsonObject.getValue("id").jsonPrimitive.content
    }

    fun ServerConnection.urlOf(path: String) = baseUrl.toHttpUrl().newBuilder().addPathSegments(path).build()

    fun call(request: Request): Pair<Int, String> =
      OkHttpClient().newCall(request).execute().use { it.code to it.body.string() }
  }
}
