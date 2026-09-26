package com.jupydroid.app.data.space

import android.os.Build
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * **P5a: `pylsp` runs in the Space's shell and answers LSP over stdio.**
 *
 * Route 2 in `docs/PLAN.md` ("P5 — LSP") is `python-lsp-server` as a direct stdio
 * subprocess off the `nativeLibraryDir` interpreter - no `jupyter-lsp` websocket hop.
 * These tests prove the two premises that route rests on, **in the app process**
 * through [runInSpace]/`ProcessBuilder`, the only place the seccomp filter applies
 * (a `run-as` probe would answer a question nobody asked - P4b's lesson):
 *
 *  1. the console script runs by name - `usr/local/bin/pylsp` -> `bin/shebang-exec`
 *     -> the interpreter, under the same trampoline chain P7e-2 proved for `pip`; and
 *  2. it *serves* - a `Content-Length`-framed `initialize` gets a framed response with
 *     `capabilities`, and `shutdown`/`exit` ends the process cleanly. `pylsp --version`
 *     alone only proves the import; P6a-0's debugpy bind is the record of a package
 *     that imported fine and aborted on device the moment it did real work.
 *
 * ```
 * ANDROID_SERIAL=TABLET_SERIAL ./gradlew :app:connectedDebugAndroidTest \
 *   -Pandroid.testInstrumentationRunnerArguments.class=com.jupydroid.app.data.space.LanguageServerDeviceTest
 * ```
 */
@RunWith(AndroidJUnit4::class)
class LanguageServerDeviceTest {
  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val baseDir by lazy { baseSpaceDir(context) }
  private val shell by lazy { spaceShellIn(baseDir).path }

  @Before
  fun onlyWhereASpaceCanRun() {
    assumeTrue(
      "no rootfs ships for ${Build.SUPPORTED_ABIS.first()}, so there is no language server to run",
      runCatching { SpaceAbi.of(Build.SUPPORTED_ABIS.first()) }.isSuccess,
    )
    runBlocking { baseSpaceInstaller(context).installIfNeeded() }
  }

  /** By name, through the trampoline - the way a user in the terminal and P5b's adapter reach it. */
  @Test
  fun pylspRunsByNameAndReportsItsVersion() {
    val run = runInSpace(shell, "-c", "pylsp --version")

    assertEquals("pylsp --version exited ${run.exitCode}: ${run.output.trim()}", 0, run.exitCode)
    assertTrue("expected a pylsp version, got: ${run.output.trim()}", run.output.trim().startsWith("pylsp v"))
  }

  /**
   * The route-2 premise itself: `pylsp` spawned with piped stdio - as P5b's adapter will spawn
   * it - answers a real `initialize`. Exec'd straight off the `usr/local/bin` link rather than
   * through a shell, so the request goes through `bin/shebang-exec` and nothing else.
   */
  @Test(timeout = 120_000)
  fun pylspAnswersAnInitializeOverStdio() {
    val pylsp = File(baseDir, "usr/local/bin/pylsp")
    val process = ProcessBuilder(pylsp.path).apply {
      environment().clear()
      environment().putAll(baseSpaceRuntime(context).environment())
    }.start()
    val stderr = StringBuilder()
    val drainer = thread {
      process.errorStream.bufferedReader().useLines { lines ->
        lines.forEach { stderr.appendLine(it) }
      }
    }
    try {
      val capabilities =
        exchange(
          process,
          1,
          """"method":"initialize","params":{"processId":null,"rootUri":null,"capabilities":{}}""",
        )
      assertTrue(
        "no capabilities in the initialize reply: $capabilities; stderr: $stderr",
        "\"capabilities\"" in capabilities,
      )

      exchange(process, 2, """"method":"shutdown","params":null""")
      writeFrame(process.outputStream, """{"jsonrpc":"2.0","method":"exit"}""")
      assertTrue("pylsp did not exit on shutdown+exit", process.waitFor(15, TimeUnit.SECONDS))
      assertEquals(
        "pylsp exited ${process.exitValue()} after a clean shutdown; stderr: $stderr",
        0,
        process.exitValue(),
      )
    } finally {
      process.destroy()
      drainer.join(TimeUnit.SECONDS.toMillis(5))
    }
  }

  /** No syscall was refused while pylsp ran - a refusal the SIGSYS handler swallowed would hide. */
  @Test
  fun nothingWasRefusedWhilePylspRan() {
    runInSpace(shell, "-c", "pylsp --version")

    assertEquals(
      "seccomp refused a syscall other than mbind while pylsp ran",
      emptyList<String>(),
      seccompRefusalsIn(baseDir),
    )
  }

  /**
   * Sends one request with [id] and [paramsAndMethod] (the `"method":…,"params":…` half of the
   * JSON body) and returns the response frame carrying `"id":<id>`, reading past any
   * notifications that land first.
   */
  private fun exchange(process: Process, id: Int, paramsAndMethod: String): String {
    writeFrame(process.outputStream, """{"jsonrpc":"2.0","id":$id,$paramsAndMethod}""")
    repeat(MAX_FRAMES_PER_REPLY) {
      val frame = readFrame(process.inputStream).also { Log.i(SPACE_LOG_TAG, "pylsp frame: $it") }
      if ("\"id\":$id" in frame) return frame
    }
    error("no reply to request $id in $MAX_FRAMES_PER_REPLY frames")
  }

  private fun writeFrame(output: OutputStream, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    output.write("Content-Length: ${bytes.size}\r\n\r\n".toByteArray(Charsets.US_ASCII))
    output.write(bytes)
    output.flush()
  }

  /** Reads one `Content-Length`-framed body; a truncated stream is a failed check, not a hang. */
  private fun readFrame(input: InputStream): String {
    val header = StringBuilder()
    while (!header.endsWith("\r\n\r\n")) {
      val b = input.read()
      check(b >= 0) { "pylsp closed stdout mid-header after '$header'" }
      header.append(b.toChar())
    }
    val length = CONTENT_LENGTH.find(header)?.groupValues?.get(1)?.toInt()
      ?: error("no Content-Length in '$header'")
    val body = ByteArray(length)
    var read = 0
    while (read < length) {
      val n = input.read(body, read, length - read)
      check(n > 0) { "pylsp closed stdout mid-body after $read/$length bytes" }
      read += n
    }
    return String(body, Charsets.UTF_8)
  }

  private companion object {
    val CONTENT_LENGTH = Regex("""Content-Length:\s*(\d+)""")

    /** Generous for a server that may interleave notifications before its reply. */
    const val MAX_FRAMES_PER_REPLY = 10
  }
}
