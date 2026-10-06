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
  /**
   * Every Space's server mints `"1"` first, so two Spaces with one shell each hold the same name
   * twice - and both keep their servers alive.
   */
  @Test
  fun oneNameInTwoSpacesCountsTwice() = runTest {
    val terminals = ActiveTerminals()

    terminals.minted(BASE, "1")
    terminals.minted(THESIS, "1")

    assertEquals(2, terminals.open.first())
  }

  /**
   * One server's list is the whole truth about that server only: Thesis's answer must not erase
   * base's shell, or the idle stopper would stop base under it.
   */
  @Test
  fun reconcilingOneSpaceKeepsAnothersTerminals() = runTest {
    val terminals = ActiveTerminals()
    terminals.minted(BASE, "1")
    terminals.minted(THESIS, "1")

    terminals.reconciled(THESIS, emptyList())

    assertEquals(1, terminals.open.first())
  }

  @Test
  fun nothingIsAliveToBeginWith() = runTest {
    assertEquals(0, ActiveTerminals().open.first())
  }

  @Test
  fun mintedCountsTheName() = runTest {
    val terminals = ActiveTerminals()

    terminals.minted(BASE, "1")

    assertEquals(1, terminals.open.first())
  }

  /** A name the server re-mints in a later generation is still just one live name. */
  @Test
  fun aReusedNameCountsOnce() = runTest {
    val terminals = ActiveTerminals()

    terminals.minted(BASE, "1")
    terminals.minted(BASE, "1")

    assertEquals(1, terminals.open.first())
  }

  @Test
  fun endedForgetsTheName() = runTest {
    val terminals = ActiveTerminals()
    terminals.minted(BASE, "1")

    terminals.ended(BASE, "1")

    assertEquals(0, terminals.open.first())
  }

  /** The failure mode that matters: an end nobody minted must not read as "terminals are open". */
  @Test
  fun endingANameNeverMintedIsANoOp() = runTest {
    val terminals = ActiveTerminals()

    terminals.ended(BASE, "1")

    assertEquals(0, terminals.open.first())
  }

  /** The `Ended` a `close` causes reaches `reconciled` after `ended` - one decrement, not two. */
  @Test
  fun anEndWitnessedTwiceStillCountsOnce() = runTest {
    val terminals = ActiveTerminals()
    terminals.minted(BASE, "1")

    terminals.ended(BASE, "1")
    terminals.reconciled(BASE, emptyList())

    assertEquals(0, terminals.open.first())
  }

  @Test
  fun reconciledTakesTheServersWholeAnswer() = runTest {
    val terminals = ActiveTerminals()
    terminals.minted(BASE, "1")
    terminals.minted(BASE, "2")

    terminals.reconciled(BASE, listOf("2"))

    assertEquals(1, terminals.open.first())
  }

  @Test
  fun terminalsCountIndependentlyOfEachOther() = runTest {
    val terminals = ActiveTerminals()

    terminals.minted(BASE, "1")
    terminals.minted(BASE, "2")
    assertEquals(2, terminals.open.first())

    terminals.ended(BASE, "1")
    assertEquals("one of the two is still open", 1, terminals.open.first())

    terminals.ended(BASE, "2")
    assertEquals(0, terminals.open.first())
  }

  private companion object {
    const val BASE = "base"
    const val THESIS = "space-1"
  }
}
