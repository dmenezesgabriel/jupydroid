package com.jupydroid.app.data.crash

import com.jupydroid.app.data.fake.FakeErrorLog
import com.jupydroid.app.data.fake.FakeProcessExits
import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.model.ErrorLogKind
import com.jupydroid.app.domain.model.ErrorLogOrigin
import com.jupydroid.app.domain.model.ProcessExit
import com.jupydroid.app.domain.model.ProcessExitReason
import com.jupydroid.app.domain.port.ErrorLog
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/** The recorder owes the log one entry per death worth keeping, once, and never loses one to a failed write. */
class ExitReasonRecorderTest {
  private val origin = ErrorLogOrigin(appVersion = "0.9.1", deviceModel = "Pixel 9", androidVersion = "16")
  private val log = FakeErrorLog()

  private fun exitAt(timestampMillis: Long, reason: ProcessExitReason) =
    ProcessExit(timestampMillis, pid = 7, reason = reason, description = "")

  private class RefusingErrorLog : ErrorLog {
    override suspend fun append(entry: ErrorLogEntry): Unit = throw IOException("disk full")

    override suspend fun prune() = Unit
  }

  @Test
  fun everyDeathWorthKeepingIsAppendedAndADeliberateOneIsNot() = runTest {
    val exits = FakeProcessExits(
      listOf(
        exitAt(1, ProcessExitReason.ANR),
        exitAt(2, ProcessExitReason.DELIBERATE),
        exitAt(3, ProcessExitReason.CRASH_NATIVE),
      ),
    )

    ExitReasonRecorder(exits, log, origin).recordUnreported()

    assertEquals(listOf(ErrorLogKind.ANR, ErrorLogKind.NATIVE), log.entries.map { it.kind })
    assertEquals(listOf(1L, 3L), log.entries.map { it.timestampMillis })
  }

  @Test
  fun aSecondStartAppendsNothingForDeathsAlreadyRecorded() = runTest {
    val exits = FakeProcessExits(listOf(exitAt(1, ProcessExitReason.ANR), exitAt(2, ProcessExitReason.DELIBERATE)))
    val recorder = ExitReasonRecorder(exits, log, origin)
    recorder.recordUnreported()

    recorder.recordUnreported()

    assertEquals(1, log.entries.size)
  }

  @Test
  fun aLogThatRefusesAnEntryLeavesTheDeathUnreportedForTheNextStart() = runTest {
    val exits = FakeProcessExits(listOf(exitAt(1, ProcessExitReason.ANR)))

    val failure = runCatching { ExitReasonRecorder(exits, RefusingErrorLog(), origin).recordUnreported() }

    assertTrue("the refusal reaches the caller: $failure", failure.exceptionOrNull() is IOException)
    assertEquals(listOf(exitAt(1, ProcessExitReason.ANR)), exits.unreported())
  }
}
