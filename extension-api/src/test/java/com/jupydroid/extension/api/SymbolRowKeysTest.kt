package com.jupydroid.extension.api

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The set itself is the item's verbatim contract, so the test reads it back whole rather than
 * sampling it - a row missing most of the characters would still pass a test that checked a few.
 */
class SymbolRowKeysTest {
  @Test
  fun theRowsCarryEveryCharacterDevelopmentTypes() {
    assertEquals(
      listOf(
        ":", "(", ")", "[", "]", "{", "}", "'", "\"", "=", "#", "_",
        "`", "/", "\\", "%", "+", "-", "*", "<", ">", ";", "&", "|", "~", "^", "@", "!", "?", ",", ".",
      ),
      SymbolRowKeys,
    )
  }
}
