package com.jupydroid.app.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteSizeTextTest {
  @Test
  fun underAKilobyteIsWholeBytes() {
    assertEquals("0 B", byteSizeText(0))
    assertEquals("1023 B", byteSizeText(1023))
  }

  @Test
  fun aKilobyteIsFromTwoToThe10thWithOneDecimal() {
    assertEquals("1.0 KB", byteSizeText(1024))
    assertEquals("2.9 KB", byteSizeText(2_970))
  }

  @Test
  fun aMegabyteIsFromTwoToThe20th() {
    assertEquals("1.0 MB", byteSizeText(1_048_576))
  }
}
