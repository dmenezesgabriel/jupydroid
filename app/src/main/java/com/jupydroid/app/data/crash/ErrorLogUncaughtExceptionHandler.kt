package com.jupydroid.app.data.crash

import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.model.ErrorLogKind
import com.jupydroid.app.domain.model.ErrorLogOrigin
import com.jupydroid.app.domain.port.ErrorLog
import kotlinx.coroutines.runBlocking

/**
 * Records an uncaught exception as a `CRASH` entry, then hands it to [next] - the platform's own handler,
 * which is what ends the process and shows the crash. The shape is Termux's
 * (`termux-app@3df69d1da197`, `termux-shared/src/main/java/com/termux/shared/crash/CrashHandler.java`).
 *
 * [next] runs in a `finally`: a log that cannot be written must neither keep the default handler from running
 * nor be swallowed - the runtime ignores what a handler throws, and the throw says why the entry is missing.
 * The write blocks the dying thread on purpose: the entry has to be on disk before the process is gone.
 *
 * [errorLog] is a provider so installing the handler in `Application.onCreate` builds nothing: the container,
 * and with it the one writer whose mutex keeps lines from interleaving, is asked for only when something crashes.
 * Redaction happens in the writer, so this hands over the exception as it happened.
 *
 * ```
 * Thread.setDefaultUncaughtExceptionHandler(
 *   ErrorLogUncaughtExceptionHandler({ container.errorLog }, origin, Thread.getDefaultUncaughtExceptionHandler()),
 * )
 * ```
 */
class ErrorLogUncaughtExceptionHandler(
  private val errorLog: () -> ErrorLog,
  private val origin: ErrorLogOrigin,
  private val next: Thread.UncaughtExceptionHandler?,
  private val clock: () -> Long = System::currentTimeMillis,
) : Thread.UncaughtExceptionHandler {
  override fun uncaughtException(thread: Thread, throwable: Throwable) {
    try {
      runBlocking { errorLog().append(crashEntry(thread, throwable)) }
    } finally {
      next?.uncaughtException(thread, throwable)
    }
  }

  private fun crashEntry(thread: Thread, throwable: Throwable) = ErrorLogEntry(
    timestampMillis = clock(),
    kind = ErrorLogKind.CRASH,
    exceptionClass = throwable.javaClass.name,
    message = throwable.message.orEmpty(),
    stack = throwable.stackTraceToString(),
    thread = thread.name,
    appVersion = origin.appVersion,
    deviceModel = origin.deviceModel,
    androidVersion = origin.androidVersion,
  )
}
