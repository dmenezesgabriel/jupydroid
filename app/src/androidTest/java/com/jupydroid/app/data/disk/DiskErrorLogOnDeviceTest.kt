package com.jupydroid.app.data.disk

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.model.ErrorLogKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The log written into the app-specific external directory, on the platform's own regex and file
 * system - neither of which a JVM test has. ART's regex is ICU, so a pattern the JVM tests accept can still
 * fail here.
 *
 * It leaves its file under `error-log-test/` on purpose: `scripts/pull-app-logs.sh` and `adb pull` read
 * that directory from the host without `run-as`, and the file is what shows they can.
 */
@RunWith(AndroidJUnit4::class)
class DiskErrorLogOnDeviceTest {
  private val token = "0123456789abcdef0123456789abcdef"

  @Test
  fun anEntryLandsInTheExternalFilesDirectoryWithoutItsToken() {
    val external = ApplicationProvider.getApplicationContext<Context>().getExternalFilesDir(null)
    val directory = File(external, "error-log-test").also { it.deleteRecursively() }
    val entry = ErrorLogEntry(
      timestampMillis = 1_700_000_000_000L,
      kind = ErrorLogKind.CRASH,
      message = "Authorization: Bearer $token",
      stack = "GET /api/kernels?token=$token",
    )

    runBlocking { DiskErrorLog(directory).append(entry) }

    val written = File(directory, "errors-2023-11-14.jsonl")
    assertTrue("the day's file exists in $directory", written.isFile)
    assertFalse("the token reached ${written.path}", written.readText().contains(token))
  }
}
