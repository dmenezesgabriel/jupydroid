package com.jupydroid.app.data.space

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/** Reading the server's own log into the parts of its boot, on lines shaped as jupyter_server 2.20.0 writes them. */
class ServerBootSplitTest {
  private val log = listOf(
    "[D 2026-10-08 23:32:02.830 ServerApp] Searching ['/home/jupydroid/.jupyter'] for config files",
    "[I 2026-10-08 23:32:03.900 ServerApp] jupyter_server_terminals | extension was successfully loaded.",
    "[I 2026-10-08 23:32:04.100 ServerApp] Jupyter Server 2.20.0 is running at:",
    "[I 2026-10-08 23:32:04.100 ServerApp] http://127.0.0.1:40723/?token=...",
    "    a continuation line, which carries no timestamp of its own",
  )
  private val spawnedAt = Instant.parse("2026-10-08T23:32:01.000Z").toEpochMilli()

  @Test
  fun aLineIsReadAsUtcWithItsMessage() {
    val line = serverLogLines(log).first()

    assertEquals(Instant.parse("2026-10-08T23:32:02.830Z").toEpochMilli(), line.epochMs)
    assertEquals("Searching ['/home/jupydroid/.jupyter'] for config files", line.message)
  }

  @Test
  fun aContinuationLineIsSkipped() {
    assertEquals(4, serverLogLines(log).size)
  }

  @Test
  fun theBootSplitsAtTheFirstLineAndAtIsRunningAt() {
    val split = ServerBootSplit.of(spawnedAt, totalMs = 3_200, serverLogLines(log))

    assertEquals(1_830, split.toFirstLineMs)
    assertEquals(1_270, split.toRunningMs)
    assertEquals(100, split.toReadyMs)
  }

  @Test
  fun theWidestGapIsNamedByTheLineThatEndsIt() {
    val split = ServerBootSplit.of(spawnedAt, totalMs = 3_200, serverLogLines(log))

    val widest = split.widestGaps(1)

    assertTrue(widest, "1070 ms" in widest && "extension was successfully loaded" in widest)
  }

  @Test
  fun aLogThatNeverRanIsRefusedWithWhatItHeld() {
    val lines = serverLogLines(log.take(2))

    val refusal = assertThrows(IllegalStateException::class.java) { ServerBootSplit.of(spawnedAt, 3_200, lines) }

    assertTrue(refusal.message.orEmpty(), "is running at" in refusal.message.orEmpty())
  }

  @Test
  fun aFirstLineBeforeTheSpawnIsRefused() {
    val refusal = assertThrows(IllegalStateException::class.java) {
      ServerBootSplit.of(spawnedAt + 5_000, 3_200, serverLogLines(log))
    }

    assertTrue(refusal.message.orEmpty(), "UTC" in refusal.message.orEmpty())
  }
}
