package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.port.ErrorLog

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

  /** What has been appended, oldest first. */
  val entries: List<ErrorLogEntry> get() = held.toList()

  override suspend fun append(entry: ErrorLogEntry) {
    held += entry
  }

  /** Memory holds one process's entries, so there is nothing to bound. */
  override suspend fun prune() = Unit
}
