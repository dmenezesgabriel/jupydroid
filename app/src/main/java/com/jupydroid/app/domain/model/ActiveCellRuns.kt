package com.jupydroid.app.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * How many cell runs the app has in flight, counted from the moment one is asked for.
 *
 * **The moment matters, and it is the whole reason this exists rather than a counter inside the
 * gateway.** A run begins at the tap and the kernel starting is part of it: installing the Space,
 * starting `jupyter_server` and starting the kernel took fourteen seconds on the tablet before the
 * cell's first byte went anywhere (`evidence/p1c-app-death-backstop/`). A count that only rises
 * when output starts arriving says "nothing is running" for exactly the stretch that is slowest to
 * lose - which is how P1c's backstop came to kill a cell it was written to protect.
 *
 * Owned by `AppContainer`, moved by `NotebookKernel`, read by `IdleServerStopper`.
 *
 * ```
 * val runs = ActiveCellRuns()
 * runs.whileRunning { gateway.execute(session, cell.id, cell.source).collect(::render) }
 * ```
 */
class ActiveCellRuns {
  private val _inFlight = MutableStateFlow(0)
  val inFlight: StateFlow<Int> = _inFlight.asStateFlow()

  /** Counts [run] as in flight for as long as it lasts, however it ends. */
  suspend fun <T> whileRunning(run: suspend () -> T): T {
    _inFlight.update { it + 1 }
    try {
      return run()
    } finally {
      _inFlight.update { it - 1 }
    }
  }
}
