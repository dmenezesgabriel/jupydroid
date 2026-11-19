package com.jupydroid.app.data.disk

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.ErrorLogCaps
import com.jupydroid.app.domain.model.ErrorLogEntry
import com.jupydroid.app.domain.model.ErrorLogKind
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
  private fun directoryNamed(name: String): File {
    val external = ApplicationProvider.getApplicationContext<Context>().getExternalFilesDir(null)
    return File(external, name).also { it.deleteRecursively() }
  }

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

    runBlocking { DiskErrorLog(directory, clock = { entry.timestampMillis }).append(entry) }

    val written = File(directory, "errors-2023-11-14.jsonl")
    assertTrue("the day's file exists in $directory", written.isFile)
    assertFalse("the token reached ${written.path}", written.readText().contains(token))
  }

  @Test
  fun aDayPastTheAgeCapIsDeletedFromTheExternalFilesDirectory() {
    val directory = directoryNamed("error-log-prune-test").also { it.mkdirs() }
    val old = File(directory, "errors-2023-11-01.jsonl").also { it.writeText("{}\n") }
    val kept = File(directory, "errors-2023-11-14.jsonl").also { it.writeText("{}\n") }
    val log = DiskErrorLog(directory, ErrorLogCaps(maxBytes = 1_048_576, maxAgeDays = 7), { 1_700_000_000_000L })

    runBlocking { log.prune() }

    assertFalse("${old.path} is past the age cap", old.exists())
    assertTrue("${kept.path} is inside it", kept.exists())
  }

  /**
   * What an entry costs on disk, which the caps are set from: a thrown exception's real stack, and the same
   * thrown from a call 150 frames deep - the order of a Compose crash. Left in `error-log-size-test/` for
   * `adb shell ls -l`, and logged under the tag `ErrorLogSize`.
   */
  @Test
  fun anEntryWithARealStackHasAMeasuredSize() {
    val directory = directoryNamed("error-log-size-test")
    val shallow = stackOf(thrownAtDepth(0))
    val deep = stackOf(thrownAtDepth(150))

    // Real timestamps, one a day apart: a day past the age cap would be pruned the moment it is written.
    val now = System.currentTimeMillis()
    runBlocking { DiskErrorLog(directory).append(crash(shallow).copy(timestampMillis = now)) }
    runBlocking { DiskErrorLog(directory).append(crash(deep).copy(timestampMillis = now - 24 * 60 * 60 * 1000)) }

    directory.listFiles().orEmpty().sortedBy { it.name }.forEach {
      Log.i("ErrorLogSize", "${it.name} ${it.length()} bytes")
    }
    assertEquals(2, directory.listFiles().orEmpty().count { it.length() > 0 })
  }

  private fun crash(stack: String) = ErrorLogEntry(
    timestampMillis = 0, kind = ErrorLogKind.CRASH, exceptionClass = "java.lang.IllegalStateException",
    message = "boom", stack = stack, thread = "main", appVersion = "1.0", deviceModel = "sdk_gphone64_x86_64",
    androidVersion = "16",
  )

  private fun stackOf(failure: Throwable): String = Log.getStackTraceString(failure)

  private fun thrownAtDepth(depth: Int): Throwable =
    if (depth > 0) thrownAtDepth(depth - 1) else IllegalStateException("boom")
}
