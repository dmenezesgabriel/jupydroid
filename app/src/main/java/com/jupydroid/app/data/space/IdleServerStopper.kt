package com.jupydroid.app.data.space

import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * P1c's decision half: when the app is left, the server should stop - but a backgrounded
 * computation must be allowed to finish.
 *
 * [onAppStopped] starts (or restarts) the countdown and [onAppStarted] cancels it, so briefly
 * backgrounding does not kill the server. The countdown wants [grace] of **continuous** idleness:
 * anything that starts running restarts it. That is not caution, it is Run All, which finishes one
 * cell before starting the next and so passes through "nothing is running" between every pair of
 * them.
 *
 * The waiting is done on [cellsInFlight] rather than by polling, so the grace is exact and a
 * backgrounded app wakes for nothing.
 *
 * ```
 * val stopper = IdleServerStopper(
 *   scope = appContainer.applicationScope,
 *   cellsInFlight = appContainer.activeCellRuns.inFlight,
 *   stopServer = { appContainer.serverLauncher.stop() },
 *   grace = 30.seconds,
 * )
 * ```
 */
class IdleServerStopper(
  private val scope: CoroutineScope,
  private val cellsInFlight: StateFlow<Int>,
  private val stopServer: suspend () -> Unit,
  private val grace: Duration,
) {
  private var stopJob: Job? = null

  fun onAppStopped() {
    stopJob?.cancel()
    stopJob = scope.launch {
      while (true) {
        cellsInFlight.first { nothing -> nothing == 0 }
        // Null means the grace elapsed with nothing having started, which is the whole condition.
        val started = withTimeoutOrNull(grace) { cellsInFlight.first { running -> running > 0 } }
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
