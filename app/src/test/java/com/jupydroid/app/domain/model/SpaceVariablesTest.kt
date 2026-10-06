package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceVariablesTest {
  @Test
  fun aNameAShellCouldExportIsAccepted() {
    listOf("GREETING", "_private", "a1", "PYTHONPATH").forEach { assertNull(it, variableNameRefusal(it)) }
  }

  /** The refusal says it is derived, so the person learns why rather than that it failed. */
  @Test
  fun aDerivedNameIsRefusedAsDerived() {
    DERIVED_VARIABLE_NAMES.forEach { name ->
      val refusal = variableNameRefusal(name)

      assertTrue("'$name' must be refused as derived, was '$refusal'", refusal?.contains("derived") == true)
      assertTrue("refusal must name '$name', was '$refusal'", refusal?.contains("'$name'") == true)
    }
  }

  @Test
  fun aNameNoShellCouldExportIsRefusedWithTheExpectedShape() {
    listOf("", "1X", "A=B", "WITH SPACE", "DASH-ED").forEach { name ->
      val refusal = variableNameRefusal(name)

      assertTrue("refusal must name '$name', was '$refusal'", refusal?.contains("'$name'") == true)
      assertTrue("refusal must state the shape, was '$refusal'", refusal?.contains(VARIABLE_NAME_SHAPE) == true)
    }
  }

  @Test
  fun theRefusalOfHomeReadsAsOneSentence() {
    assertEquals("'HOME' is derived from the Space's directory and cannot be set", variableNameRefusal("HOME"))
  }

  @Test
  fun aValueMayHoldAnythingButNul() {
    assertNull(variableValueRefusal("GREETING", "hello world = 'quoted' \$HOME\n"))
    assertTrue(variableValueRefusal("GREETING", "a\u0000b")?.contains("'GREETING'") == true)
  }
}
