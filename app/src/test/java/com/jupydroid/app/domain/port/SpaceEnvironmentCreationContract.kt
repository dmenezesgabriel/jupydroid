package com.jupydroid.app.domain.port

import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [SpaceEnvironmentCreation] must do, whoever implements it: a create's events are
 * the script's output lines and then exactly one terminal [SpaceShellEvent.Finished] - the same
 * shape [SpaceShell.run] owes, because the create IS a bounded shell run and its exit status is
 * the script's own verdict. The bound itself and cancellation are each implementation's own pins.
 */
abstract class SpaceEnvironmentCreationContract {
  protected abstract fun creation(): SpaceEnvironmentCreation

  @Test
  fun aCreateEndsWithOneFinishedEventAfterItsOutputLines() = runTest {
    val events = creation().create(SPACE_ID, PROJECT).toList()

    assertTrue("a create emitted nothing", events.isNotEmpty())
    assertTrue("the last event is not Finished: ${events.lastOrNull()}", events.last() is SpaceShellEvent.Finished)
    assertTrue(
      "an Output follows the Finished: $events",
      events.dropLast(1).all { it is SpaceShellEvent.Output },
    )
  }

  protected companion object {
    const val SPACE_ID = "space-1"
    const val PROJECT = "proj-a"
  }
}
