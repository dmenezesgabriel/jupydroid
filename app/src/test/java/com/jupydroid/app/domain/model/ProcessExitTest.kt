package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What a person reading the log sees of a death the platform recorded. */
class ProcessExitTest {
  private val origin = ErrorLogOrigin(appVersion = "0.9.1", deviceModel = "Pixel 9", androidVersion = "16")

  private fun exitFor(reason: ProcessExitReason) =
    ProcessExit(timestampMillis = 1_700_000_000_000L, pid = 3363, reason = reason, description = "shell-induced crash")

  @Test
  fun anAnrIsAnAnrEntryNamingTheReasonThePidAndTheBuild() {
    val entry = exitFor(ProcessExitReason.ANR).toEntry(origin)

    assertEquals(
      ErrorLogEntry(
        timestampMillis = 1_700_000_000_000L,
        kind = ErrorLogKind.ANR,
        exceptionClass = "ANR",
        message = "pid 3363: shell-induced crash",
        appVersion = "0.9.1",
        deviceModel = "Pixel 9",
        androidVersion = "16",
      ),
      entry,
    )
  }

  @Test
  fun aNativeCrashIsANativeEntry() {
    assertEquals(ErrorLogKind.NATIVE, exitFor(ProcessExitReason.CRASH_NATIVE).toEntry(origin)?.kind)
  }

  @Test
  fun aJavaCrashTheHandlerMayHaveMissedAndEveryKillAreExitEntriesNamedByTheirReason() {
    val killed = listOf(ProcessExitReason.CRASH, ProcessExitReason.LOW_MEMORY, ProcessExitReason.SIGNALED)

    val entries = killed.map { exitFor(it).toEntry(origin) }

    assertEquals(List(3) { ErrorLogKind.EXIT }, entries.map { it?.kind })
    assertEquals(listOf("CRASH", "LOW_MEMORY", "SIGNALED"), entries.map { it?.exceptionClass })
  }

  @Test
  fun aDeathThePersonOrTheAppChoseIsNotAnEntry() {
    assertNull(exitFor(ProcessExitReason.DELIBERATE).toEntry(origin))
  }

  @Test
  fun aDescriptionlessExitStillNamesItsPid() {
    val entry = exitFor(ProcessExitReason.SIGNALED).copy(description = "").toEntry(origin)

    assertEquals("pid 3363", entry?.message)
  }
}
