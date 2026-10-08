package com.jupydroid.app.data.space

import android.os.Build
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
import org.junit.Assume.assumeTrue
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
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.KernelWindowProbeDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class KernelWindowProbeDeviceTest {
  @Before
  fun onlyWhereProotShips() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  @Test(timeout = 900_000)
  fun whereTheKernelWindowGoes() {
    val report = StringBuilder("== kernel window split (base, $RUNS runs each, in run order)\n")
    val interpreter = runsOf { timedPython("-c", "pass") }
    val imports = runsOf { timedPython("-c", "import $KERNEL_MODULE") }
    report.append(row("python3 -c pass", interpreter)).append(row("import $KERNEL_MODULE", imports))
    report.append(slowestImports())
    val windows = listOf(
      "window, alone" to { window(DEFAULT_KERNEL_ID) },
      "window, pylsp beside it" to { windowBesidePylsp() },
      "window, kernel at INFO" to { window(installInfoKernel()) },
      "window, cache evicted" to { evicted { window(DEFAULT_KERNEL_ID) } },
      "window, evicted, pylsp beside" to { evicted { windowBesidePylsp() } },
    )
    windows.forEach { (label, measure) -> report.append(row(label, runsOf(measure))) }
    File(context.getExternalFilesDir(null), "kernel-window-probe.txt").writeText(report.toString())
    Log.i(SPACE_LOG_TAG, report.toString())

    // The probe's job is to print the split; fail only if it measured nothing.
    assertTrue(report.toString(), interpreter.median() > 0 && imports.median() > interpreter.median())
  }

  private companion object {
    const val RUNS = 3
    const val KERNEL_MODULE = "jupydroid_kernel"
    const val INFO_KERNEL = "kernel-window-info"
    const val SLOWEST = 30
    const val CELL_URI = "file:///home/jupydroid/kernel-window.py"
    val EVICT_PYTHON = """
      import os
      for top, _, names in os.walk("/usr/lib/python3.12"):
          for name in names:
              try:
                  fd = os.open(os.path.join(top, name), os.O_RDONLY)
              except OSError:
                  continue
              os.posix_fadvise(fd, 0, 0, os.POSIX_FADV_DONTNEED)
              os.close(fd)
    """.trimIndent()

    /** Lets a shut-down kernel's process exit, so it never competes with the next run's. */
    const val SETTLE_MS = 2_000L
    val CONNECT_TIMEOUT = 120.seconds
    val LSP_TIMEOUT = 120.seconds

    val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val runtime: SpaceRuntime get() = spaceRuntime(context, BASE_SPACE)
    val client = OkHttpClient.Builder().readTimeout(0, TimeUnit.MILLISECONDS).build()

    /** One launcher for the class: `JupyterServerLauncher.start` begins with `stop()`. */
    val launcher by lazy {
      spaceServerLauncher(context, settingsFor = { SpaceSettings() }, defaultKernelFor = { DEFAULT_KERNEL_ID })
    }
    val server: ServerConnection by lazy { runBlocking { launcher.start(BASE_SPACE) } }
    var sessions = 0

    /** Every run is kept in order, because the first of a kind is the one a cold page cache would slow. */
    fun runsOf(measure: () -> Long): List<Long> = List(RUNS) { measure() }

    fun List<Long>.median(): Long = sorted()[size / 2]

    fun row(label: String, runs: List<Long>): String =
      "${label.padEnd(30)}: ${runs.joinToString(" / ")} ms, median ${runs.median()} ms\n"

    /** Wall time of the Space's interpreter run with [args] to its exit, which must be 0. */
    fun timedPython(vararg args: String): Long {
      val begin = TimeSource.Monotonic.markNow()
      val process = runtime.processBuilder(args.toList()).redirectErrorStream(true).start()
      val output = process.inputStream.bufferedReader().readText()
      check(process.waitFor() == 0) { "python3 ${args.joinToString(" ")} exited ${process.exitValue()}: $output" }
      return begin.elapsedNow().inWholeMilliseconds
    }

    /**
     * The kernel module's [SLOWEST] slowest imports at any depth, from `-X importtime`, in import
     * order and indented as CPython indents them - one space plus two per level of nesting
     * (`Python/import.c`, `import_find_and_load`) - so a row's parents are the less indented rows
     * above it.
     */
    fun slowestImports(): String {
      val process = runtime.processBuilder(listOf("-X", "importtime", "-c", "import $KERNEL_MODULE")).start()
      val rows = process.errorStream.bufferedReader().readLines().mapNotNull(::importRow)
      process.waitFor()
      val slowest = rows.sortedByDescending { it.second }.take(SLOWEST).toSet()
      return rows.filter { it in slowest }
        .joinToString("") { (module, micros) -> "  ${module.padEnd(48)} : ${micros / 1000}ms cumulative\n" }
    }

    /** `import time: self | cumulative | name` -> (name with its indent, cumulative µs). */
    fun importRow(row: String): Pair<String, Long>? {
      val columns = row.removePrefix("import time:").split("|")
      if (columns.size != 3) return null
      return columns[1].trim().toLongOrNull()?.let { columns[2].trimEnd() to it }
    }

    /**
     * [measure], after the clean pages of every file under the image's `/usr/lib/python3.12` are
     * dropped from the page cache, as memory pressure does between two launches of the app.
     * `POSIX_FADV_DONTNEED` needs no root, only a descriptor (posix_fadvise(2)).
     */
    fun <T> evicted(measure: () -> T): T {
      timedPython("-c", EVICT_PYTHON)
      return measure()
    }

    /**
     * Opens a session on [kernelName] and times its socket from asked to accepted, exactly the span
     * the app logs as `socket.opened → socket.connected`; the session is closed after.
     */
    fun window(kernelName: String): Long {
      val (sessionId, kernelId) = openSession(kernelName)
      try {
        return timeSocketAccepted(kernelId, sessionId)
      } finally {
        server.send("api/sessions/$sessionId", "DELETE")
        Thread.sleep(SETTLE_MS)
      }
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
    fun openSession(kernelName: String): Pair<String, String> {
      val body = """{"path":"kernel-window-${sessions++}.ipynb","type":"notebook","kernel":{"name":"$kernelName"}}"""
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
