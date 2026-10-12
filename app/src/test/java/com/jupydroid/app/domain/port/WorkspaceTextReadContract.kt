package com.jupydroid.app.domain.port

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [WorkspaceRepository] owes a file asked for as text when it is not text: a refusal,
 * never a string that would be saved back over it.
 *
 * The rule is the server's own. `jupyter_server/services/contents/fileio.py` `_read_file` decodes a
 * file asked for with `format="text"` as UTF-8 strictly and answers 400 "is not UTF-8 encoded" on
 * anything else - and UTF-8 is what [WorkspaceRepository.writeText] writes, so a file that passes
 * is one a save writes back byte for byte. Seeded through `writeBytes`, the port's own way to hold
 * content it did not author, so the fake is held to this beside the disk.
 */
abstract class WorkspaceTextReadContract {
  protected abstract fun repository(): WorkspaceRepository

  @Test
  fun anImageIsRefusedAsText() = runTest {
    val repository = repository()
    repository.writeBytes("plot.png", PNG_HEADER)

    val refusal = runCatching { repository.readStampedText("plot.png") }

    assertTrue("expected a refusal, got $refusal", refusal.exceptionOrNull() is FileNotTextException)
  }

  /** Not a NUL in sight, and still not what a save would write back: `é` alone is one byte in Latin-1. */
  @Test
  fun textInAnotherEncodingIsRefusedAsText() = runTest {
    val repository = repository()
    repository.writeBytes("latin.txt", LATIN_1_CAFE)

    val refusal = runCatching { repository.readText("latin.txt") }

    assertTrue("expected a refusal, got $refusal", refusal.exceptionOrNull() is FileNotTextException)
  }

  @Test
  fun theRefusalNamesTheFileAndWhatWasExpected() = runTest {
    val repository = repository()
    repository.writeBytes("plot.png", PNG_HEADER)

    val message = runCatching { repository.readText("plot.png") }.exceptionOrNull()?.message.orEmpty()

    assertTrue("expected the path in '$message'", "plot.png" in message)
    assertTrue("expected the encoding in '$message'", "UTF-8" in message)
  }

  /** The refusal is of the string, not of the file: an export or a viewer still reads it whole. */
  @Test
  fun aFileRefusedAsTextStillReadsAsBytes() = runTest {
    val repository = repository()
    repository.writeBytes("plot.png", PNG_HEADER)
    runCatching { repository.readText("plot.png") }

    assertArrayEquals(PNG_HEADER, repository.readBytes("plot.png"))
  }

  /** What the rule must not cost: text past ASCII, and a byte-order mark, go out as they came in. */
  @Test
  fun textPastAsciiIsReadAndWrittenBackByteForByte() = runTest {
    val repository = repository()
    val marked = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "café ☕ 日本語\n".toByteArray()
    repository.writeBytes("notes.txt", marked)

    val read = repository.readStampedText("notes.txt")
    repository.writeText("notes.txt", read.text, read.stamp)

    assertEquals("﻿café ☕ 日本語\n", read.text)
    assertArrayEquals(marked, repository.readBytes("notes.txt"))
  }

  private companion object {
    val PNG_HEADER = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00)
    val LATIN_1_CAFE = byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte(), 0x0A)
  }
}
