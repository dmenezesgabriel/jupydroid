package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.ActiveTerminals
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach

/**
 * A [TerminalGateway] that keeps [activeTerminals] believing what the server believes, and
 * nothing else - the six ops pass through to [gateway] with the counting seam wrapped around
 * the three places the truth changes.
 *
 * **The seam lives here rather than inside [RealTerminalGateway] or [BaseSpaceTerminalGateway]**,
 * because the latter rebuilds its underlying gateway whenever the Space's server restarts
 * (`PerRunningServer`); a belief kept there would reset to zero across that restart even though
 * whether this process still has a shell open is a question that outlives one server instance.
 * Wrapping the port instead keeps it right regardless of which concrete gateway - or how many
 * of them across restarts - actually opened and closed each terminal.
 *
 * ```
 * val terminals = ActiveTerminals()
 * val gateway = ActivityTrackedTerminalGateway(FakeTerminalGateway(), terminals, spaceId = "base")
 * gateway.open() // terminals.open emits 1
 * ```
 */
class ActivityTrackedTerminalGateway(
  private val gateway: TerminalGateway,
  private val activeTerminals: ActiveTerminals,
  /** The Space whose server [gateway] talks to - what its names are counted under. */
  private val spaceId: String,
) : TerminalGateway {
  override suspend fun open(cwd: String?): TerminalSession =
    gateway.open(cwd).also { activeTerminals.minted(spaceId, it.name) }

  /** The server's list is the truth the counter approximates - every ask re-aligns it. */
  override suspend fun running(): List<TerminalSession> =
    gateway.running().also { activeTerminals.reconciled(spaceId, it.map(TerminalSession::name)) }

  /**
   * [TerminalEvent.Ended] is where a death that was never a [close] surfaces - a shell that
   * exits, a name a replaced server refuses at upgrade. Re-listing rather than decrementing:
   * `Ended` alone cannot tell a dead pty from a clean socket close (`TermSocket.on_close`
   * drops only the client), and `running()` is the ask that can.
   */
  override fun attach(session: TerminalSession): Flow<TerminalEvent> = gateway.attach(session).onEach { event ->
    if (event is TerminalEvent.Ended) reconcile()
  }

  override suspend fun send(session: TerminalSession, text: String) = gateway.send(session, text)

  override suspend fun resize(session: TerminalSession, rows: Int, cols: Int) = gateway.resize(session, rows, cols)

  override suspend fun close(session: TerminalSession) {
    gateway.close(session)
    activeTerminals.ended(spaceId, session.name)
  }

  private suspend fun reconcile() {
    val names = try {
      gateway.running().map(TerminalSession::name)
    } catch (cancelled: CancellationException) {
      throw cancelled
    } catch (_: Exception) {
      // A refused list keeps the last belief, as `keepingLastGood` keeps the panel's rows.
      return
    }
    activeTerminals.reconciled(spaceId, names)
  }
}
