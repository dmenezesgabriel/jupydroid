package com.jupydroid.app.domain.model

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The fold on ART, where a cost hid that the JVM test cannot see: Android's `Matcher` copies its
 * whole input into ICU each time one is made, and Kotlin's `Regex.findAll` makes one per match, so
 * tokenizing a chunk with a regex cost tokens x chunk length - quadratic in one message on a device
 * and linear on a desktop (StreamFoldTest.aRunawayPrintFoldsInLinearTime passed throughout).
 */
@RunWith(AndroidJUnit4::class)
class StreamFoldOnArtTest {
  /** Bounded in this thread's CPU time, as StreamFoldTest's is: a wall clock measures the host. */
  @Test
  fun oneMessageOfManyLinesFoldsInLinearTime() {
    val chunk = (0 until LINES_IN_ONE_MESSAGE).joinToString("") { "$it\n" }

    val before = SystemClock.currentThreadTimeMillis()
    val folded = emptyList<CellOutput>().withOutputFolded(CellOutput.Stream(StreamName.STDOUT, chunk))
    val spentMs = SystemClock.currentThreadTimeMillis() - before

    assertEquals(chunk, (folded.single() as CellOutput.Stream).text)
    assertTrue(
      "folding took $spentMs ms of CPU; expected under $ONE_BIG_CHUNK_BOUND_MS",
      spentMs < ONE_BIG_CHUNK_BOUND_MS,
    )
  }

  private companion object {
    /** Enough lines that a copy of the chunk per token cannot finish inside the bound. */
    const val LINES_IN_ONE_MESSAGE = 50_000
    const val ONE_BIG_CHUNK_BOUND_MS = 5_000L
  }
}
