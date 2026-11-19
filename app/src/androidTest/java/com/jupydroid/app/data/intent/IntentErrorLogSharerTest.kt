package com.jupydroid.app.data.intent

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * What is handed to the chooser is a `content://` URI the provider must serve: an authority that does not match
 * the manifest, or a `<cache-path>` that does not cover the bundle, fails only when a person taps "Share log",
 * and nothing a JVM test can reach says it would. `openInputStream` is what says the provider serves it.
 */
@RunWith(AndroidJUnit4::class)
class IntentErrorLogSharerTest {
  private val context = ApplicationProvider.getApplicationContext<Context>()
  private val logDirectory = File(context.cacheDir, "error-log-sharer-test").also { it.deleteRecursively() }
  private val sharer = IntentErrorLogSharer(context, logDirectory)

  @After
  fun removeWhatWasWritten() {
    logDirectory.deleteRecursively()
  }

  private fun dayFile(name: String, text: String) {
    logDirectory.mkdirs()
    File(logDirectory, name).writeText(text)
  }

  private fun servedBundle(): String =
    context.contentResolver.openInputStream(sharer.bundleUri())!!.use { it.readBytes().decodeToString() }

  @Test
  fun servesEveryDaysEntriesOldestDayFirst() {
    dayFile("errors-2023-11-14.jsonl", "{\"n\":3}\n{\"n\":4}\n")
    dayFile("errors-2023-11-13.jsonl", "{\"n\":1}\n{\"n\":2}\n")

    assertEquals("{\"n\":1}\n{\"n\":2}\n{\"n\":3}\n{\"n\":4}\n", servedBundle())
  }

  /** A crash can tear a day's last line; it must stay its own line instead of eating the next day's first. */
  @Test
  fun aTornLastLineDoesNotJoinTheNextDaysFirst() {
    dayFile("errors-2023-11-13.jsonl", "{\"n\":1}\n{\"n\":")
    dayFile("errors-2023-11-14.jsonl", "{\"n\":3}\n")

    assertEquals("{\"n\":1}\n{\"n\":\n{\"n\":3}\n", servedBundle())
  }

  @Test
  fun leavesOutWhatIsNotADaysFile() {
    dayFile("errors-2023-11-13.jsonl", "{\"n\":1}\n")
    dayFile("notes.txt", "not the log's\n")

    assertEquals("{\"n\":1}\n", servedBundle())
  }

  @Test
  fun aSecondShareReplacesTheBundleInsteadOfAppendingToIt() {
    dayFile("errors-2023-11-13.jsonl", "{\"n\":1}\n")
    sharer.bundleUri()
    File(logDirectory, "errors-2023-11-13.jsonl").delete()
    dayFile("errors-2023-11-14.jsonl", "{\"n\":2}\n")

    assertEquals("{\"n\":2}\n", servedBundle())
  }

  @Test
  fun usesTheAuthorityTheManifestDeclares() {
    dayFile("errors-2023-11-13.jsonl", "{\"n\":1}\n")

    assertEquals("${context.packageName}.exports", sharer.bundleUri().authority)
  }
}
