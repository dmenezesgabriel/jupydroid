package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.ActiveTerminals
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.flow.Flow

/**
 * A [TerminalGateway] that reports every open terminal to [activeTerminals], and nothing else -
 * everything but [open] and [close] passes straight through to [gateway].
 *
 * **The seam lives here rather than inside [RealTerminalGateway] or [BaseSpaceTerminalGateway]**,
 * because the latter rebuilds its underlying gateway whenever the Space's server restarts
 * (`PerRunningServer`); a counter kept there would reset to zero across that restart even though
 * whether this process still has a shell open is a question that outlives one server instance.
 * Wrapping the port instead keeps the count correct regardless of which concrete gateway - or how
 * many of them across restarts - actually opened and closed each terminal.
 *
 * ```
 * val terminals = ActiveTerminals()
 * val gateway = ActivityTrackedTerminalGateway(FakeTerminalGateway(), terminals)
 * gateway.open() // terminals.open.value == 1
 * ```
 */
class ActivityTrackedTerminalGateway(
  private val gateway: TerminalGateway,
  private val activeTerminals: ActiveTerminals,
) : TerminalGateway {
  override suspend fun open(cwd: String?): TerminalSession = gateway.open(cwd).also { activeTerminals.opened() }

  override suspend fun running(): List<TerminalSession> = gateway.running()

  override fun attach(session: TerminalSession): Flow<TerminalEvent> = gateway.attach(session)

  override suspend fun send(session: TerminalSession, text: String) = gateway.send(session, text)

  override suspend fun resize(session: TerminalSession, rows: Int, cols: Int) = gateway.resize(session, rows, cols)

  override suspend fun close(session: TerminalSession) {
    gateway.close(session)
    activeTerminals.closed()
  }
}
