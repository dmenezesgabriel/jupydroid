package com.jupydroid.app.domain.model

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The names this process believes alive - the signal `IdleServerStopper` needs so a live shell
 * is not indistinguishable from an idle app. See `ActiveTerminals`'s own doc for why the state
 * is a set of names and not an integer.
 */
class ActiveTerminalsTest {
  @Test
  fun nothingIsAliveToBeginWith() = runTest {
    assertEquals(0, ActiveTerminals().open.first())
  }

  @Test
  fun mintedCountsTheName() = runTest {
    val terminals = ActiveTerminals()

    terminals.minted("1")

    assertEquals(1, terminals.open.first())
  }

  /** A name the server re-mints in a later generation is still just one live name. */
  @Test
  fun aReusedNameCountsOnce() = runTest {
    val terminals = ActiveTerminals()

    terminals.minted("1")
    terminals.minted("1")

    assertEquals(1, terminals.open.first())
  }

  @Test
  fun endedForgetsTheName() = runTest {
    val terminals = ActiveTerminals()
    terminals.minted("1")

    terminals.ended("1")

    assertEquals(0, terminals.open.first())
  }

  /** The failure mode that matters: an end nobody minted must not read as "terminals are open". */
  @Test
  fun endingANameNeverMintedIsANoOp() = runTest {
    val terminals = ActiveTerminals()

    terminals.ended("1")

    assertEquals(0, terminals.open.first())
  }

  /** The `Ended` a `close` causes reaches `reconciled` after `ended` - one decrement, not two. */
  @Test
  fun anEndWitnessedTwiceStillCountsOnce() = runTest {
    val terminals = ActiveTerminals()
    terminals.minted("1")

    terminals.ended("1")
    terminals.reconciled(emptyList())

    assertEquals(0, terminals.open.first())
  }

  @Test
  fun reconciledTakesTheServersWholeAnswer() = runTest {
    val terminals = ActiveTerminals()
    terminals.minted("1")
    terminals.minted("2")

    terminals.reconciled(listOf("2"))

    assertEquals(1, terminals.open.first())
  }

  @Test
  fun terminalsCountIndependentlyOfEachOther() = runTest {
    val terminals = ActiveTerminals()

    terminals.minted("1")
    terminals.minted("2")
    assertEquals(2, terminals.open.first())

    terminals.ended("1")
    assertEquals("one of the two is still open", 1, terminals.open.first())

    terminals.ended("2")
    assertEquals(0, terminals.open.first())
  }
}
