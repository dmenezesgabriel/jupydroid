package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.domain.model.ActiveTerminals
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
    val gateway = ActivityTrackedTerminalGateway(FakeTerminalGateway(), activeTerminals)

    gateway.open()

    assertEquals(1, activeTerminals.open.value)
  }

  @Test
  fun closeDecrementsTheCount() = runTest {
    val activeTerminals = ActiveTerminals()
    val gateway = ActivityTrackedTerminalGateway(FakeTerminalGateway(), activeTerminals)
    val session = gateway.open()

    gateway.close(session)

    assertEquals(0, activeTerminals.open.value)
  }

  @Test
  fun openStillForwardsToTheWrappedGateway() = runTest {
    val fake = FakeTerminalGateway()
    val gateway = ActivityTrackedTerminalGateway(fake, ActiveTerminals())

    gateway.open(cwd = "notebooks")

    assertEquals(listOf("notebooks"), fake.openedIn)
  }

  @Test
  fun runningStillForwardsToTheWrappedGateway() = runTest {
    val fake = FakeTerminalGateway()
    val gateway = ActivityTrackedTerminalGateway(fake, ActiveTerminals())
    val session = gateway.open()

    assertEquals(listOf(session), gateway.running())
  }
}
