package com.jupydroid.app.data.space

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * One language-server process driven over its stdio with `Content-Length` framing, as the adapter
 * drives it - shared by every device test that asks whether a `pylsp` *serves*, whichever Space's
 * environment it was started in.
 *
 * stderr drains on its own thread from the start, so an undrained pipe can never stall the reply
 * being waited on, and what the server said there is in every failure message.
 *
 * ```
 * LspStdioSession(process).use { assertTrue("\"capabilities\"" in it.initialize()) }
 * ```
 */
class LspStdioSession(private val process: Process) : AutoCloseable {
  private val stderrLines = StringBuilder()
  private val drainer = thread {
    process.errorStream.bufferedReader().useLines { lines -> lines.forEach { stderrLines.appendLine(it) } }
  }

  /** What the server wrote to stderr so far. */
  val stderr: String get() = stderrLines.toString()

  /** Sends a real `initialize` and returns the reply frame, which carries `capabilities` if it serves. */
  fun initialize(): String =
    exchange(1, """"method":"initialize","params":{"processId":null,"rootUri":null,"capabilities":{}}""")

  /** `shutdown` then `exit`; the exit code, or null if the process outlived [EXIT_WAIT_SECONDS]. */
  fun shutdownAndExit(): Int? {
    exchange(2, """"method":"shutdown","params":null""")
    writeFrame(process.outputStream, """{"jsonrpc":"2.0","method":"exit"}""")
    return if (process.waitFor(EXIT_WAIT_SECONDS, TimeUnit.SECONDS)) process.exitValue() else null
  }

  override fun close() {
    process.destroy()
    drainer.join(TimeUnit.SECONDS.toMillis(EXIT_WAIT_SECONDS))
  }

  /**
   * Sends one request with [id] and [paramsAndMethod] (the `"method":…,"params":…` half of the
   * JSON body) and returns the response frame carrying `"id":<id>`, reading past any
   * notifications that land first.
   */
  private fun exchange(id: Int, paramsAndMethod: String): String {
    writeFrame(process.outputStream, """{"jsonrpc":"2.0","id":$id,$paramsAndMethod}""")
    repeat(MAX_FRAMES_PER_REPLY) {
      val frame = readFrame(process.inputStream).also { Log.i(SPACE_LOG_TAG, "lsp frame: $it") }
      if ("\"id\":$id" in frame) return frame
    }
    error("no reply to request $id in $MAX_FRAMES_PER_REPLY frames; stderr: $stderr")
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
      check(b >= 0) { "the server closed stdout mid-header after '$header'; stderr: $stderr" }
      header.append(b.toChar())
    }
    val length = CONTENT_LENGTH.find(header)?.groupValues?.get(1)?.toInt()
      ?: error("no Content-Length in '$header'")
    val body = ByteArray(length)
    var read = 0
    while (read < length) {
      val n = input.read(body, read, length - read)
      check(n > 0) { "the server closed stdout mid-body after $read/$length bytes; stderr: $stderr" }
      read += n
    }
    return String(body, Charsets.UTF_8)
  }

  private companion object {
    val CONTENT_LENGTH = Regex("""Content-Length:\s*(\d+)""")

    /** Generous for a server that may interleave notifications before its reply. */
    const val MAX_FRAMES_PER_REPLY = 10

    const val EXIT_WAIT_SECONDS = 15L
  }
}
