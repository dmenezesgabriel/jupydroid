package com.jupydroid.app.domain.model

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * How many terminals the app has opened and not yet closed.
 *
 * **A counter of its own, not state inside a gateway - because the gateway backing a Space is
 * rebuilt whenever its server restarts.** `BaseSpaceTerminalGateway` builds a fresh
 * `RealTerminalGateway` per running server, so state kept there would reset to zero across a
 * restart even though the question this answers - "does this process currently have a shell it
 * opened and has not closed" - spans the whole app's life, not one server instance's. Mirrors
 * `ActiveCellRuns`' reasoning, but as two independent calls rather than one wrapped block: a
 * terminal's lifetime runs from [opened] to a much later, maybe-never [closed], not across a
 * single suspend call the way a cell run does.
 *
 * Owned by `AppContainer`, moved by `ActivityTrackedTerminalGateway`, read by `IdleServerStopper`.
 *
 * ```
 * val terminals = ActiveTerminals()
 * terminals.opened() // a shell now exists
 * terminals.closed() // it doesn't anymore
 * ```
 */
class ActiveTerminals {
  private val _open = MutableStateFlow(0)
  val open: StateFlow<Int> = _open.asStateFlow()

  fun opened() {
    _open.update { it + 1 }
  }

  /** Floored at zero: a stray extra close must not read as a terminal being open. */
  fun closed() {
    _open.update { (it - 1).coerceAtLeast(0) }
  }
}
