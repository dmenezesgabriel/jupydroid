package com.jupydroid.app.data.space

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

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

  /** A stale file from a killed server would otherwise point at a dead port. */
  @Test
  fun clear_removesRuntimeFilesSoAStaleOneIsNeverRead() {
    write("jpserver-8.json", port = 45243)

    JupyterRuntimeFile.clear(runtimeDir)

    assertNull(JupyterRuntimeFile.read(runtimeDir))
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
