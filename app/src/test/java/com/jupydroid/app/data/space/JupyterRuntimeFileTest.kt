package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class JupyterRuntimeFileTest {
  @get:Rule val tempFolder = TemporaryFolder()

  private val runtimeDir: File get() = tempFolder.root

  @Test
  fun read_takesThePortJupyterActuallyBound() {
    write("jpserver-8.json", port = 45243)

    assertEquals(45243, JupyterRuntimeFile.read(runtimeDir)?.port)
  }

  @Test
  fun read_returnsNullBeforeTheServerHasWrittenAnything() {
    assertNull(JupyterRuntimeFile.read(runtimeDir))
  }

  /** The cookie secret lands in the same directory and is not JSON. */
  @Test
  fun read_ignoresOtherFilesInTheRuntimeDirectory() {
    File(runtimeDir, "jupyter_cookie_secret").writeText("not json")

    assertNull(JupyterRuntimeFile.read(runtimeDir))
  }

  /**
   * jupyter-server-mcp writes `jpserver-mcp-<pid>.json` into the same directory, naming its own port
   * (jupyter-server-mcp@d1823a5 `jupyter_server_mcp/runtime.py`). Read as the server's, it would hand
   * the app the MCP port.
   */
  @Test
  fun read_ignoresTheMcpServersRuntimeFile() {
    write("jpserver-mcp-8.json", port = 3001)

    assertNull(JupyterRuntimeFile.read(runtimeDir))
  }

  /** The file is written before it is complete, so a truncated read must not be fatal. */
  @Test
  fun read_treatsAHalfWrittenFileAsNotReadyYet() {
    File(runtimeDir, "jpserver-8.json").writeText("{\"port\": 45")

    assertNull(JupyterRuntimeFile.read(runtimeDir))
  }

  @Test
  fun read_exposesTheUrlSoCallersNeedNotRebuildIt() {
    write("jpserver-8.json", port = 45243)

    assertEquals("http://127.0.0.1:45243/", JupyterRuntimeFile.read(runtimeDir)?.url)
  }

  @Test
  fun read_rejectsAFileWithNoPortRatherThanGuessing() {
    File(runtimeDir, "jpserver-8.json").writeText("{\"url\": \"http://127.0.0.1:1/\"}")

    try {
      JupyterRuntimeFile.read(runtimeDir)
      fail("expected a file without a port to be rejected")
    } catch (e: IllegalArgumentException) {
      assertTrue("message must name the file, was '${e.message}'", e.message!!.contains("jpserver-8.json"))
    }
  }

  private fun write(name: String, port: Int) {
    File(runtimeDir, name).writeText(
      """{"base_url": "/", "hostname": "127.0.0.1", "port": $port, "secure": false,
         "token": "", "url": "http://127.0.0.1:$port/", "version": "2.20.0"}""",
    )
  }
}
