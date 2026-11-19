package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.ErrorLogEntry

/**
 * Where the app keeps a record of its crashes and errors, so one survives the process that had it.
 *
 * Nothing in it leaves the device. An implementation never lets a secret reach its store: callers hand
 * over the entry as it happened and are not asked to clean it first.
 *
 * ```
 * errorLog.append(ErrorLogEntry(timestampMillis = now, kind = ErrorLogKind.ERROR, message = "refused"))
 * ```
 */
interface ErrorLog {
  /** Records [entry] after the ones already there; it is on stable storage when this returns. */
  suspend fun append(entry: ErrorLogEntry)

  /**
   * Drops what the log's caps no longer allow, oldest first, so it never grows without being asked.
   * An implementation also does this on its own whenever it starts a new day's file.
   */
  suspend fun prune()
}
