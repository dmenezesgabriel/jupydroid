package com.jupydroid.app.data.space

import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress
import java.net.ServerSocket

class LoopbackPortTest {
  /** The port is handed to a server that binds it after us, so it must be free again on return. */
  @Test
  fun thePortIsFreeOnLoopbackOnceReturned() {
    val port = freeLoopbackPort()

    ServerSocket(port, 0, InetAddress.getByName("127.0.0.1")).use { assertTrue(it.localPort == port) }
  }

  @Test
  fun thePortIsAnEphemeralOneTheOsPicked() {
    val port = freeLoopbackPort()

    assertTrue("expected a non-privileged port, got $port", port in 1024..65535)
  }
}
