package com.jupydroid.app.data.space

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.DEFAULT_KERNEL_ID
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * **Where the kernel window's time goes** - the wait between the app opening a kernel's socket and
 * the server accepting it, which is `kernel.socket.opened → kernel.socket.connected` in
 * `KernelLog`.
 *
 * The server accepts only once the kernel answers: its socket awaits `kernel_manager.ready`, then
 * nudges with `kernel_info` every 0.5 s until a reply (jupyter_server 2.20.0
 * `services/kernels/connection/channels.py`, `prepare` and `nudge`). So the window holds the
 * interpreter starting under proot, `import jupydroid_kernel`, the kernel app's init and the nudge.
 * This probe runs base's interpreter, its kernel module and its server - the shipped launcher, on
 * the installed Space - and takes those apart:
 *
 * - the interpreter alone, `python3 -c pass`
 * - the interpreter importing the kernel module, and that import's ten slowest top-level modules
 * - the window with nothing else running
 * - the window with pylsp starting beside it, as `StdioLanguageServer` starts it when a notebook opens
 * - the window with the kernel at `--log-level=INFO` rather than `SpaceKernelSpec`'s DEBUG
 *
 * Then it measures the two repairs that would take the kernel module's import out of the window,
 * each from an evicted page cache, which is how the app's warm start finds the image:
 *
 * - a pre-read: the import run once and thrown away, then the window
 * - a pre-start: a kernel started a walk's length before a session binds it, which `POST api/sessions`
 *   does when handed `kernel.id` (jupyter_server 2.20.0 `services/sessions/handlers.py`)
 * - what a pre-started kernel holds while it waits, as its proportional set size
 * - the server's own start, alone and with a pre-read beside it
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.KernelWindowProbeDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class KernelWindowProbeDeviceTest {
  @Before
  fun onlyWhereProotShips() = installBaseWhereProotShips()

  @Test(timeout = 1_800_000)
  fun whereTheKernelWindowGoes() {
    val report = StringBuilder("== kernel window split (base, $PROBE_RUNS runs each, in run order)\n")
    val interpreter = runsOf { probe.timedPython("-c", "pass") }
    val imports = runsOf { probe.timedPython("-c", "import $KERNEL_MODULE") }
    report.append(row("python3 -c pass", interpreter)).append(row("import $KERNEL_MODULE", imports))
    report.append(probe.slowestImports(KERNEL_MODULE))
    val windows = listOf(
      "window, alone" to { window(DEFAULT_KERNEL_ID) },
      "window, pylsp beside it" to { windowBesidePylsp() },
      "window, kernel at INFO" to { window(installInfoKernel()) },
      "window, cache evicted" to { probe.evicted { window(DEFAULT_KERNEL_ID) } },
      "window, evicted, pylsp beside" to { probe.evicted { windowBesidePylsp() } },
    )
    windows.forEach { (label, measure) -> report.append(row(label, runsOf(measure))) }
    report.append(repairRows())
    File(context.getExternalFilesDir(null), "kernel-window-probe.txt").writeText(report.toString())
    Log.i(SPACE_LOG_TAG, report.toString())

    // The probe's job is to print the split; fail only if it measured nothing.
    assertTrue(report.toString(), interpreter.median() > 0 && imports.median() > interpreter.median())
  }

  /** The repairs' rows. The server's restarts come last, because they replace the server the others use. */
  private fun repairRows(): String {
    val windows = listOf(
      "window, evicted, pre-read first" to { probe.evicted { preRead() }.let { window(DEFAULT_KERNEL_ID) } },
      "window, evicted, pre-started" to { probe.evicted { windowOnPreStartedKernel() } },
      "server start, evicted" to { probe.evicted { timedServerStart() } },
      "server start, evicted, pre-read" to
        { probe.evicted { probe.besideImport(KERNEL_MODULE) { timedServerStart() } } },
    )
    val idle = row("pre-started kernel, idle PSS", runsOf { idleKernelPssKb() }, "kB")
    return idle + windows.joinToString("") { (label, measure) -> row(label, runsOf(measure)) }
  }

  private companion object {
    const val KERNEL_MODULE = "jupydroid_kernel"
    const val INFO_KERNEL = "kernel-window-info"
    const val CELL_URI = "file:///home/jupydroid/kernel-window.py"

    /** Lets a shut-down kernel's process exit, so it never competes with the next run's. */
    const val SETTLE_MS = 2_000L

    /**
     * How long a pre-started kernel waits before its session asks for it: the user's walk from Run on
     * the home to the notebook's first cell, which the cold-path flow measures at about 13 s.
     */
    const val WALK_MS = 13_000L
    val CONNECT_TIMEOUT = 120.seconds
    val LSP_TIMEOUT = 120.seconds

    val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val runtime: SpaceRuntime get() = spaceRuntime(context, BASE_SPACE)
    val probe get() = SpaceTimingProbe(runtime)
    val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    /** One launcher for the class: `JupyterServerLauncher.start` begins with `stop()`. */
    val launcher by lazy {
      spaceServerLauncher(
        context,
        settingsFor = { SpaceSettings() },
        defaultKernelFor = { DEFAULT_KERNEL_ID },
        nativeLibraryDir = patchedProotLibraryDir(),
      )
    }
    var running: ServerConnection? = null

    /** The server the class's launcher runs, started on first use and replaced by [timedServerStart]. */
    val server: ServerConnection get() = running ?: runBlocking { launcher.start(BASE_SPACE) }.also { running = it }
    var sessions = 0

    /**
     * Opens a session on [kernelName] and times its socket from asked to accepted, exactly the span
     * the app logs as `socket.opened → socket.connected`; the session is closed after.
     */
    fun window(kernelName: String): Long {
      val (sessionId, kernelId) = openSession(kernelName)
      try {
        return timeSocketAccepted(kernelId, sessionId)
      } finally {
        closeSession(sessionId)
      }
    }

    /** Deleting a session shuts its kernel down too, whichever way the kernel was started. */
    fun closeSession(sessionId: String) {
      server.send("api/sessions/$sessionId", "DELETE")
      Thread.sleep(SETTLE_MS)
    }

    /** The kernel module's import, run to its end and thrown away: what is left of it is the page cache. */
    fun preRead(): Long = probe.timedPython("-c", "import $KERNEL_MODULE")

    /** The window on a kernel `POST api/kernels` started [WALK_MS] before a session asks for it. */
    fun windowOnPreStartedKernel(): Long {
      val kernelId = startKernel()
      Thread.sleep(WALK_MS)
      val sessionId = openSessionOn(kernelId)
      try {
        return timeSocketAccepted(kernelId, sessionId)
      } finally {
        closeSession(sessionId)
      }
    }

    /** What a pre-started kernel holds [WALK_MS] after it starts, before anything has asked it for anything. */
    fun idleKernelPssKb(): Long {
      val kernelId = startKernel()
      try {
        Thread.sleep(WALK_MS)
        return ProcessFootprint { arguments ->
          arguments.any { it.endsWith("kernel-$kernelId.json") }
        }.proportionalSetKb()
      } finally {
        server.send("api/kernels/$kernelId", "DELETE")
        Thread.sleep(SETTLE_MS)
      }
    }

    /** The server's start from a stopped server to a reported port, as `JupyterServerLauncher.start` times none of it. */
    fun timedServerStart(): Long {
      runBlocking { launcher.stop() }
      val begin = TimeSource.Monotonic.markNow()
      running = runBlocking { launcher.start(BASE_SPACE) }
      return begin.elapsedNow().inWholeMilliseconds
    }

    /** `POST api/kernels` for base's default kernel, with no session; the kernel's id. */
    fun startKernel(): String {
      val kernel = server.send("api/kernels", "POST", """{"name":"$DEFAULT_KERNEL_ID"}""")
      return Json.parseToJsonElement(kernel).jsonObject.getValue("id").jsonPrimitive.content
    }

    /** [window], with pylsp spawned and handed the cell as the app hands it a notebook it opens. */
    fun windowBesidePylsp(): Long {
      val pylsp = startPylsp(runtime, spaceDirs(context, BASE_SPACE))
      val channel = JsonRpcChannel(pylsp.inputStream, pylsp.outputStream)
      val initialized = thread { runBlocking { openTheCell(channel) } }
      try {
        return window(DEFAULT_KERNEL_ID)
      } finally {
        initialized.join()
        channel.close()
        pylsp.destroy()
        pylsp.waitFor()
      }
    }

    /** `initialize`, `initialized` and a `didOpen` of the cell, which is what makes pylsp lint it. */
    suspend fun openTheCell(channel: JsonRpcChannel) {
      channel.request("initialize", LspWire.initializeParams(), LSP_TIMEOUT)
      channel.notify("initialized", buildJsonObject {})
      channel.notify("textDocument/didOpen", LspWire.didOpenParams(CELL_URI, "python", "print(6 * 7)\n"))
    }

    /** `POST api/sessions` for a fresh notebook path on [kernelName]; the session's id and its kernel's. */
    fun openSession(kernelName: String): Pair<String, String> = postSession("""{"name":"$kernelName"}""")

    /** `POST api/sessions` binding a fresh notebook path to the running kernel [kernelId]; the session's id. */
    fun openSessionOn(kernelId: String): String = postSession("""{"id":"$kernelId"}""").first

    /** `POST api/sessions` for a fresh notebook path on the [kernel] object given; the session's id and its kernel's. */
    fun postSession(kernel: String): Pair<String, String> {
      val body = """{"path":"kernel-window-${sessions++}.ipynb","type":"notebook","kernel":$kernel}"""
      val session = Json.parseToJsonElement(server.send("api/sessions", "POST", body)).jsonObject
      val kernel = session.getValue("kernel").jsonObject
      return session.getValue("id").jsonPrimitive.content to kernel.getValue("id").jsonPrimitive.content
    }

    fun timeSocketAccepted(kernelId: String, sessionId: String): Long {
      val accepted = CountDownLatch(1)
      val request = server.authorized("api/kernels/$kernelId/channels?session_id=$sessionId").build()
      val begin = TimeSource.Monotonic.markNow()
      val socket = client.newWebSocket(
        request,
        object : WebSocketListener() {
          override fun onOpen(webSocket: WebSocket, response: Response) = accepted.countDown()
        },
      )
      check(accepted.await(CONNECT_TIMEOUT.inWholeSeconds, TimeUnit.SECONDS)) { "kernel $kernelId never accepted" }
      return begin.elapsedNow().inWholeMilliseconds.also { socket.close(1000, null) }
    }

    /** base's own kernelspec with only its log level changed, under a name of its own. */
    fun installInfoKernel(): String {
      val shipped = File(baseSpaceDir(context), "usr/share/jupyter/kernels/python3/kernel.json").readText()
      check("--log-level=DEBUG" in shipped) { "expected --log-level=DEBUG in base's kernelspec, got $shipped" }
      infoKernelDir().apply { mkdirs() }.resolve("kernel.json")
        .writeText(shipped.replace("--log-level=DEBUG", "--log-level=INFO"))
      return INFO_KERNEL
    }

    fun infoKernelDir() = File(baseSpaceDir(context), "usr/local/share/jupyter/kernels/$INFO_KERNEL")

    fun ServerConnection.authorized(path: String): Request.Builder =
      Request.Builder().url(baseUrl + path).header("Authorization", "token $token")

    /** [method] on [path] with token auth; the body, which must come with a 2xx. */
    fun ServerConnection.send(path: String, method: String, body: String? = null): String {
      val payload = body?.toRequestBody("application/json".toMediaType())
      val request = authorized(path).method(method, payload).build()
      return client.newCall(request).execute().use {
        check(it.isSuccessful) { "$method $path answered ${it.code}, expected 2xx: ${it.body.string()}" }
        it.body.string()
      }
    }

    @JvmStatic
    @AfterClass
    fun stopTheServerAndRemoveTheInfoKernel() {
      runBlocking { launcher.stop() }
      infoKernelDir().deleteRecursively()
    }
  }
}
