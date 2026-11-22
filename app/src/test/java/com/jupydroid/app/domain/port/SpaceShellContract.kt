package com.jupydroid.app.domain.port

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 * What every [SpaceShell] must do, whoever implements it: a run's events are the script's output
 * lines and then exactly one terminal [SpaceShellEvent.Finished] carrying the exit status, and
 * the flow completes - a bounded script always ends, which is what the timeout buys. Exit codes,
 * the bound itself and cancellation are each implementation's own pins.
 */
abstract class SpaceShellContract {
  protected abstract fun shell(): SpaceShell

  @Test
  fun aRunEndsWithOneFinishedEventAfterItsOutputLines() = runTest {
    val events = shell().run("base", "echo hello", 1.minutes).toList()

    assertTrue("a run emitted nothing", events.isNotEmpty())
    assertTrue("the last event is not Finished: ${events.lastOrNull()}", events.last() is SpaceShellEvent.Finished)
    assertTrue(
      "an Output follows the Finished: $events",
      events.dropLast(1).all { it is SpaceShellEvent.Output },
    )
  }
}
