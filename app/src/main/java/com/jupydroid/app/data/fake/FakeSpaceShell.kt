package com.jupydroid.app.data.fake

import com.jupydroid.app.domain.port.SpaceShell
import com.jupydroid.app.domain.port.SpaceShellEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration

/** One [SpaceShell.run] as it was asked, recorded when its flow is collected - the flow is cold. */
data class SpaceShellRun(val spaceId: String, val script: String, val timeout: Duration)

/**
 * A [SpaceShell] that runs nothing: every run is recorded and answered from [eventsFor].
 *
 * The default echoes the script's own lines and exits cleanly - the fake kernel's shape
 * (`FakeKernelGateway` echoes the cell's source back). A test scripts what a run emits through
 * [eventsFor], including a suspend that never returns for the run a cancel must end.
 */
class FakeSpaceShell : SpaceShell {
  /** Every run collected, oldest first - a `run` call alone records nothing. */
  val runs = mutableListOf<SpaceShellRun>()

  /** What each run emits, in order; the default is the script echoed and a clean exit. */
  var eventsFor: suspend (SpaceShellRun) -> List<SpaceShellEvent> = { run ->
    run.script.lines().map { SpaceShellEvent.Output(it) } + SpaceShellEvent.Finished(EXIT_OK)
  }

  override fun run(spaceId: String, script: String, timeout: Duration): Flow<SpaceShellEvent> = flow {
    val run = SpaceShellRun(spaceId, script, timeout)
    runs += run
    eventsFor(run).forEach { emit(it) }
  }

  private companion object {
    const val EXIT_OK = 0
  }
}
