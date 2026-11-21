package com.jupydroid.app.domain.port

import kotlinx.coroutines.flow.Flow
import kotlin.time.Duration

/**
 * What a [SpaceShell.run] emits: the script's output lines, then exactly one terminal event.
 *
 * [SpaceShellEvent.Finished]'s exit status is the script's own, so `128 + SIGKILL` - 137 -
 * reads as killed at the timeout bound, the same meaning `requireStartupScriptSucceeded` gives
 * it (`app/src/main/java/com/jupydroid/app/data/space/SpaceStartupScript.kt`).
 */
sealed interface SpaceShellEvent {
  /** One line of the script's merged stdout and stderr, its terminator stripped. */
  data class Output(val line: String) : SpaceShellEvent

  /** The script exited - [exitCode] is its status, 137 when the bound killed it. */
  data class Finished(val exitCode: Int) : SpaceShellEvent
}

/**
 * A script run inside a Space's own shell, bounded and streamed - for what a stopped Space's
 * disk cannot answer, like whether a harness's executable is on its PATH or how to install one.
 *
 * The Space's `jupyter_server` is not involved: the process runs under proot with the Space as
 * `/` (ADR-0073), so it answers for a Space that is stopped - the only state the Settings dialog
 * offers it in anyway.
 *
 * ```
 * spaceShell.run("base", "command -v opencode", 15.seconds).toList()
 * ```
 */
interface SpaceShell {
  /**
   * Runs [script] - a POSIX sh script, handed over whole and never templated into a command -
   * in [spaceId]'s shell and streams what it prints, bounded by [timeout].
   *
   * Cold: nothing is ensured, installed or exec'd until the flow is collected. Cancelling the
   * collection ends the run; whatever the script left running stays bounded by the Space-side
   * timeout, and the install scripts are idempotent so a re-run resumes.
   *
   * @throws IllegalArgumentException collected when no Space has [spaceId].
   */
  fun run(spaceId: String, script: String, timeout: Duration): Flow<SpaceShellEvent>
}
