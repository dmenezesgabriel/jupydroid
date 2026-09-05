package com.jupydroid.app.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * How many terminals the app has opened and not yet closed - the signal `IdleServerStopper` needs
 * so a live shell is not indistinguishable from an idle app. See `ActiveTerminals`'s own doc for
 * why this is a standalone counter and not state kept inside a gateway.
 */
class ActiveTerminalsTest {
  @Test
  fun nothingIsOpenToBeginWith() {
    assertEquals(0, ActiveTerminals().open.value)
  }

  @Test
  fun openedIncrementsTheCount() {
    val terminals = ActiveTerminals()

    terminals.opened()

    assertEquals(1, terminals.open.value)
  }

  @Test
  fun closedDecrementsTheCount() {
    val terminals = ActiveTerminals()
    terminals.opened()

    terminals.closed()

    assertEquals(0, terminals.open.value)
  }

  /** The failure mode that matters: a stray extra close must not read as "terminals are open". */
  @Test
  fun closedNeverGoesBelowZero() {
    val terminals = ActiveTerminals()

    terminals.closed()

    assertEquals(0, terminals.open.value)
  }

  @Test
  fun terminalsCountIndependentlyOfEachOther() {
    val terminals = ActiveTerminals()

    terminals.opened()
    terminals.opened()
    assertEquals(2, terminals.open.value)

    terminals.closed()
    assertEquals("one of the two is still open", 1, terminals.open.value)

    terminals.closed()
    assertEquals(0, terminals.open.value)
  }
}
