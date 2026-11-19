package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.model.ErrorLogKind
import com.jupydroid.app.domain.model.ErrorLogSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeErrorLogTest {
  @Test
  fun entriesAreHeldInTheOrderTheyWereAppended() = runTest {
    val log = FakeErrorLog()
    val first = ErrorLogEntry(timestampMillis = 1, kind = ErrorLogKind.CRASH)
    val second = ErrorLogEntry(timestampMillis = 2, kind = ErrorLogKind.ERROR)

    log.append(first)
    log.append(second)

    assertEquals(listOf(first, second), log.entries)
  }

  @Test
  fun pruningLeavesWhatWasAppended() = runTest {
    val log = FakeErrorLog()
    val held = ErrorLogEntry(timestampMillis = 1, kind = ErrorLogKind.CRASH)
    log.append(held)

    log.prune()

    assertEquals(listOf(held), log.entries)
  }

  @Test
  fun theSummaryCountsWhatWasAppendedAndHasASize() = runTest {
    val log = FakeErrorLog()
    log.append(ErrorLogEntry(timestampMillis = 1, kind = ErrorLogKind.CRASH, message = "boom"))
    log.append(ErrorLogEntry(timestampMillis = 2, kind = ErrorLogKind.ERROR, message = "bang"))

    val summary = log.summary()

    assertEquals(2, summary.entryCount)
    assertTrue("${summary.sizeBytes} bytes", summary.sizeBytes > 0)
  }

  @Test
  fun clearingLeavesNothingToSummarise() = runTest {
    val log = FakeErrorLog()
    log.append(ErrorLogEntry(timestampMillis = 1, kind = ErrorLogKind.CRASH))

    log.clear()

    assertEquals(emptyList<ErrorLogEntry>(), log.entries)
    assertEquals(ErrorLogSummary(entryCount = 0, sizeBytes = 0), log.summary())
  }
}
