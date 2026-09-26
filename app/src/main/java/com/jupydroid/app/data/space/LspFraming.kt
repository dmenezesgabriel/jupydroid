package com.jupydroid.app.data.space

import java.io.InputStream
import java.io.OutputStream

/**
 * `Content-Length`-framed bodies over a byte stream - the transport LSP and DAP share.
 *
 * The header is `Name: value` lines ending at one blank line; `Content-Length` is required
 * and every other header is skipped, because pylsp's replies carry a `Content-Type` of their
 * own. UTF-8 on both sides: LSP bodies are JSON, and JSON without a charset is UTF-8.
 *
 * ```
 * LspFraming.writeFrame(out, """{"jsonrpc":"2.0","id":1}""")
 * ```
 */
internal object LspFraming {
  fun writeFrame(output: OutputStream, body: String) {
    val bytes = body.toByteArray(Charsets.UTF_8)
    output.write("Content-Length: ${bytes.size}\r\n\r\n".toByteArray(Charsets.US_ASCII))
    output.write(bytes)
    output.flush()
  }

  /**
   * One framed body, or **null on a clean close** - EOF *before* a header byte, which is how a
   * stopped server's stdout ends. A truncated frame is a failed check, never a hang.
   */
  fun readFrame(input: InputStream): String? {
    val length = contentLengthOf(readHeader(input) ?: return null)
    val body = ByteArray(length)
    var read = 0
    while (read < length) {
      val n = input.read(body, read, length - read)
      check(n > 0) { "stream closed mid-body after $read/$length bytes" }
      read += n
    }
    return String(body, Charsets.UTF_8)
  }

  /** The header block as text - null only when the stream is closed before any byte arrives. */
  private fun readHeader(input: InputStream): String? {
    val header = StringBuilder()
    while (!header.endsWith("\r\n\r\n")) {
      val b = input.read()
      if (b < 0 && header.isEmpty()) return null
      check(b >= 0) { "stream closed mid-header after '$header'" }
      header.append(b.toChar())
    }
    return header.toString()
  }

  private fun contentLengthOf(header: String): Int = CONTENT_LENGTH.find(header)?.groupValues?.get(1)?.toInt()
    ?: error("no Content-Length in header '$header'")

  private val CONTENT_LENGTH = Regex("""Content-Length:\s*(\d+)""")
}
