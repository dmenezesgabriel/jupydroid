package com.jupydroid.app.di

import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.domain.model.BASE_SPACE
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class EnteredSpacePortsTest {
  /**
   * The default gateway and sessions share one registry, so the Running panel of a device with no
   * Space lists what that Space's notebooks opened.
   */
  @Test
  fun theDefaultFakesListTheSessionsTheirOwnGatewayOpened() = runTest {
    val ports = EnteredSpacePorts(FakeNotebookRepository())

    val session = ports.kernelGateway.connect(BASE_SPACE, "Analysis.ipynb")

    assertEquals(listOf(session.serverSessionId), ports.kernelSessions.running().map { it.serverSessionId })
  }
}
