package com.jupydroid.app.domain.model

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ErrorLogCapsTest {
  @Test
  fun aSizeCapThatIsNotPositiveIsRefusedNamingTheValue() {
    val refused = assertThrows(IllegalArgumentException::class.java) { ErrorLogCaps(maxBytes = 0, maxAgeDays = 7) }

    assertTrue(refused.message.orEmpty(), refused.message.orEmpty().contains("maxBytes=0"))
  }

  @Test
  fun anAgeCapThatIsNotPositiveIsRefusedNamingTheValue() {
    val refused = assertThrows(IllegalArgumentException::class.java) { ErrorLogCaps(maxBytes = 1, maxAgeDays = -3) }

    assertTrue(refused.message.orEmpty(), refused.message.orEmpty().contains("maxAgeDays=-3"))
  }
}
