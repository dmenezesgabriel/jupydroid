package com.jupydroid.app.data.space

import com.jupydroid.app.data.fake.FakeSpaceShell
import com.jupydroid.app.data.fake.SpaceShellRun
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.minutes

/**
 * The create act's shape, pinned where a UI test cannot see it: which script the Space's shell
 * is asked to run, how the project is quoted into it, and the bound a venv's pip gets.
 */
class SpaceShellEnvironmentCreationTest {
  private val shell = FakeSpaceShell()
  private val creation = SpaceShellEnvironmentCreation(shell)

  @Test
  fun create_runsTheHelperScriptInTheSpaceBounded() = runTest {
    creation.create("space-1", "proj-a").toList()

    assertEquals(
      listOf(SpaceShellRun("space-1", "jupydroid-env create 'proj-a'", 5.minutes)),
      shell.runs,
    )
  }

  /** A project named `o'brien` or `My Project` must reach the script as one word, not a split command. */
  @Test
  fun create_quotesTheProjectSoItsNameCannotSplitTheCommand() = runTest {
    creation.create("space-1", "My Project").toList()
    creation.create("space-1", "o'brien").toList()

    assertEquals("jupydroid-env create 'My Project'", shell.runs[0].script)
    assertEquals("jupydroid-env create 'o'\\''brien'", shell.runs[1].script)
  }

  /** The flow is cold like `SpaceShell.run`'s: nothing is ensured or exec'd until it is collected. */
  @Test
  fun create_isColdNothingRunsUntilTheFlowIsCollected() {
    creation.create("space-1", "proj-a")

    assertEquals(emptyList<SpaceShellRun>(), shell.runs)
  }

  @Test
  fun create_streamsTheRunsEventsThrough() = runTest {
    shell.eventsFor = { listOf(SpaceShellEvent.Output("making venv"), SpaceShellEvent.Finished(3)) }

    val events = creation.create("space-1", "proj-a").toList()

    assertEquals(listOf(SpaceShellEvent.Output("making venv"), SpaceShellEvent.Finished(3)), events)
  }
}
