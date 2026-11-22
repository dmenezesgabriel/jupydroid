package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * What only this implementation can be asked, beside
 * [com.jupydroid.app.domain.port.SpaceShellContract]: what it **records** - a port whose arguments
 * vanish cannot tell a screen that passed the script it showed from one that did not - and that
 * the flow is cold.
 */
class FakeSpaceShellTest {
  private val shell = FakeSpaceShell()

  @Test
  fun run_recordsNothingUntilTheFlowIsCollected() {
    shell.run("base", "echo hi", 1.minutes)

    assertTrue(shell.runs.isEmpty())
  }

  @Test
  fun run_recordsTheSpaceScriptAndTimeoutItWasAskedFor() = runTest {
    shell.run("base", "echo hi", 30.seconds).toList()

    assertEquals(listOf(SpaceShellRun("base", "echo hi", 30.seconds)), shell.runs)
  }

  @Test
  fun eventsFor_scriptsWhatARunEmits() = runTest {
    shell.eventsFor = { listOf(SpaceShellEvent.Output("half"), SpaceShellEvent.Finished(3)) }

    val events = shell.run("base", "anything", 1.minutes).toList()

    assertEquals(listOf(SpaceShellEvent.Output("half"), SpaceShellEvent.Finished(3)), events)
  }

  @Test
  fun theDefaultEchoesTheScriptAndExitsCleanly() = runTest {
    val events = shell.run("base", "echo hi\necho bye", 1.minutes).toList()

    assertEquals(
      listOf(
        SpaceShellEvent.Output("echo hi"),
        SpaceShellEvent.Output("echo bye"),
        SpaceShellEvent.Finished(0),
      ),
      events,
    )
  }
}
