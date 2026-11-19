package com.jupydroid.app.data.crash

import com.jupydroid.app.data.fake.FakeErrorLog
import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.model.ErrorLogKind
import com.jupydroid.app.domain.model.ErrorLogOrigin
import com.jupydroid.app.domain.model.ErrorLogSummary
import com.jupydroid.app.domain.port.ErrorLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** The handler owes what a person reading the log, and the platform's default handler, can observe. */
class ErrorLogUncaughtExceptionHandlerTest {
  private val origin = ErrorLogOrigin(appVersion = "0.9.1", deviceModel = "Pixel 9", androidVersion = "16")
  private val crashedAt = 1_700_000_000_000L
  private val log = FakeErrorLog()

  private class RecordingDefaultHandler(private val log: FakeErrorLog) : Thread.UncaughtExceptionHandler {
    var thread: Thread? = null
    var throwable: Throwable? = null
    var entriesHeldWhenCalled: Int = -1

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
      this.thread = thread
      this.throwable = throwable
      entriesHeldWhenCalled = log.entries.size
    }
  }

  private class RefusingErrorLog : ErrorLog {
    override suspend fun append(entry: ErrorLogEntry): Unit = throw java.io.IOException("disk full")

    override suspend fun prune() = Unit

    override suspend fun summary() = ErrorLogSummary(entryCount = 0, sizeBytes = 0)

    override suspend fun clear() = Unit
  }

  private fun handlerOver(errorLog: ErrorLog, next: Thread.UncaughtExceptionHandler?) =
    ErrorLogUncaughtExceptionHandler({ errorLog }, origin, next, clock = { crashedAt })

  private fun crashOn(name: String, cause: Throwable) = Thread({}, name).let { it to cause }

  @Test
  fun aCrashAppendsOneEntryNamingTheExceptionItsStackItsThreadAndTheBuild() {
    val (thread, failure) = crashOn("worker-3", IllegalStateException("kernel gone"))

    handlerOver(log, next = null).uncaughtException(thread, failure)

    val entry = log.entries.single()
    assertEquals(ErrorLogKind.CRASH, entry.kind)
    assertEquals("java.lang.IllegalStateException", entry.exceptionClass)
    assertEquals("kernel gone", entry.message)
    assertTrue(entry.stack.contains("aCrashAppendsOneEntryNamingTheExceptionItsStackItsThreadAndTheBuild"))
    assertEquals("worker-3", entry.thread)
    assertEquals(crashedAt, entry.timestampMillis)
    assertEquals(listOf("0.9.1", "Pixel 9", "16"), listOf(entry.appVersion, entry.deviceModel, entry.androidVersion))
  }

  @Test
  fun aCauseIsInTheStackSoTheLineSaysWhatReallyFailed() {
    val (thread, failure) = crashOn("main", RuntimeException("wrapper", java.io.IOException("socket closed")))

    handlerOver(log, next = null).uncaughtException(thread, failure)

    assertTrue(log.entries.single().stack.contains("java.io.IOException: socket closed"))
  }

  @Test
  fun theDefaultHandlerReceivesTheSameCrashAfterTheEntryIsOnTheLog() {
    val default = RecordingDefaultHandler(log)
    val (thread, failure) = crashOn("main", IllegalStateException("boom"))

    handlerOver(log, next = default).uncaughtException(thread, failure)

    assertSame(thread, default.thread)
    assertSame(failure, default.throwable)
    assertEquals(1, default.entriesHeldWhenCalled)
  }

  @Test
  fun theDefaultHandlerStillRunsWhenTheLogRefusesTheEntryAndTheRefusalIsNotHidden() {
    val default = RecordingDefaultHandler(log)
    val (thread, failure) = crashOn("main", IllegalStateException("boom"))

    val refusal = assertThrows(java.io.IOException::class.java) {
      handlerOver(RefusingErrorLog(), next = default).uncaughtException(thread, failure)
    }

    assertEquals("disk full", refusal.message)
    assertSame(failure, default.throwable)
  }

  @Test
  fun aCrashWithNoDefaultHandlerIsStillRecorded() {
    val (thread, failure) = crashOn("main", IllegalStateException("boom"))

    handlerOver(log, next = null).uncaughtException(thread, failure)

    assertEquals(1, log.entries.size)
  }
}
