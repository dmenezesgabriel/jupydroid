package com.jupydroid.app.data.crash

import android.app.ActivityManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.ProcessExit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The platform's own exit reasons, which a JVM test cannot ask: `getHistoricalProcessExitReasons` is answered
 * by `ActivityManager` on a device. What it holds depends on the device's history, so these assert the contract
 * (order, and "reported once") and not any particular death.
 */
@RunWith(AndroidJUnit4::class)
class ActivityManagerProcessExitsOnDeviceTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val markFile = File(context.cacheDir, "exit-mark-test").also { it.delete() }

  private fun exitsReadingTheMark() =
    ActivityManagerProcessExits(context.getSystemService(ActivityManager::class.java), markFile)

  @Test
  fun deathsComeOldestFirst() = runBlocking {
    val exits = exitsReadingTheMark().unreported()

    assertEquals(exits.sortedBy(ProcessExit::timestampMillis), exits)
  }

  @Test
  fun whatWasMarkedReportedStaysReportedForAnotherReaderOfTheSameMark() = runBlocking {
    val first = exitsReadingTheMark()
    first.markReported(first.unreported())

    assertEquals(emptyList<ProcessExit>(), exitsReadingTheMark().unreported())
  }
}
