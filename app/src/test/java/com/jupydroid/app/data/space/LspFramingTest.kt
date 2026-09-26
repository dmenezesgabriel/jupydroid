package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * The wire codec LSP shares with DAP: `Content-Length: <n>\r\n` plus any other header lines,
 * a blank line, then <n> bytes of body. pylsp adds a `Content-Type` line of its own, which is
 * why the parser reads headers until the blank line rather than for one line only.
 */
class LspFramingTest {
  @Test
  fun writeFrame_emitsTheContentLengthHeaderAndBody() {
    val out = ByteArrayOutputStream()

    LspFraming.writeFrame(out, "{}")

    assertEquals("Content-Length: 2\r\n\r\n{}", out.toString(Charsets.US_ASCII))
  }

  @Test
  fun writeFrame_countsBytesNotCharacters() {
    val out = ByteArrayOutputStream()

    LspFraming.writeFrame(out, "\"λ\"")

    assertEquals("Content-Length: 4\r\n\r\n\"λ\"", out.toString(Charsets.UTF_8))
  }

  @Test
  fun readFrame_readsTheBodyTheHeaderAnnounces() {
    val stream = ByteArrayInputStream("Content-Length: 2\r\n\r\n{}".toByteArray())

    assertEquals("{}", LspFraming.readFrame(stream))
  }

  /** pylsp's own reply shape: a Content-Type line between length and blank line. */
  @Test
  fun readFrame_ignoresHeadersOtherThanContentLength() {
    val wire =
      "Content-Length: 2\r\nContent-Type: application/vscode-jsonrpc; charset=utf8\r\n\r\n{}"
    val stream = ByteArrayInputStream(wire.toByteArray())

    assertEquals("{}", LspFraming.readFrame(stream))
  }

  @Test
  fun readFrame_readsTwoFramesBackToBack() {
    val wire = "Content-Length: 2\r\n\r\n{}Content-Length: 3\r\n\r\n[1]"
    val stream = ByteArrayInputStream(wire.toByteArray())

    assertEquals("{}", LspFraming.readFrame(stream))
    assertEquals("[1]", LspFraming.readFrame(stream))
  }

  /** A clean close is EOF *before* a header byte - the way a stopped server's stdout ends. */
  @Test
  fun readFrame_returnsNullOnEofBeforeAnyHeaderByte() {
    val stream = ByteArrayInputStream(ByteArray(0))

    assertNull(LspFraming.readFrame(stream))
  }

  /** A truncated stream is a failed check, not a hang and not a clean close. */
  @Test
  fun readFrame_failsOnEofMidHeader() {
    val stream = ByteArrayInputStream("Content-Length: 10\r\n".toByteArray())

    assertThrows(IllegalStateException::class.java) { LspFraming.readFrame(stream) }
  }

  @Test
  fun readFrame_failsOnEofMidBody() {
    val stream = ByteArrayInputStream("Content-Length: 10\r\n\r\n{}".toByteArray())

    assertThrows(IllegalStateException::class.java) { LspFraming.readFrame(stream) }
  }

  @Test
  fun readFrame_failsWhenNoContentLengthArrives() {
    val stream = ByteArrayInputStream("Content-Type: text/plain\r\n\r\n{}".toByteArray())

    assertThrows(IllegalStateException::class.java) { LspFraming.readFrame(stream) }
  }
}
