package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.ErrorLogCaps
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeParseException

private val DAY_FILE_NAME = Regex("""errors-(\d{4}-\d{2}-\d{2})\.jsonl""")

/**
 * The UTC day a log file holds, or null for any other name - a stray file in the directory is not ours to
 * delete, and neither is `errors-2023-13-45.jsonl`, which names no day.
 */
internal fun errorLogDayOf(fileName: String): LocalDate? {
  val day = DAY_FILE_NAME.matchEntire(fileName)?.groupValues?.get(1) ?: return null
  return try {
    LocalDate.parse(day)
  } catch (notADay: DateTimeParseException) {
    null
  }
}

/**
 * The day files in [directory] that [caps] no longer allow at [nowMillis]: every one older than the age cap,
 * then the oldest of the rest until the total fits the size cap. The newest day is never taken for its size,
 * since the entry just written lives in it.
 */
internal fun filesToPrune(directory: File, caps: ErrorLogCaps, nowMillis: Long): List<File> {
  val today = Instant.ofEpochMilli(nowMillis).atZone(ZoneOffset.UTC).toLocalDate()
  val dayFiles = directory.listFiles().orEmpty()
    .mapNotNull { file -> errorLogDayOf(file.name)?.let { day -> day to file } }
    .sortedBy { (day, _) -> day }
  val (stale, kept) = dayFiles.partition { (day, _) -> day.isBefore(today.minusDays(caps.maxAgeDays.toLong())) }
  return (stale + kept.oldestOverSize(caps.maxBytes)).map { (_, file) -> file }
}

private fun List<Pair<LocalDate, File>>.oldestOverSize(maxBytes: Long): List<Pair<LocalDate, File>> {
  var total = sumOf { (_, file) -> file.length() }
  val taken = mutableListOf<Pair<LocalDate, File>>()
  for (dayFile in dropLast(1)) {
    if (total <= maxBytes) break
    taken += dayFile
    total -= dayFile.second.length()
  }
  return taken
}
