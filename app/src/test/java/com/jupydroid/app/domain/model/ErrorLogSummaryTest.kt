package com.jupydroid.app.domain.model

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorLogSummaryTest {
  @Test
  fun aNegativeEntryCountIsRefusedNamingTheValue() {
    val refused = assertThrows(IllegalArgumentException::class.java) { ErrorLogSummary(entryCount = -1, sizeBytes = 0) }

    assertTrue(refused.message.orEmpty(), refused.message.orEmpty().contains("entryCount=-1"))
  }

  @Test
  fun aNegativeSizeIsRefusedNamingTheValue() {
    val refused = assertThrows(IllegalArgumentException::class.java) { ErrorLogSummary(entryCount = 0, sizeBytes = -5) }

    assertTrue(refused.message.orEmpty(), refused.message.orEmpty().contains("sizeBytes=-5"))
  }
}
