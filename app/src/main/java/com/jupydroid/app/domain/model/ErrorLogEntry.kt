package com.jupydroid.app.domain.model

import kotlinx.serialization.Serializable

/** What an [ErrorLogEntry] records; `ANR`, `NATIVE` and `EXIT` come from the platform, where no handler runs. */
@Serializable
enum class ErrorLogKind { CRASH, ANR, NATIVE, EXIT, ERROR }

/**
 * One event worth keeping after the process that saw it is gone - a line of the app's own error log.
 *
 * [timestampMillis] is epoch milliseconds and says which day's file the entry belongs to. [message] and
 * [stack] may carry a server token when a failed request is the cause, which is why the log's writer
 * redacts them before a byte lands. New fields must carry a default: a file written by an older build is
 * read with `ignoreUnknownKeys`, and [schemaVersion] says which shape a line has.
 *
 * ```
 * ErrorLogEntry(timestampMillis = now, kind = ErrorLogKind.CRASH, exceptionClass = "java.lang.IllegalStateException")
 * ```
 */
@Serializable
data class ErrorLogEntry(
  val timestampMillis: Long,
  val kind: ErrorLogKind,
  val exceptionClass: String = "",
  val message: String = "",
  val stack: String = "",
  val thread: String = "",
  val appVersion: String = "",
  val deviceModel: String = "",
  val androidVersion: String = "",
  val schemaVersion: Int = 1,
)
