package com.jupydroid.app.data.crash

import android.app.ApplicationExitInfo
import com.jupydroid.app.domain.model.ProcessExitReason
import org.junit.Assert.assertEquals
import org.junit.Test

/** The platform's reason numbers, read as the app's own words; the constants are inlined, so no device is needed. */
class ProcessExitReasonMappingTest {
  @Test
  fun aCrashAnAnrAndANativeCrashKeepTheirNames() {
    assertEquals(ProcessExitReason.CRASH, processExitReasonOf(ApplicationExitInfo.REASON_CRASH))
    assertEquals(ProcessExitReason.CRASH_NATIVE, processExitReasonOf(ApplicationExitInfo.REASON_CRASH_NATIVE))
    assertEquals(ProcessExitReason.ANR, processExitReasonOf(ApplicationExitInfo.REASON_ANR))
  }

  @Test
  fun everyKillTheAppDidNotChooseKeepsItsName() {
    val expected = mapOf(
      ApplicationExitInfo.REASON_LOW_MEMORY to ProcessExitReason.LOW_MEMORY,
      ApplicationExitInfo.REASON_SIGNALED to ProcessExitReason.SIGNALED,
      ApplicationExitInfo.REASON_INITIALIZATION_FAILURE to ProcessExitReason.INITIALIZATION_FAILURE,
      ApplicationExitInfo.REASON_EXCESSIVE_RESOURCE_USAGE to ProcessExitReason.EXCESSIVE_RESOURCE_USAGE,
      ApplicationExitInfo.REASON_DEPENDENCY_DIED to ProcessExitReason.DEPENDENCY_DIED,
    )

    assertEquals(expected, expected.keys.associateWith(::processExitReasonOf))
  }

  @Test
  fun aDeathSomeoneChoseIsDeliberate() {
    val chosen = listOf(
      ApplicationExitInfo.REASON_EXIT_SELF,
      ApplicationExitInfo.REASON_USER_REQUESTED,
      ApplicationExitInfo.REASON_USER_STOPPED,
      ApplicationExitInfo.REASON_PERMISSION_CHANGE,
    )

    assertEquals(List(chosen.size) { ProcessExitReason.DELIBERATE }, chosen.map(::processExitReasonOf))
  }

  @Test
  fun aReasonNoBuildNamedIsUnknownRatherThanDropped() {
    assertEquals(ProcessExitReason.UNKNOWN, processExitReasonOf(ApplicationExitInfo.REASON_OTHER))
    assertEquals(ProcessExitReason.UNKNOWN, processExitReasonOf(9_999))
  }
}
