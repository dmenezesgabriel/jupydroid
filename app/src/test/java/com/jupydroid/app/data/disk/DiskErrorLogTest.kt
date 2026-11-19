package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.model.ErrorLogKind
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The disk log owes what a person pulling its files over `adb` can observe: lines, days and no token. */
class DiskErrorLogTest {
  @get:Rule val temp = TemporaryFolder()

  private val logDir: File get() = File(temp.root, "not-yet-created/logs")
  private val token = "0123456789abcdef0123456789abcdef"
  private val firstDay = 1_700_000_000_000L // 2023-11-14T22:13:20Z
  private val nextDay = firstDay + 24 * 60 * 60 * 1000

  private fun entry(at: Long = firstDay, message: String = "boom") =
    ErrorLogEntry(timestampMillis = at, kind = ErrorLogKind.CRASH, message = message)

  private fun lines(file: File): List<String> = file.readLines()

  @Test
  fun anEntryIsOneJsonLineThatReadsBackAsItself() = runTest {
    DiskErrorLog(logDir).append(entry())

    val file = File(logDir, "errors-2023-11-14.jsonl")
    assertEquals(listOf(entry()), lines(file).map { Json.decodeFromString(ErrorLogEntry.serializer(), it) })
  }

  /** A reader groups lines by shape, so the version is on every line, not left out as a default. */
  @Test
  fun everyLineStatesItsSchemaVersion() = runTest {
    DiskErrorLog(logDir).append(entry())

    assertTrue(lines(File(logDir, "errors-2023-11-14.jsonl")).single().contains("\"schemaVersion\":1"))
  }

  @Test
  fun aSecondEntryIsAppendedNotReplacing() = runTest {
    val log = DiskErrorLog(logDir)

    log.append(entry(message = "first"))
    log.append(entry(message = "second"))

    val decoded = lines(File(logDir, "errors-2023-11-14.jsonl"))
      .map { Json.decodeFromString(ErrorLogEntry.serializer(), it).message }
    assertEquals(listOf("first", "second"), decoded)
  }

  @Test
  fun aLaterDayOpensItsOwnFile() = runTest {
    val log = DiskErrorLog(logDir)

    log.append(entry(at = firstDay))
    log.append(entry(at = nextDay))

    assertEquals(
      listOf("errors-2023-11-14.jsonl", "errors-2023-11-15.jsonl"),
      logDir.list().orEmpty().sorted(),
    )
  }

  @Test
  fun noTokenReachesTheFile() = runTest {
    val leaking = entry(message = "Authorization: Bearer $token").copy(stack = "at x\nGET /api?token=$token")

    DiskErrorLog(logDir).append(leaking)

    assertFalse(File(logDir, "errors-2023-11-14.jsonl").readText().contains(token))
  }

  @Test
  fun concurrentAppendsEachLeaveAWholeLine() = runTest {
    val log = DiskErrorLog(logDir)

    (1..20).map { index -> async { log.append(entry(message = "m$index")) } }.awaitAll()

    val decoded = lines(File(logDir, "errors-2023-11-14.jsonl"))
      .map { Json.decodeFromString(ErrorLogEntry.serializer(), it).message }
    assertEquals((1..20).map { "m$it" }.sorted(), decoded.sorted())
  }
}
