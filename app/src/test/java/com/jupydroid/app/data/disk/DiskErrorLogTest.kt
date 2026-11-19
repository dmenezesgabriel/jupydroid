package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.ErrorLogCaps
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

  /** The clock reads the day after [firstDay], so a week's age cap keeps both days the tests write. */
  private fun diskLog(caps: ErrorLogCaps = ErrorLogCaps(maxBytes = 1_048_576, maxAgeDays = 7)) =
    DiskErrorLog(logDir, caps = caps, clock = { nextDay })

  private fun planted(day: String, bytes: Int): File =
    File(logDir.also { it.mkdirs() }, "errors-$day.jsonl").apply { writeText("x".repeat(bytes)) }

  private fun lines(file: File): List<String> = file.readLines()

  @Test
  fun anEntryIsOneJsonLineThatReadsBackAsItself() = runTest {
    diskLog().append(entry())

    val file = File(logDir, "errors-2023-11-14.jsonl")
    assertEquals(listOf(entry()), lines(file).map { Json.decodeFromString(ErrorLogEntry.serializer(), it) })
  }

  /** A reader groups lines by shape, so the version is on every line, not left out as a default. */
  @Test
  fun everyLineStatesItsSchemaVersion() = runTest {
    diskLog().append(entry())

    assertTrue(lines(File(logDir, "errors-2023-11-14.jsonl")).single().contains("\"schemaVersion\":1"))
  }

  @Test
  fun aSecondEntryIsAppendedNotReplacing() = runTest {
    val log = diskLog()

    log.append(entry(message = "first"))
    log.append(entry(message = "second"))

    val decoded = lines(File(logDir, "errors-2023-11-14.jsonl"))
      .map { Json.decodeFromString(ErrorLogEntry.serializer(), it).message }
    assertEquals(listOf("first", "second"), decoded)
  }

  @Test
  fun aLaterDayOpensItsOwnFile() = runTest {
    val log = diskLog()

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

    diskLog().append(leaking)

    assertFalse(File(logDir, "errors-2023-11-14.jsonl").readText().contains(token))
  }

  @Test
  fun concurrentAppendsEachLeaveAWholeLine() = runTest {
    val log = diskLog()

    (1..20).map { index -> async { log.append(entry(message = "m$index")) } }.awaitAll()

    val decoded = lines(File(logDir, "errors-2023-11-14.jsonl"))
      .map { Json.decodeFromString(ErrorLogEntry.serializer(), it).message }
    assertEquals((1..20).map { "m$it" }.sorted(), decoded.sorted())
  }

  @Test
  fun pruningDeletesADayOlderThanTheAgeCapAndKeepsOneInsideIt() = runTest {
    planted("2023-11-07", 10)
    planted("2023-11-08", 10)

    diskLog(ErrorLogCaps(maxBytes = 1_000, maxAgeDays = 7)).prune()

    assertEquals(listOf("errors-2023-11-08.jsonl"), logDir.list().orEmpty().sorted())
  }

  @Test
  fun overTheSizeCapTheOldestDayGoesFirst() = runTest {
    listOf("2023-11-12", "2023-11-13", "2023-11-14").forEach { planted(it, 400) }

    diskLog(ErrorLogCaps(maxBytes = 1_000, maxAgeDays = 7)).prune()

    assertEquals(
      listOf("errors-2023-11-13.jsonl", "errors-2023-11-14.jsonl"),
      logDir.list().orEmpty().sorted(),
    )
  }

  /** The entry just written lives in the newest file, so a size cap never takes it. */
  @Test
  fun theNewestDayStaysEvenWhenItAloneIsOverTheSizeCap() = runTest {
    planted("2023-11-14", 5_000)

    diskLog(ErrorLogCaps(maxBytes = 1_000, maxAgeDays = 7)).prune()

    assertEquals(listOf("errors-2023-11-14.jsonl"), logDir.list().orEmpty().toList())
  }

  @Test
  fun aFileThatIsNotADaysLogIsNeverTouched() = runTest {
    listOf("notes.txt", "errors-latest.jsonl", "errors-2023-13-45.jsonl").forEach {
      File(logDir.also { dir -> dir.mkdirs() }, it).writeText("x".repeat(5_000))
    }

    diskLog(ErrorLogCaps(maxBytes = 1_000, maxAgeDays = 1)).prune()

    assertEquals(
      listOf("errors-2023-13-45.jsonl", "errors-latest.jsonl", "notes.txt"),
      logDir.list().orEmpty().sorted(),
    )
  }

  /** Opening a day's file is a rotation: the log is trimmed without anyone asking. */
  @Test
  fun openingANewDaysFilePrunesWhatTheCapsNoLongerAllow() = runTest {
    planted("2023-11-01", 10)

    diskLog().append(entry(at = firstDay))

    assertEquals(listOf("errors-2023-11-14.jsonl"), logDir.list().orEmpty().toList())
  }

  /** A crash loop must not pass the cap: the day keeps its first entries, which hold the cause. */
  @Test
  fun aDayAtTheSizeCapTakesNoMoreEntries() = runTest {
    val log = diskLog(ErrorLogCaps(maxBytes = 600, maxAgeDays = 7))

    (1..20).forEach { log.append(entry(message = "m$it")) }

    val file = File(logDir, "errors-2023-11-14.jsonl")
    val messages = lines(file).map { Json.decodeFromString(ErrorLogEntry.serializer(), it).message }
    assertEquals("m1", messages.first())
    assertFalse(messages.contains("m20"))
    assertTrue("${file.length()} bytes", file.length() < 600 + lines(file).first().length + 10)
  }
}
