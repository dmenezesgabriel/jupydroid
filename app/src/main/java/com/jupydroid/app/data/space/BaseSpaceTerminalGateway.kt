package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * The terminal gateway for the base Space's server, rebuilt whenever that server is replaced.
 *
 * ```
 * BaseSpaceTerminalGateway(
 *   server = baseSpaceServer(context, baseSpaceServerLauncher(context))!!,
 *   gatewayFor = { RealTerminalGateway(OkHttpTerminalSocketOpener(it), OkHttpTerminalLifecycle(it)) },
 * )
 * ```
 *
 * **Thinner than [BaseSpaceKernelGateway], and the difference is the protocol's rather than a
 * shortcut.** That class has to track which server issued which session, because a stale kernel
 * session fails as an undebuggable `404` on a websocket upgrade. A terminal needs none of that
 * bookkeeping: its name is the server's own, a replaced server lists no terminals at all, and an
 * attach to a name the new server never minted is refused by `TermSocket.get` and surfaces as
 * [TerminalEvent.Ended] carrying that 404 - which is a truthful answer rather than a protocol
 * error leaking. Adding a registry here would be inventing a state the wire already reports.
 */
class BaseSpaceTerminalGateway(server: BaseSpaceServer, gatewayFor: (ServerConnection) -> TerminalGateway) :
  TerminalGateway {
  private val live = PerRunningServer(server, gatewayFor)

  /** Starts the Space if it is not up: asking for a terminal is asking for the runtime. */
  override suspend fun open(cwd: String?): TerminalSession = live.get().open(cwd)

  /**
   * The terminals of the server that is *already* running, and none where none is.
   *
   * It must not start one. A screen asking what is open must not be what boots a seven-second
   * runtime - and the honest answer for a stopped server is that it holds no terminals, which is
   * exactly what a freshly started one would have said anyway.
   */
  override suspend fun running(): List<TerminalSession> = live.liveOrNull()?.running().orEmpty()

  override fun attach(session: TerminalSession): Flow<TerminalEvent> = flow {
    emitAll(live.get().attach(session))
  }

  override suspend fun send(session: TerminalSession, text: String) = live.get().send(session, text)

  override suspend fun resize(session: TerminalSession, rows: Int, cols: Int) = live.get().resize(session, rows, cols)

  /**
   * Ends the terminal, and does nothing at all where no server is running.
   *
   * [BaseSpaceKernelGateway]'s rule for the same reason: the shell died with the server the
   * backstop stopped, and booting a runtime to delete a terminal that no longer exists would spend
   * seven seconds and ~130 MB undoing what the lifecycle backstop did.
   */
  override suspend fun close(session: TerminalSession) {
    live.liveOrNull()?.close(session)
  }
}
