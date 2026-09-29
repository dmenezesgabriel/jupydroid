package com.jupydroid.app.data.space

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * P1c's decision half: when the app goes away, the server should stop - but a
 * backgrounded computation must be allowed to finish. The gate
 * backgrounds the app mid-run and shows the
 * cell completes, then backgrounds it idle and shows the server stops; both halves
 * are this class's contract.
 */
class IdleServerStopperTest {
  private val stops = mutableListOf<String>()

  @Test
  fun stoppedWhileIdle_stopsTheServerAfterTheGracePeriod() = runTest {
    val stopper = stopper(MutableStateFlow(0))

    stopper.onAppStopped()
    advanceTimeBy(29.seconds.inWholeMilliseconds)
    runCurrent()
    assertTrue("server must survive the grace window, stopped: $stops", stops.isEmpty())

    advanceTimeBy(2.seconds.inWholeMilliseconds)
    runCurrent()
    assertEquals(listOf("stopped"), stops)
  }

  @Test
  fun stoppedWhileACellRuns_waitsForTheCellBeforeStopping() = runTest {
    val running = MutableStateFlow(1)
    val stopper = stopper(running)

    stopper.onAppStopped()
    advanceTimeBy(5.seconds.inWholeMilliseconds)
    runCurrent()
    assertTrue("a running cell must not be killed, stopped: $stops", stops.isEmpty())

    running.value = 0 // the cell finished, in the background
    advanceTimeBy(31.seconds.inWholeMilliseconds) // and the grace counts down
    runCurrent()
    assertEquals(listOf("stopped"), stops)
  }

  /**
   * The grace is thirty seconds of *continuous* idleness, not thirty seconds after the first
   * idle instant. Run All finishes one cell before it starts the next, so the count passes
   * through zero between them; so does a user who runs a second cell while the app is away.
   * A grace that only samples once would stop the server under both.
   */
  @Test
  fun aCellThatStartsDuringTheGrace_keepsTheServer() = runTest {
    val running = MutableStateFlow(0)
    val stopper = stopper(running)

    stopper.onAppStopped()
    advanceTimeBy(10.seconds.inWholeMilliseconds)
    runCurrent()
    running.value = 1 // the next cell of a Run All picks up where the last left off
    advanceTimeBy(25.seconds.inWholeMilliseconds)
    runCurrent()
    assertTrue("the grace must restart when something runs again, stopped: $stops", stops.isEmpty())

    running.value = 0
    advanceTimeBy(31.seconds.inWholeMilliseconds)
    runCurrent()
    assertEquals(listOf("stopped"), stops)
  }

  @Test
  fun startedBeforeTheGraceExpires_keepsTheServerRunning() = runTest {
    val stopper = stopper(MutableStateFlow(0))

    stopper.onAppStopped()
    advanceTimeBy(10.seconds.inWholeMilliseconds)
    runCurrent()
    stopper.onAppStarted()

    advanceTimeBy(60.seconds.inWholeMilliseconds)
    runCurrent()
    assertTrue("returning to the app must cancel the pending stop, stopped: $stops", stops.isEmpty())
  }

  @Test
  fun stoppedAgain_restartsTheCountdownRatherThanStoppingTwice() = runTest {
    val stopper = stopper(MutableStateFlow(0))

    stopper.onAppStopped()
    advanceTimeBy(20.seconds.inWholeMilliseconds)
    runCurrent()
    stopper.onAppStopped() // the same backgrounding, reported again
    advanceTimeBy(20.seconds.inWholeMilliseconds)
    runCurrent()
    assertTrue("the countdown must restart, not stack, stopped: $stops", stops.isEmpty())

    advanceTimeBy(11.seconds.inWholeMilliseconds)
    runCurrent()
    assertEquals(listOf("stopped"), stops)
  }

  /**
   * A live terminal must hold the server open exactly as an in-flight cell does - the server side
   * of this is `jupyter_server_terminals`' `current_activity()`, reported by `any_activity()`
   * (`serverapp.py:2602-2618`); this class is the half of that this app owns.
   */
  @Test
  fun stoppedWhileATerminalIsOpen_waitsForItBeforeStopping() = runTest {
    val terminalsOpen = MutableStateFlow(1)
    val stopper = stopper(MutableStateFlow(0), terminalsOpen)

    stopper.onAppStopped()
    advanceTimeBy(5.seconds.inWholeMilliseconds)
    runCurrent()
    assertTrue("a live terminal must not be killed, stopped: $stops", stops.isEmpty())

    terminalsOpen.value = 0 // the shell exited, in the background
    advanceTimeBy(31.seconds.inWholeMilliseconds) // and the grace counts down
    runCurrent()
    assertEquals(listOf("stopped"), stops)
  }

  private fun TestScope.stopper(cellsInFlight: StateFlow<Int>, terminalsOpen: StateFlow<Int> = MutableStateFlow(0)) =
    IdleServerStopper(
      scope = this,
      cellsInFlight = cellsInFlight,
      terminalsOpen = terminalsOpen,
      stopServer = { stops += "stopped" },
      grace = 30.seconds,
    )
}
