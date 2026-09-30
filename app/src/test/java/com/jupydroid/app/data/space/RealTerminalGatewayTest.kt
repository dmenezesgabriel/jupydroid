package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What only the real gateway can be asked, beside the contract it shares with the fake.
 *
 * All of it is about the socket: which one is opened, when it may be written to, and what happens
 * when it ends. The lifecycle half is [OkHttpTerminalLifecycleTest]'s.
 */
class RealTerminalGatewayTest {
  private val lifecycle = FakeTerminalLifecycle()

  @Test
  fun open_passesTheDirectoryStraightThroughToTheServer() = runTest {
    gateway(ScriptedTerminalSocketOpener()).open(cwd = "reports")

    assertEquals(listOf("reports"), lifecycle.createdIn)
  }

  @Test
  fun attach_opensTheSocketForTheTerminalItWasGiven() = runTest {
    val opener = ScriptedTerminalSocketOpener()
    val gateway = gateway(opener)
    val session = gateway.open()

    gateway.attach(session).first()

    assertEquals(session.name, opener.openedName)
  }

  /**
   * **Nothing is opened until somebody collects**, which is what keeps a shell's first prompt.
   *
   * `TermSocket.open` writes `setup` and then drains `read_buffer` immediately. A socket opened
   * eagerly - at `attach()` rather than at collection - would receive both into a flow with no
   * subscriber and no replay, and the prompt would be gone before the screen ever drew.
   */
  @Test
  fun attach_opensNothingUntilTheFlowIsCollected() = runTest {
    val opener = ScriptedTerminalSocketOpener()
    val gateway = gateway(opener)

    gateway.attach(gateway.open())

    assertEquals(0, opener.openCount)
    assertNull(opener.openedName)
  }

  @Test
  fun attach_closesTheSocketWhenTheCollectionStops() = runTest {
    val opener = ScriptedTerminalSocketOpener()
    val gateway = gateway(opener)
    val session = gateway.open()

    gateway.attach(session).first()

    assertTrue("the socket outlived its collection", opener.closed)
  }

  /**
   * **A write waits for `setup`, not for the handshake.**
   *
   * The two are a round trip apart: OkHttp reports `Opened` when the upgrade completes, and
   * `TermSocket.open` runs afterwards - it is what appends this client to `terminal.clients`. A
   * frame written in between is accepted by the socket and dropped by the server with nothing
   * raised at either end, which is the silent failure this ordering exists to prevent
   * (`default.ts:301-307` is the reference refusing to call itself connected until `setup`).
   */
  @Test
  fun send_waitsForSetupRatherThanForTheHandshake() = runTest {
    val opener = ScriptedTerminalSocketOpener(setUpImmediately = false)
    val gateway = gateway(opener)
    val session = gateway.open()

    coroutineScope {
      val attached = launch { gateway.attach(session).collect { } }
      val typed = launch { gateway.send(session, "ls\n") }
      yield()
      assertEquals("nothing may be written before setup, sent: ${opener.sent}", emptyList<String>(), opener.sent)

      opener.finishSetup()
      typed.join()
      assertEquals(listOf("""["stdin","ls\n"]"""), opener.sent)
      attached.cancel()
    }
  }

  /** `on_pty_died` sends this and then closes; the shell is gone and the stream must say so. */
  @Test
  fun attach_endsWhenTheShellExits() = runTest {
    val opener = ScriptedTerminalSocketOpener()
    val gateway = gateway(opener)
    val session = gateway.open()

    val events = coroutineScope {
      val collected = async { gateway.attach(session).toList() }
      yield()
      opener.push(TerminalFrames.DISCONNECT)
      collected.await()
    }

    assertEquals(listOf(TerminalEvent.Ready, TerminalEvent.Ended("the shell exited")), events)
  }

  /**
   * A refused upgrade ends the stream **with its status**, rather than looking like an empty shell.
   *
   * `TermSocket.get` raises 404 for a name the server does not have, which is what a terminal from
   * a previous server looks like after the idle backstop replaced it.
   */
  @Test
  fun attach_toATerminalTheServerDoesNotHaveEndsWithTheReason() = runTest {
    val opener = ScriptedTerminalSocketOpener(setUpImmediately = false)
    val gateway = gateway(opener)

    val events = coroutineScope {
      val collected = async { gateway.attach(TerminalSession("gone")).toList() }
      yield()
      opener.failHandshake("http 404")
      collected.await()
    }

    val ended = events.filterIsInstance<TerminalEvent.Ended>().single()
    assertTrue("expected the status in '${ended.reason}'", "404" in ended.reason)
  }

  /**
   * **A write pending on a socket that dies fails rather than hanging forever.**
   *
   * Without this the 404 above would leave every keystroke suspended for the life of the app with
   * nothing logged - the shape of failure that took six sightings to name. The readiness signal is
   * completed exceptionally, so the waiter learns what happened rather than merely stopping.
   */
  @Test
  fun send_failsWithTheReasonWhenTheSocketDiesBeforeSetup() = runTest {
    val opener = ScriptedTerminalSocketOpener(setUpImmediately = false)
    val gateway = gateway(opener)
    val session = gateway.open()

    val failure = coroutineScope {
      val attached = launch { gateway.attach(session).collect { } }
      val typed = async { runCatching { gateway.send(session, "ls\n") }.exceptionOrNull() }
      yield()
      opener.failHandshake("http 404")
      typed.await().also { attached.cancel() }
    }

    assertTrue("expected the reason in '${failure?.message}'", failure?.message?.contains("404") == true)
  }

  /** A second attach is a re-attach: the server replays `read_buffer` into it, so it is a new socket. */
  @Test
  fun attach_afterTheFirstCollectionStoppedOpensAFreshSocket() = runTest {
    val opener = ScriptedTerminalSocketOpener()
    val gateway = gateway(opener)
    val session = gateway.open()

    gateway.attach(session).take(1).toList()
    gateway.attach(session).take(1).toList()

    assertEquals(2, opener.openCount)
  }

  /**
   * The stage the cold-path measurement reads: `setup` is the server's "this pty is live"
   * signal (`default.ts:301-307`), so it is the terminal's line between `SOCKET_OPENED` and the
   * first output the user can see - and a logcat timeline without it cannot say when a shell became
   * usable.
   */
  @Test
  fun attach_marksReadyWhenTheSetupFrameLands() = runTest {
    val log = RecordingSpaceLifecycleLog()
    val opener = ScriptedTerminalSocketOpener(setUpImmediately = false)
    val gateway = gateway(opener, log)
    val session = gateway.open()

    coroutineScope {
      val attached = launch { gateway.attach(session).collect { } }
      yield()
      assertFalse(TerminalLog.READY in log.stages)

      opener.finishSetup()
      yield()
      assertTrue(TerminalLog.READY in log.stages)
      assertEquals(session.name, log.fieldsOf(TerminalLog.READY)["terminal"])
      attached.cancel()
    }
  }

  private fun gateway(opener: ScriptedTerminalSocketOpener, log: SpaceLifecycleLog = SpaceLifecycleLog.Silent) =
    RealTerminalGateway(opener, lifecycle, log)
}
