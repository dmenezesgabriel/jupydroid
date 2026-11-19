package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.model.ErrorLogSummary
import com.jupydroid.app.domain.port.ErrorLog
import kotlinx.serialization.json.Json

/**
 * An error log held in memory - [AppContainer][com.jupydroid.app.di.AppContainer]'s default, and what a
 * device with no external storage keeps, so nothing that reports an error has to ask whether it can.
 *
 * ```
 * FakeErrorLog().also { it.append(entry) }.entries
 * ```
 */
class FakeErrorLog : ErrorLog {
  private val held = mutableListOf<ErrorLogEntry>()

  /** Encoded as the disk adapter writes a line, so the size shown on a device with no external storage is honest. */
  private val json = Json { encodeDefaults = true }

  /** What has been appended, oldest first. */
  val entries: List<ErrorLogEntry> get() = held.toList()

  override suspend fun append(entry: ErrorLogEntry) {
    held += entry
  }

  /** Memory holds one process's entries, so there is nothing to bound. */
  override suspend fun prune() = Unit

  override suspend fun summary() = ErrorLogSummary(
    entryCount = held.size,
    sizeBytes = held.sumOf { entry ->
      (json.encodeToString(ErrorLogEntry.serializer(), entry) + "\n").toByteArray(Charsets.UTF_8).size.toLong()
    },
  )

  override suspend fun clear() = held.clear()
}
