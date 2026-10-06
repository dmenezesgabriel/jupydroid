package com.jupydroid.app.ui.spaces

import com.jupydroid.app.domain.model.MAX_THREAD_CAP
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the Settings dialog's thread-cap field reads as: a cap, none, or a value Save is held for. */
class ThreadCapTextTest {
  @Test
  fun aNumberInTheRangeIsThatCap() {
    assertEquals(1, threadCapOf("1").getOrThrow())
    assertEquals(MAX_THREAD_CAP, threadCapOf(" $MAX_THREAD_CAP ").getOrThrow())
  }

  /** Blank is no cap at all - the libraries' own default - not a refusal. */
  @Test
  fun blankIsNoCap() {
    assertEquals(null, threadCapOf("").getOrThrow())
    assertEquals(null, threadCapOf("  ").getOrThrow())
  }

  @Test
  fun anythingElseIsRefusedNamingWhatWasTyped() {
    listOf("0", "${MAX_THREAD_CAP + 1}", "-1", "two").forEach { text ->
      val refusal = threadCapOf(text).exceptionOrNull()

      assertTrue(
        "'$text' must be refused naming it, was '${refusal?.message}'",
        refusal?.message?.contains("'$text'") == true,
      )
    }
  }
}
