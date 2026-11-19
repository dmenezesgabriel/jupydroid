package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.model.ErrorLogKind
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
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
}
