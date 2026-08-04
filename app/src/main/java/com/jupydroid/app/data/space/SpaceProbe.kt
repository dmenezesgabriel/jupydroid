package com.jupydroid.app.data.space

import android.content.Context
import android.util.Log
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.ServerConnection
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Walks the Step 8 runtime chain at startup and logs what happened at each stage:
 * install the Space, run the interpreter, start the server, start a kernel, execute
 * a cell. Every stage reports its outcome, so a regression names the stage it broke
 * at rather than surfacing as a blank screen.
 *
 * Temporary by intent. It is replaced by real UI as the Space screens land, and by
 * the KernelGateway port in Step 10; it exists because the runtime has no UI yet and
 * a silent runtime is undebuggable. Filter with `adb logcat -s JupydroidSpace`.
 */
class SpaceProbe(
  private val context: Context,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
  suspend fun run() {
    installSpace()
    probeInterpreter()
    val connection = startServer() ?: return
    val kernelId = startKernel(connection) ?: return
    exerciseKernel(connection, kernelId)
  }

  private suspend fun installSpace() {
    val installed = baseSpaceInstaller(context).installIfNeeded()
    Log.i(
      TAG,
      "base Space v${installed.version} at ${installed.baseDir}: " +
        if (installed.freshlyInstalled) "extracted ${installed.entriesWritten} entries" else "already installed",
    )
  }

  private suspend fun probeInterpreter() {
    val runner = PythonRunner(baseSpaceRuntime(context))
    val version = runner.run("-c", "import sys; print(sys.version.split()[0], sys.executable)")
    Log.i(TAG, "interpreter: exit=${version.exitCode} ${version.output}")

    val imports = runner.run("-c", IMPORT_PROBE)
    Log.i(TAG, "imports: exit=${imports.exitCode} ${imports.output}")

    for ((label, code) in SCIENCE_PROBES) {
      val outcome = runner.run("-c", code)
      Log.i(TAG, "$label: exit=${outcome.exitCode} ${outcome.output}")
    }
  }

  private suspend fun startServer(): ServerConnection? {
    launcher = baseSpaceServerLauncher(context)
    val connection = runCatching { launcher!!.start(BASE_SPACE) }.getOrElse { failure ->
      Log.e(TAG, "server failed to start", failure)
      return null
    }
    Log.i(TAG, "server: ${connection.baseUrl} running=${launcher!!.isRunning()}")
    Log.i(TAG, "api/status: ${get(connection, "api/status")}")
    return connection
  }

  private suspend fun startKernel(connection: ServerConnection): String? {
    val body = runCatching { post(connection, "api/kernels") }
      .getOrElse { "FAILED ${it::class.simpleName}: ${it.message}" }
    Log.i(TAG, "kernel: $body (running=${launcher?.isRunning()} exit=${launcher?.exitCode()})")

    val kernelId = KERNEL_ID.find(body)?.groupValues?.get(1)
    if (kernelId == null) Log.w(TAG, "no kernel id; server log tail:\n${serverLogTail()}")
    return kernelId
  }

  /**
   * Exercises every branch of [ExecutionOutcome] against a real kernel, not just the
   * happy one: a value, a raising cell, and a socket that cannot be opened. An error
   * path that has never run is an error path that does not work.
   */
  private suspend fun exerciseKernel(connection: ServerConnection, kernelId: String) {
    val channel = KernelChannel(connection)

    val value = channel.execute(kernelId, "2 + 2")
    Log.i(TAG, "execute 2 + 2 -> $value")
    if (value !is ExecutionOutcome.Result) Log.w(TAG, "server log tail:\n${serverLogTail()}")

    // The path that actually matters, and the one the preload has to reach: the kernel is
    // a subprocess jupyter_server spawned, not a process we launched. If LD_PRELOAD did
    // not survive that hop, this cell is where a user meets exit 159.
    Log.i(TAG, "execute import numpy -> ${channel.execute(kernelId, NUMPY_CELL)}")

    Log.i(TAG, "execute 1 / 0 -> ${channel.execute(kernelId, "1 / 0")}")
    Log.i(TAG, "execute on unknown kernel -> ${channel.execute(UNKNOWN_KERNEL, "2 + 2")}")

    // Settles where the UI should read execution state from: the REST field is only
    // as fresh as the last iopub message the server saw.
    Log.i(TAG, "kernels after execution: ${get(connection, "api/kernels")}")
  }

  private suspend fun serverLogTail(): String = withContext(ioDispatcher) {
    File(baseSpaceDir(context), "run/jupyter_server.log")
      .takeIf { it.isFile }?.readText()?.takeLast(LOG_TAIL_CHARS).orEmpty()
  }

  private suspend fun get(connection: ServerConnection, path: String): String =
    request(connection, path, body = null)

  private suspend fun post(connection: ServerConnection, path: String): String =
    request(connection, path, body = "{}")

  private suspend fun request(
    connection: ServerConnection,
    path: String,
    body: String?,
  ): String = withContext(ioDispatcher) {
    (URL(connection.baseUrl + path).openConnection() as HttpURLConnection).run {
      try {
        setRequestProperty("Authorization", "token ${connection.token}")
        if (body != null) {
          requestMethod = "POST"
          doOutput = true
          setRequestProperty("Content-Type", "application/json")
          outputStream.use { it.write(body.toByteArray()) }
        }
        val stream = if (responseCode in HTTP_OK_RANGE) inputStream else errorStream
        "HTTP $responseCode " + stream.bufferedReader().use { it.readText() }
      } finally {
        disconnect()
      }
    }
  }

  private var launcher: JupyterServerLauncher? = null

  private companion object {
    const val TAG = "JupydroidSpace"
    const val LOG_TAIL_CHARS = 1500
    val HTTP_OK_RANGE = 200..299
    const val UNKNOWN_KERNEL = "00000000-0000-0000-0000-000000000000"
    val KERNEL_ID = Regex("\"id\": \"([^\"]+)\"")

    const val NUMPY_CELL = "import numpy; numpy.linalg.det(numpy.array([[4.0, 7.0], [2.0, 6.0]]))"
    val BASE_SPACE = Space(id = "base", name = "Base", kernels = emptyList())

    /**
     * Importing proves the wheel loads; solving proves its BLAS/LAPACK backend runs.
     * A musllinux wheel that imports and then faults on the first real call would be
     * the worst outcome to discover in Step 12.
     */
    val SCIENCE_PROBES = listOf(
      "numpy import" to "import numpy; print(numpy.__version__)",
      "numpy compute" to
        "import numpy; a = numpy.array([[3.0, 1.0], [1.0, 2.0]]); print('dot', a.dot(a).sum())",
      "scipy import" to "import scipy; print(scipy.__version__)",
      "scipy compute" to
        "import numpy, scipy.linalg as la; " +
          "print('solve', la.solve(numpy.array([[3.0, 1.0], [1.0, 2.0]]), " +
          "numpy.array([9.0, 8.0])).tolist())",
      // Threads and a real BLAS allocation, because that is where a second refused
      // syscall would show up if the set is not just mbind: pthread creation, futexes
      // and OpenBLAS's per-thread arenas all sit outside the 2x2 toy path above.
      "numpy under load" to
        "import numpy, threading; " +
          "a = numpy.random.default_rng(0).random((512, 512)); " +
          "r = []; t = [threading.Thread(target=lambda: r.append(float((a @ a).sum()))) " +
          "for _ in range(4)]; " +
          "[x.start() for x in t]; [x.join() for x in t]; " +
          "print('threads', len(r), 'fft', float(abs(numpy.fft.fft(a[0])).sum()))",
    )

    /** pyzmq is the one that matters: it is the first dlopen'd native extension. */
    const val IMPORT_PROBE =
      "import jupyter_server, ipykernel, zmq; " +
        "print('jupyter_server', jupyter_server.__version__, 'ipykernel', ipykernel.__version__, " +
        "'libzmq', zmq.zmq_version())"
  }
}
