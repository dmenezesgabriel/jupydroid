package com.jupydroid.app.data.intent

import com.jupydroid.app.domain.port.SharedDocumentTooLarge
import java.io.ByteArrayOutputStream
import java.io.InputStream

private const val CHUNK_BYTES = 4096

/**
 * Every byte of this stream, up to [limitBytes]; one more and the read stops with
 * [SharedDocumentTooLarge], having held at most [limitBytes] plus a chunk.
 *
 * Bounded by what was *read* rather than by a length the provider reports, which it may leave out
 * or get wrong. Chunks of 4 KiB are `termux-app@3df69d1
 * app/src/main/java/com/termux/app/api/file/FileReceiverActivity.java` `saveStreamWithName`'s.
 *
 * ```
 * stream.readBoundedBytes(limitBytes = 15 * 1024 * 1024)
 * ```
 */
fun InputStream.readBoundedBytes(limitBytes: Int): ByteArray {
  val held = ByteArrayOutputStream()
  val chunk = ByteArray(CHUNK_BYTES)
  while (true) {
    val count = read(chunk)
    if (count < 0) return held.toByteArray()
    if (held.size() + count > limitBytes) throw SharedDocumentTooLarge(limitBytes)
    held.write(chunk, 0, count)
  }
}
