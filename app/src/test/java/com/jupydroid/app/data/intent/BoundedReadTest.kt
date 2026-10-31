package com.jupydroid.app.data.intent

import com.jupydroid.app.domain.port.SharedDocumentTooLarge
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream

/**
 * [readBoundedBytes] is the whole of the reader's defence against a provider that serves more than
 * an import can hold, so it is tested on a stream that never ends: a read that is not bounded
 * would not fail here, it would never return.
 */
class BoundedReadTest {
  @Test
  fun aStreamWithinTheLimitIsReadWhole() {
    val bytes = ByteArray(10_000) { it.toByte() }

    assertArrayEquals(bytes, ByteArrayInputStream(bytes).readBoundedBytes(limitBytes = 10_000))
  }

  @Test
  fun aStreamOneByteOverTheLimitIsRefused() {
    val refused = runCatching { ByteArrayInputStream(ByteArray(101)).readBoundedBytes(limitBytes = 100) }

    assertEquals(100, (refused.exceptionOrNull() as SharedDocumentTooLarge).limitBytes)
  }

  @Test
  fun anEndlessStreamIsRefusedAfterTheLimitPlusOneChunkAtMost() {
    val endless = CountingEndlessStream()

    val refused = runCatching { endless.readBoundedBytes(limitBytes = 50_000) }

    assertTrue("expected a refusal, got $refused", refused.exceptionOrNull() is SharedDocumentTooLarge)
    assertTrue("read ${endless.served} bytes of an endless stream", endless.served <= 50_000 + 4096)
  }

  /** A provider that never stops serving, remembering how much it was asked for. */
  private class CountingEndlessStream : InputStream() {
    var served = 0L
      private set

    override fun read(): Int = 0.also { served++ }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int = length.also { served += it }
  }
}
