package com.jupydroid.app.data.space

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

/**
 * The decision half: when the app is left, the server should stop - but a backgrounded
 * computation must be allowed to finish, and a live terminal must be treated exactly the same way.
 *
 * [onAppStopped] starts (or restarts) the countdown and [onAppStarted] cancels it, so briefly
 * backgrounding does not kill the server. The countdown wants [grace] of **continuous** idleness:
 * anything that starts running restarts it. That is not caution, it is Run All, which finishes one
 * cell before starting the next and so passes through "nothing is running" between every pair of
 * them.
 *
 * The waiting is done on [cellsInFlight] and [terminalsOpen] rather than by polling, so the grace
 * is exact and a backgrounded app wakes for nothing. A terminal counts for the same reason a cell
 * does: `jupyter_server_terminals`' `current_activity()` keeps the reference server up under an
 * open shell (`serverapp.py:2602-2618`), and this app's own stopper has to agree with it.
 *
 * ```
 * val stopper = IdleServerStopper(
 *   scope = appContainer.applicationScope,
 *   cellsInFlight = appContainer.activeCellRuns.inFlight,
 *   terminalsOpen = appContainer.activeTerminals.open,
 *   stopServer = { appContainer.spaceServers.stopAll() },
 *   grace = 30.seconds,
 * )
 * ```
 */
class IdleServerStopper(
  private val scope: CoroutineScope,
  private val cellsInFlight: StateFlow<Int>,
  /** `ActiveTerminals.open`'s own shape - a count derived from the set, not a stored state. */
  private val terminalsOpen: Flow<Int>,
  private val stopServer: suspend () -> Unit,
  private val grace: Duration,
) {
  private var stopJob: Job? = null

  fun onAppStopped() {
    val activity = combine(cellsInFlight, terminalsOpen) { cells, terminals -> cells + terminals }
    stopJob?.cancel()
    stopJob = scope.launch {
      while (true) {
        activity.first { nothing -> nothing == 0 }
        // Null means the grace elapsed with nothing having started, which is the whole condition.
        val started = withTimeoutOrNull(grace) { activity.first { running -> running > 0 } }
        if (started == null) break
      }
      stopServer()
    }
  }

  fun onAppStarted() {
    stopJob?.cancel()
    stopJob = null
  }
}
