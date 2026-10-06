package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.domain.model.ActiveTerminals
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The seam that makes a terminal's existence visible to `IdleServerStopper` without touching
 * `TerminalGateway` or any of its three implementations - see `ActiveTerminals`'s own doc for why
 * the counting cannot live inside the gateway itself.
 */
class ActivityTrackedTerminalGatewayTest {
  @Test
  fun openIncrementsTheCount() = runTest {
    val activeTerminals = ActiveTerminals()
    val gateway = ActivityTrackedTerminalGateway(FakeTerminalGateway(), activeTerminals, spaceId = "base")

    gateway.open()

    assertEquals(1, activeTerminals.open.first())
  }

  @Test
  fun closeDecrementsTheCount() = runTest {
    val activeTerminals = ActiveTerminals()
    val gateway = ActivityTrackedTerminalGateway(FakeTerminalGateway(), activeTerminals, spaceId = "base")
    val session = gateway.open()

    gateway.close(session)

    assertEquals(0, activeTerminals.open.first())
  }

  @Test
  fun openStillForwardsToTheWrappedGateway() = runTest {
    val fake = FakeTerminalGateway()
    val gateway = ActivityTrackedTerminalGateway(fake, ActiveTerminals(), spaceId = "base")

    gateway.open(cwd = "notebooks")

    assertEquals(listOf("notebooks"), fake.openedIn)
  }

  @Test
  fun runningStillForwardsToTheWrappedGateway() = runTest {
    val fake = FakeTerminalGateway()
    val gateway = ActivityTrackedTerminalGateway(fake, ActiveTerminals(), spaceId = "base")
    val session = gateway.open()

    assertEquals(listOf(session), gateway.running())
  }

  /**
   * A shell that exits is an `Ended` on the attach, not a `close` - the count has to follow
   * the server's own answer anyway, which is what `terminado`'s `on_eof` does to
   * `current_activity()`. `fake.close` is called directly on purpose: through the wrapper it
   * would already decrement and the `Ended`-driven reconcile would go unmeasured.
   */
  @Test
  fun anEndedAttachReconcilesAwayTheDeadShell() = runTest {
    val fake = FakeTerminalGateway()
    val activeTerminals = ActiveTerminals()
    val gateway = ActivityTrackedTerminalGateway(fake, activeTerminals, spaceId = "base")
    val session = gateway.open()

    val collected = async { gateway.attach(session).toList() }
    // `send` suspends until the attach has subscribed - the signal, not a delay.
    gateway.send(session, "")
    fake.close(session)
    collected.await()

    assertEquals(0, activeTerminals.open.first())
  }

  /**
   * The half that keeps reconcile honest: a clean socket close leaves the pty on the server
   * (`TermSocket.on_close` drops the client only), so an `Ended` alone must not decrement -
   * `running()` still lists the shell and the hold stays.
   */
  @Test
  fun aSocketEndOnALiveShellKeepsItsHold() = runTest {
    val fake = FakeTerminalGateway()
    val activeTerminals = ActiveTerminals()
    val gateway = ActivityTrackedTerminalGateway(fake, activeTerminals, spaceId = "base")
    val session = gateway.open()

    val collected = async { gateway.attach(session).toList() }
    gateway.send(session, "")
    fake.detach(session)
    collected.await()

    assertEquals(1, activeTerminals.open.first())
  }

  /** A death nobody was attached to see surfaces only when the server is asked again. */
  @Test
  fun aShellEndedElsewhereDropsOnTheNextList() = runTest {
    val fake = FakeTerminalGateway()
    val activeTerminals = ActiveTerminals()
    val gateway = ActivityTrackedTerminalGateway(fake, activeTerminals, spaceId = "base")
    gateway.open()

    fake.close(TerminalSession("1"))
    gateway.running()

    assertEquals(0, activeTerminals.open.first())
  }

  /** The `Ended` a `close` causes lands on the same reconcile - one decrement, not two. */
  @Test
  fun anEndedObservedAfterCloseStillCountsOnce() = runTest {
    val fake = FakeTerminalGateway()
    val activeTerminals = ActiveTerminals()
    val gateway = ActivityTrackedTerminalGateway(fake, activeTerminals, spaceId = "base")
    val session = gateway.open()

    val collected = async { gateway.attach(session).toList() }
    gateway.send(session, "")
    gateway.close(session)
    collected.await()

    assertEquals(0, activeTerminals.open.first())
  }

  /**
   * Two Spaces' gateways report into the app's one counter, and each server names its first shell
   * `"1"`: listing Thesis's terminals must leave base's shell counted.
   */
  @Test
  fun listingOneSpacesTerminalsKeepsAnotherSpacesShellCounted() = runTest {
    val activeTerminals = ActiveTerminals()
    val base = ActivityTrackedTerminalGateway(FakeTerminalGateway(), activeTerminals, spaceId = "base")
    val thesis = ActivityTrackedTerminalGateway(FakeTerminalGateway(), activeTerminals, spaceId = "space-1")
    base.open()

    thesis.running()

    assertEquals(1, activeTerminals.open.first())
  }
}
