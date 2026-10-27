package com.jupydroid.app.data.space

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The `jpserver-<pid>.json` connection file jupyter_server writes on startup.
 *
 * This is how the ephemeral port is discovered. The server is launched with
 * `--ServerApp.port=0` so the OS picks a free port, which avoids both a hardcoded
 * port and the race in picking one ourselves and hoping it is still free by the time
 * the server binds. The server then reports what it actually bound.
 */
@Serializable
data class JupyterRuntimeFile(
  val port: Int,
  val url: String = "",
  @SerialName("version") val serverVersion: String = "",
) {
  companion object {
    private const val PREFIX = "jpserver-"
    private const val SUFFIX = ".json"
    private val json = Json { ignoreUnknownKeys = true }

    /** Null until the server has written a complete file; the caller polls. */
    @Suppress("SwallowedException") // a truncated file is a timing state, not an error
    // MissingFieldException is the only way to tell "no port" from "not written yet".
    @OptIn(ExperimentalSerializationApi::class)
    fun read(runtimeDir: File): JupyterRuntimeFile? {
      val file = runtimeFiles(runtimeDir).firstOrNull() ?: return null
      return try {
        json.decodeFromString<JupyterRuntimeFile>(file.readText())
      } catch (e: MissingFieldException) {
        // Parsed as JSON but has no port: a real problem, not a timing one.
        throw IllegalArgumentException("'$file' has no port field: ${e.message}", e)
      } catch (e: SerializationException) {
        // The file is written non-atomically, so a truncated read just means the
        // server has not finished starting. The caller polls.
        null
      }
    }

    /** A leftover file from a killed server would otherwise advertise a dead port. */
    fun clear(runtimeDir: File) {
      runtimeFiles(runtimeDir).forEach { it.delete() }
    }

    private fun runtimeFiles(runtimeDir: File): List<File> =
      runtimeDir.listFiles { file -> isServerRuntimeFile(file.name) }
        ?.sortedBy { it.name }
        .orEmpty()

    /**
     * `jpserver-<pid>.json` exactly. jupyter-server-mcp writes `jpserver-mcp-<pid>.json` beside it, naming
     * its own port (jupyter-server-mcp@d1823a5 `jupyter_server_mcp/runtime.py`).
     */
    private fun isServerRuntimeFile(name: String): Boolean {
      val pid = name.removePrefix(PREFIX).removeSuffix(SUFFIX)
      return name.startsWith(PREFIX) && name.endsWith(SUFFIX) && pid.isNotEmpty() && pid.all(Char::isDigit)
    }
  }
}
