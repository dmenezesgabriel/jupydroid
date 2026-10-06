package com.jupydroid.app.ui.spaces

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpaceVariableRowTest {
  @Test
  fun storedVariablesBecomeRowsInTheirOrderAndBackAgain() {
    val variables = linkedMapOf("ZETA" to "z", "GREETING" to "hello")

    val rows = variableRowsOf(variables)

    assertEquals(listOf(SpaceVariableRow("ZETA", "z"), SpaceVariableRow("GREETING", "hello")), rows)
    assertEquals(variables.toList(), rows.toVariables().toList())
  }

  @Test
  fun aDerivedNameIsRefusedByTheDomainsOwnWords() {
    val refusals = listOf(SpaceVariableRow("GREETING", "hello"), SpaceVariableRow("HOME", "/tmp")).refusals()

    assertNull(refusals[0])
    assertEquals("'HOME' is derived from the Space's directory and cannot be set", refusals[1])
  }

  /** A map cannot hold a name twice, so the second row would silently overwrite the first. */
  @Test
  fun aNameGivenTwiceIsRefusedOnTheSecondRowOnly() {
    val refusals = listOf(SpaceVariableRow("A", "1"), SpaceVariableRow("A", "2")).refusals()

    assertNull(refusals[0])
    assertTrue(refusals[1].orEmpty(), refusals[1]?.contains("'A'") == true)
  }

  @Test
  fun aValueNoProcessCanCarryIsRefused() {
    val refusals = listOf(SpaceVariableRow("A", "a\u0000b")).refusals()

    assertTrue(refusals[0].orEmpty(), refusals[0]?.contains("NUL") == true)
  }
}
