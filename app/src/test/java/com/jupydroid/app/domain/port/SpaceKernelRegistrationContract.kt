package com.jupydroid.app.domain.port

import com.jupydroid.app.domain.model.SpaceInterpreter
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [SpaceKernelRegistration] must do, whoever implements it: an unknown Space is
 * refused by name, and a register's events are the script's output lines and then exactly one
 * terminal [SpaceShellEvent.Finished] - the same shape [SpaceShell.run] owes, because the
 * register IS a bounded shell run and its exit status is the script's own verdict. Which
 * interpreters a Space lists and the command's text are each implementation's own pins.
 */
abstract class SpaceKernelRegistrationContract {
  /** An implementation that knows [SPACE_ID] - the fake seeds it, the adapter a Space's directory. */
  protected abstract fun registration(): SpaceKernelRegistration

  @Test
  fun interpreters_ofAnUnknownSpaceIsRefusedNamingIt() = runTest {
    val failure = runCatching { registration().interpreters("space-404") }.exceptionOrNull()

    assertTrue(
      "expected IllegalArgumentException naming space-404, got $failure",
      failure is IllegalArgumentException && "space-404" in failure.message.orEmpty(),
    )
  }

  @Test
  fun aRegisterEndsWithOneFinishedEventAfterItsOutputLines() = runTest {
    val events = registration().register(SPACE_ID, MUSL.path).toList()

    assertTrue("a register emitted nothing", events.isNotEmpty())
    assertTrue("the last event is not Finished: ${events.lastOrNull()}", events.last() is SpaceShellEvent.Finished)
    assertTrue("an Output follows the Finished: $events", events.dropLast(1).all { it is SpaceShellEvent.Output })
  }

  protected companion object {
    const val SPACE_ID = "space-1"
    val MUSL = SpaceInterpreter("/usr/bin/python3", isGnu = false)
    val GNU = SpaceInterpreter("/opt/gnu/python/bin/python3.12", isGnu = true)
  }
}
