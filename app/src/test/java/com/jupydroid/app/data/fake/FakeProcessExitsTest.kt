package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.model.ProcessExit
import com.jupydroid.app.domain.model.ProcessExitReason
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class FakeProcessExitsTest {
  private fun exitAt(timestampMillis: Long) =
    ProcessExit(timestampMillis, pid = 1, reason = ProcessExitReason.ANR, description = "")

  @Test
  fun everythingIsUnreportedUntilSomethingIsMarked() = runTest {
    val exits = FakeProcessExits(listOf(exitAt(1), exitAt(2)))

    assertEquals(listOf(exitAt(1), exitAt(2)), exits.unreported())
  }

  @Test
  fun markingReportedHidesThoseExitsAndEveryOlderOne() = runTest {
    val exits = FakeProcessExits(listOf(exitAt(1), exitAt(2), exitAt(3)))

    exits.markReported(listOf(exitAt(2)))

    assertEquals(listOf(exitAt(3)), exits.unreported())
  }

  @Test
  fun markingNothingKeepsTheMark() = runTest {
    val exits = FakeProcessExits(listOf(exitAt(1)))

    exits.markReported(emptyList())

    assertEquals(listOf(exitAt(1)), exits.unreported())
  }
}
