package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGatewayContract
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What only this implementation can be asked, beside [com.jupydroid.app.domain.port.TerminalGatewayContract].
 *
 * Three of these are about what the fake **records** rather than what it does. That is the shape
 * every fake here has - `FakeKernelGateway.shutDownSessions` exists for the same reason - because a
 * port whose arguments vanish cannot tell a screen that passed them from one that did not, and the
 * terminal screen has two it must get right: the directory a shell starts in and the size it
 * believes its window is.
 */
class FakeTerminalGatewayTest {
  private val gateway = FakeTerminalGateway()

  /** The server counts from `"1"` (`terminado/management.py:394-399`); so does this. */
  @Test
  fun open_namesTerminalsTheWayTheServerDoes() = runTest {
    assertEquals(TerminalSession("1"), gateway.open())
    assertEquals(TerminalSession("2"), gateway.open())
  }

  @Test
  fun open_recordsTheDirectoryItWasAskedFor() = runTest {
    gateway.open(cwd = "reports")
    gateway.open(cwd = null)

    assertEquals(listOf("reports", null), gateway.openedIn)
  }

  /** Attached, because a resize is a write - see [TerminalGatewayContract.resize_reachesAnAttachedTerminal]. */
  @Test
  fun resize_recordsRowsThenColumns() = runTest {
    val session = gateway.open()

    coroutineScope {
      val attached = launch { gateway.attach(session).collect { } }
      gateway.resize(session, rows = 24, cols = 80)
      attached.cancel()
    }

    assertEquals(24 to 80, gateway.sizeOf(session))
  }

  /**
   * Closing ends an attached stream, and the reason says which terminal went.
   *
   * `TermSocket.on_pty_died` sends `["disconnect", 1]` and closes; a caller drawing a screen needs
   * that to arrive rather than the flow simply stopping, which is indistinguishable from the app
   * having cancelled it.
   */
  @Test
  fun close_endsAnAttachedStreamWithAReason() = runTest {
    val session = gateway.open()

    val events = coroutineScope {
      val collected = async { gateway.attach(session).toList() }
      // `send` suspends until an attach has subscribed, so this is the signal rather than a delay.
      gateway.send(session, "")
      gateway.close(session)
      collected.await()
    }

    assertEquals(TerminalEvent.Ready, events.first())
    val ended = requireNotNull(events.filterIsInstance<TerminalEvent.Ended>().singleOrNull()) {
      "expected the stream to end exactly once, got $events"
    }
    assertTrue("the reason must name the terminal, got $ended", session.name in ended.reason)
  }

  /** The message carries the name asked for and what was open, which is what makes a typo findable. */
  @Test
  fun send_toATerminalThatWasNeverOpenedSaysWhichAndWhatWasOpen() = runTest {
    gateway.open()

    val failure = runCatching { gateway.send(TerminalSession("nope"), "ls\n") }.exceptionOrNull()

    assertTrue("expected the bad name in '${failure?.message}'", failure?.message?.contains("nope") == true)
    assertTrue("expected the open terminals in '${failure?.message}'", failure?.message?.contains("[1]") == true)
  }
}
