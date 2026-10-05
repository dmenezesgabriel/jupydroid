package com.jupydroid.app.data.space

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.domain.model.CellOutput
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.ServerConnection
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * **`jupyter_server`, a kernel and `import numpy` under proot, in a private copy of the image** -
 * the run ADR-0073 rests on.
 *
 * The whole Jupyter stack runs as the image's own `/usr/bin/python3` with the private root as `/`:
 * nothing in it is pointed at `nativeLibraryDir` except proot and its loader. It answers the three
 * things nobody had measured: ZMQ over the ipc transport through a translated root, the SIGSYS
 * reporter preloaded from inside that root, and the idle cull.
 *
 * Only in a build made with `-Pjupydroid.isolationProbe` after `scripts/stage_isolation_probe.py`.
 *
 * ```
 * python3 scripts/stage_isolation_probe.py
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest -Pjupydroid.isolationProbe \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.ProotRuntimeProbeDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class ProotRuntimeProbeDeviceTest {
  @Before
  fun onlyWhereProotShips() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
    assumeTrue("proot ships only under -Pjupydroid.isolationProbe", root.hasProot)
  }

  @Test
  fun theServerRunsUnderProotAndAnswers() {
    assertEquals(HTTP_OK, server.request("api/status").first)
  }

  /** The image's own interpreter and numpy, over Unix-domain sockets whose paths proot translates. */
  @Test(timeout = 180_000)
  fun aKernelUnderProotImportsNumpyOverTheIpcTransport() {
    assertEquals(kernelAnswer.toString(), "2.5.1", kernelAnswer.text("numpy"))
    assertEquals(kernelAnswer.toString(), "/usr", kernelAnswer.text("prefix"))
    assertEquals(kernelAnswer.toString(), "ipc", kernelAnswer.text("transport"))
  }

  /**
   * `getsignal` returns None only when something outside Python installed the handler - the
   * preload, named by its in-root path. numpy's `mbind` is the one refusal tolerated (ADR-0004).
   */
  @Test(timeout = 180_000)
  fun theSigsysReporterIsArmedFromInsideTheRoot() {
    assertEquals(kernelAnswer.toString(), "None", kernelAnswer.text("sigsys_handler"))
    assertEquals(emptyList<String>(), seccompRefusalsIn(root.dir))
  }

  /** A kernel no websocket holds is culled after [CULL_IDLE_SECONDS], as the shipped server's are. */
  @Test(timeout = 180_000)
  fun anIdleKernelIsCulled() {
    val started = server.request("api/kernels", """{"name":"python3"}""")
    val id = Json.parseToJsonElement(started.second).jsonObject.text("id")
    val begin = TimeSource.Monotonic.markNow()
    while (id in server.request("api/kernels").second && begin.elapsedNow() < CULL_DEADLINE) Thread.sleep(POLL_MS)
    Log.i(PrivateRootUnderTest.PROBE_TAG, "kernel $id culled after ms=${begin.elapsedNow().inWholeMilliseconds}")

    assertTrue("kernel $id still listed after $CULL_DEADLINE", id !in server.request("api/kernels").second)
  }

  private companion object {
    const val HTTP_OK = 200
    const val POLL_MS = 1_000L
    const val CULL_IDLE_SECONDS = 10
    const val CULL_INTERVAL_SECONDS = 5
    val CULL_DEADLINE = 60.seconds
    val START_DEADLINE = 120.seconds
    val KERNEL_TIMEOUT = 120.seconds

    /** Where the server writes inside the root; the app reads it at the same place outside. */
    const val IN_ROOT_RUNTIME = "/run/jupyter"
    const val IN_ROOT_HOME = "/root"

    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext
    val root by lazy { PrivateRootUnderTest(context, "proot-runtime-probe") }
    val hostRuntimeDir: File get() = File(root.dir, IN_ROOT_RUNTIME.removePrefix("/"))
    const val TOKEN = "proot-runtime-probe-token"
    var process: Process? = null

    val server: ServerConnection by lazy { startServer() }
    val kernelAnswer: JsonObject by lazy {
      val answer = runInKernel(KERNEL_PROBE)
      Log.i(PrivateRootUnderTest.PROBE_TAG, "kernel under proot answered $answer")
      Json.parseToJsonElement(answer).jsonObject
    }

    fun startServer(): ServerConnection {
      root.extract()
      root.prepareForProot()
      hostRuntimeDir.mkdirs()
      File(root.dir, IN_ROOT_HOME.removePrefix("/")).mkdirs()
      runBlocking { SpaceKernelSpec.install(root.dir, File("/usr/bin/python3")) }
      val begin = TimeSource.Monotonic.markNow()
      process = ProcessBuilder(root.prootCommand(listOf("/usr/bin/python3") + serverArgs()))
        .apply { environment().clear() }
        .apply { environment().putAll(root.prootEnvironment() + inRootEnvironment()) }
        .redirectErrorStream(true)
        .redirectOutput(jupyterServerLogFile(hostRuntimeDir))
        .start()
      val port = awaitPort()
      Log.i(
        PrivateRootUnderTest.PROBE_TAG,
        "server under proot port=$port ms=${begin.elapsedNow().inWholeMilliseconds}",
      )
      return ServerConnection(baseUrl = "http://127.0.0.1:$port/", port = port, token = TOKEN)
    }

    /**
     * The shipped command line, read inside the root, with the cull shortened so a test can watch it
     * and root allowed.
     */
    fun serverArgs(): List<String> = jupyterServerArgs(File(IN_ROOT_HOME), TOKEN, File("/bin/sh")).map {
      // traitlets refuses a flag given twice, so the shipped value is replaced rather than overridden.
      when (it.substringBefore("=")) {
        "--MappingKernelManager.cull_idle_timeout" -> "--MappingKernelManager.cull_idle_timeout=$CULL_IDLE_SECONDS"
        "--MappingKernelManager.cull_interval" -> "--MappingKernelManager.cull_interval=$CULL_INTERVAL_SECONDS"
        else -> it
      }
    } +
      // proot's `-0` reports uid 0, which `apk add` needs, and jupyter_server refuses to start as
      // root without this flag (jupyter_server 2.20.0 `serverapp.py` `ServerApp.start_app`).
      "--allow-root"

    /** Every path is the in-root one: inside the translated root `/usr/bin/python3` is simply itself. */
    fun inRootEnvironment(): Map<String, String> = mapOf(
      "PATH" to PrivateRootUnderTest.IN_ROOT_PATH,
      "HOME" to IN_ROOT_HOME,
      "TMPDIR" to "/tmp",
      "SHELL" to "/bin/sh",
      "JUPYTER_RUNTIME_DIR" to IN_ROOT_RUNTIME,
      "PYTHONFAULTHANDLER" to "1",
      "LD_PRELOAD" to "/lib/libsigsys_report.so",
      "JUPYDROID_SIGSYS_LOG" to "/tmp/sigsys.log",
    )

    fun awaitPort(): Int {
      val begin = TimeSource.Monotonic.markNow()
      while (begin.elapsedNow() < START_DEADLINE) {
        JupyterRuntimeFile.read(hostRuntimeDir)?.let { return it.port }
        check(process?.isAlive == true) { "server under proot exited: ${serverLogTail()}" }
        Thread.sleep(POLL_MS / 10)
      }
      error("server under proot reported no port within $START_DEADLINE: ${serverLogTail()}")
    }

    fun serverLogTail(): String = jupyterServerLogFile(hostRuntimeDir).takeIf {
      it.isFile
    }?.readText()?.takeLast(LOG_TAIL).orEmpty()

    /** Stdout of [code] in a fresh kernel on the server under proot; the kernel is shut down after. */
    fun runInKernel(code: String): String = runBlocking {
      val gateway = RealKernelGateway(KernelSockets(OkHttpKernelSocketOpener(server), OkHttpKernelLifecycle(server)))
      val session = gateway.connect(BASE_SPACE, "proot-probe.ipynb")
      try {
        withTimeout(KERNEL_TIMEOUT) {
          gateway.execute(session, "probe", code).takeWhile { it !is KernelEvent.Idle }
            .filterIsInstance<KernelEvent.Output>().map { it.output }.toList()
        }.filterIsInstance<CellOutput.Stream>().joinToString("") { it.text }
      } finally {
        gateway.shutdown(session)
      }
    }

    /** `GET` [path], or `POST` [body] to it, with token auth; the status and the body. */
    fun ServerConnection.request(path: String, body: String? = null): Pair<Int, String> {
      val url = baseUrl.toHttpUrl().newBuilder().addPathSegments(path).build()
      val request = Request.Builder().url(url).header("Authorization", "token $token")
        .apply { body?.let { post(it.toRequestBody("application/json".toMediaType())) } }
        .build()
      return OkHttpClient().newCall(request).execute().use { it.code to it.body.string() }
    }

    fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

    /** The server's own account outlives the root, so a run can be read after it is deleted. */
    @JvmStatic
    @AfterClass
    fun stopServerAndRemoveTheRoot() {
      process?.destroy()
      process?.waitFor()
      jupyterServerLogFile(hostRuntimeDir).takeIf { it.isFile }?.copyTo(File(spacesDir(context), SAVED_LOG), true)
      root.delete()
    }

    const val LOG_TAIL = 4_000
    const val SAVED_LOG = "proot-runtime-probe-server.log"

    val KERNEL_PROBE = """
      import json, signal, sys, numpy
      from ipykernel.kernelapp import IPKernelApp
      connection = IPKernelApp.instance().connection_file
      print(json.dumps({
          "numpy": numpy.__version__, "prefix": sys.prefix,
          "transport": json.load(open(connection))["transport"],
          "sigsys_handler": repr(signal.getsignal(signal.SIGSYS)),
      }))
    """.trimIndent()
  }
}
