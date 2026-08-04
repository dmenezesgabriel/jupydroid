package com.jupydroid.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.space.PythonRunner
import com.jupydroid.app.data.space.SpaceRuntime
import com.jupydroid.app.data.space.baseSpaceDir
import com.jupydroid.app.data.space.baseSpaceInstaller
import com.jupydroid.app.data.space.baseSpaceRuntime
import com.jupydroid.app.data.space.baseSpaceServerLauncher
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.import.importTitleFrom
import com.jupydroid.app.theme.JupyterDroidTheme
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
  private val appContainer: AppContainer by lazy {
    AppContainer(
      notebookRepository = DiskNotebookRepository(File(applicationContext.filesDir, "notebooks")),
    )
  }
  private var importTitle by mutableStateOf<String?>(null)

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    installBaseSpace()
    importTitle = importTitleFrom(intent)
    enableEdgeToEdge()
    setContent {
      JupyterDroidTheme {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
          MainNavigation(
            appContainer = appContainer,
            importTitle = importTitle,
            onImportConsumed = { importTitle = null },
          )
        }
      }
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    importTitle = importTitleFrom(intent)
  }

  /**
   * Unpacks the bundled rootfs on first launch. Off the main thread and not awaited:
   * nothing in the UI depends on it yet, and it is idempotent, so a launch that is
   * killed mid-extract simply redoes the work. Progress UI arrives with Step 8.4,
   * when the server actually needs the Space to be ready.
   */
  private fun installBaseSpace() {
    lifecycleScope.launch {
      val installed = baseSpaceInstaller(applicationContext).installIfNeeded()
      Log.i(
        BASE_SPACE_LOG_TAG,
        "base Space v${installed.version} at ${installed.baseDir}: " +
          if (installed.freshlyInstalled) "extracted ${installed.entriesWritten} entries" else "already installed",
      )
      probeInterpreter()
    }
  }

  /**
   * Step 8.3's risk gate: can the app execve a glibc interpreter at all? Logged rather
   * than shown, because there is nothing to interact with yet - the Space UI arrives
   * with the server in Step 8.4.
   */
  private suspend fun probeInterpreter() {
    val runtime = baseSpaceRuntime(applicationContext)
    val base = baseSpaceDir(applicationContext)
    val loader = File(applicationInfo.nativeLibraryDir, "libloader.so")
    val bare = SpaceRuntime(base, loader)
    val loaderOut = PythonRunner(bare).run("--version")
    Log.i(BASE_SPACE_LOG_TAG, "loader: exit=${loaderOut.exitCode} ${loaderOut.output}")

    // A non-Python program, to keep "libc starts" separable from "CPython starts".
    // busybox by its real path, not /bin/echo: the rootfs runs without a chroot, so
    // its absolute symlinks (/bin/echo -> /bin/busybox) dangle against the device root.
    val busybox = PythonRunner(bare).run(File(base, "bin/busybox").path, "echo", "hello")
    Log.i(BASE_SPACE_LOG_TAG, "busybox: exit=${busybox.exitCode} ${busybox.output}")

    val runner = PythonRunner(runtime)
    val version = runner.run("-S", "-c", "import sys; print(sys.version.split()[0], sys.executable)")
    Log.i(BASE_SPACE_LOG_TAG, "interpreter: exit=${version.exitCode} ${version.output}")

    val imports = runner.run("-c", IMPORT_PROBE)
    Log.i(BASE_SPACE_LOG_TAG, "imports: exit=${imports.exitCode} ${imports.output}")

    val zmqBind = runner.run("-c", ZMQ_BIND_PROBE)
    Log.i(BASE_SPACE_LOG_TAG, "zmq tcp bind: exit=${zmqBind.exitCode} ${zmqBind.output}")

    startServer()
  }

  /** Step 8.4: stock jupyter_server on loopback, reached over HTTP from the app. */
  private var serverLauncher: com.jupydroid.app.data.space.JupyterServerLauncher? = null

  private suspend fun startServer() {
    val launcher = baseSpaceServerLauncher(applicationContext).also { serverLauncher = it }
    val connection = launcher.start(BASE_SPACE)
    Log.i(BASE_SPACE_LOG_TAG, "server: ${connection.baseUrl} running=${launcher.isRunning()}")
    Log.i(BASE_SPACE_LOG_TAG, "api/status: ${httpGet(connection.baseUrl + "api/status", connection.token)}")
    Log.i(BASE_SPACE_LOG_TAG, "kernelspecs: ${httpGet(connection.baseUrl + "api/kernelspecs", connection.token)}")
    startKernel(connection.baseUrl, connection.token)
  }

  /**
   * Step 8.5: POST /api/kernels. This is the subprocess execve gate - jupyter_server
   * spawns the kernel itself, through the kernelspec argv the installer rewrote.
   */
  private suspend fun startKernel(baseUrl: String, token: String) {
    val launcher = serverLauncher ?: return
    Log.i(BASE_SPACE_LOG_TAG, "before POST: running=${launcher.isRunning()}")
    val kernel = runCatching { httpPost(baseUrl + "api/kernels", "{}", token) }
    Log.i(
      BASE_SPACE_LOG_TAG,
      "kernel: ${kernel.getOrElse { "FAILED ${it::class.simpleName}: ${it.message}" }} " +
        "(running=${launcher.isRunning()} exit=${launcher.exitCode()})",
    )
    Log.i(BASE_SPACE_LOG_TAG, "server log tail:\n" + serverLogTail())
  }

  private suspend fun serverLogTail(): String = withContext(Dispatchers.IO) {
    File(baseSpaceDir(applicationContext), "run/jupyter_server.log")
      .takeIf { it.isFile }?.readText()?.takeLast(LOG_TAIL_CHARS).orEmpty()
  }

  private suspend fun httpPost(url: String, body: String, token: String): String = withContext(Dispatchers.IO) {
    (URL(url).openConnection() as HttpURLConnection).run {
      try {
        requestMethod = "POST"
        setRequestProperty("Authorization", "token $token")
        doOutput = true
        setRequestProperty("Content-Type", "application/json")
        outputStream.use { it.write(body.toByteArray()) }
        val stream = if (responseCode in 200..299) inputStream else errorStream
        "HTTP $responseCode " + stream.bufferedReader().use { it.readText() }
      } finally {
        disconnect()
      }
    }
  }

  private suspend fun httpGet(url: String, token: String): String = withContext(Dispatchers.IO) {
    (URL(url).openConnection() as HttpURLConnection).run {
      try {
        setRequestProperty("Authorization", "token $token")
        inputStream.bufferedReader().use { it.readText() }
      } finally {
        disconnect()
      }
    }
  }

  private companion object {
    const val BASE_SPACE_LOG_TAG = "JupydroidSpace"
    const val LOG_TAIL_CHARS = 1500

    /** ipykernel binds this exact way for its subprocess-output pipe, ignoring transport. */
    const val ZMQ_BIND_PROBE =
      "import zmq; s = zmq.Context().socket(zmq.PULL); " +
        "print('bound port', s.bind_to_random_port('tcp://127.0.0.1'))"
    val BASE_SPACE = Space(id = "base", name = "Base", kernels = emptyList())

    /** pyzmq is the one that matters: it is the first dlopen'd native extension. */
    const val IMPORT_PROBE =
      "import jupyter_server, ipykernel, zmq; " +
        "print('jupyter_server', jupyter_server.__version__, 'ipykernel', ipykernel.__version__, " +
        "'libzmq', zmq.zmq_version())"
  }

  private fun importTitleFrom(intent: Intent): String? {
    val name = intent.data?.let(::displayNameOf) ?: return null
    return name.takeIf { it.endsWith(".ipynb", ignoreCase = true) }?.let(::importTitleFrom)
  }

  private fun displayNameOf(uri: Uri): String? =
    contentResolver
      .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
      ?: uri.lastPathSegment
}
