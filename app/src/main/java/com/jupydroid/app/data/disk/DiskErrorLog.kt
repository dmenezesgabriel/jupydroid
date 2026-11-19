package com.jupydroid.app.data.disk

import com.jupydroid.app.domain.model.ErrorLogCaps
import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.port.ErrorLog
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * An entry measured 2.9 KB with a real stack and 18.7 KB with the same thrown 150 frames deep
 * (`DiskErrorLogOnDeviceTest.anEntryWithARealStackHasAMeasuredSize`, dev36), so 1 MiB holds about 360 of the
 * first or 56 of the second, and a week is time enough to be read or pulled.
 */
private val DEFAULT_ERROR_LOG_CAPS = ErrorLogCaps(maxBytes = 1_048_576, maxAgeDays = 7)

private val DAY_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC)

/** The file one UTC day's entries go to, so an age cap can drop a day by deleting a file. */
internal fun errorLogFileNameFor(timestampMillis: Long): String =
  "errors-${DAY_FORMAT.format(Instant.ofEpochMilli(timestampMillis))}.jsonl"

/**
 * The error log as files in [directory] - one JSON line per entry, one file per UTC day - read by a person
 * over `adb pull` as well as by the app.
 *
 * An entry is **redacted when it is written** ([redactSecrets] on its message and stack), not when it is
 * shared: on Android 10 and below another app holding the storage permission can read the app-specific
 * external directory this is meant to sit in.
 *
 * `encodeDefaults` is on so every line carries its `schemaVersion`: a default the encoder leaves out is a
 * version a reader cannot see.
 *
 * It appends and syncs rather than replacing the file the way [writeAtomically] does: a crash handler
 * has no time to copy a day's log, and the only damage an append can leave is a torn last line, which
 * a reader skips.
 *
 * ```
 * DiskErrorLog(context.getExternalFilesDir(null)!!)
 * ```
 */
class DiskErrorLog(
  private val directory: File,
  private val caps: ErrorLogCaps = DEFAULT_ERROR_LOG_CAPS,
  private val clock: () -> Long = System::currentTimeMillis,
  private val json: Json = Json { encodeDefaults = true },
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ErrorLog {
  /** One writer at a time, so two entries never interleave inside a line. */
  private val writeMutex = Mutex()

  override suspend fun append(entry: ErrorLogEntry) = withContext(ioDispatcher) {
    val line = json.encodeToString(ErrorLogEntry.serializer(), entry.redacted()) + "\n"
    writeMutex.withLock {
      directory.mkdirs()
      val file = File(directory, errorLogFileNameFor(entry.timestampMillis))
      // A crash loop appends the same failure until it fills the disk; the day keeps its first entries.
      if (file.length() >= caps.maxBytes) return@withLock
      val opensTheDay = !file.exists()
      appendSynced(file, line)
      if (opensTheDay) pruneLocked()
    }
  }

  override suspend fun prune() = withContext(ioDispatcher) { writeMutex.withLock { pruneLocked() } }

  private fun pruneLocked() = filesToPrune(directory, caps, clock()).forEach(File::delete)

  private fun ErrorLogEntry.redacted() = copy(message = redactSecrets(message), stack = redactSecrets(stack))

  private fun appendSynced(file: File, line: String) = FileOutputStream(file, true).use { stream ->
    stream.write(line.toByteArray(Charsets.UTF_8))
    stream.fd.sync()
  }
}
