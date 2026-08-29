package com.jupydroid.app.domain.port

import kotlinx.coroutines.flow.Flow

/**
 * A shell running in a pseudoterminal on the Space's server.
 *
 * **One id, unlike a kernel's three.** The server names a terminal and that name addresses
 * everything: `DELETE api/terminals/<name>` ends it and `/terminals/websocket/<name>` carries its
 * bytes. There is no client-side id because there is nothing to sign - a terminal's socket is the
 * session (`terminado/websocket.py`, `TermSocket.open`).
 */
data class TerminalSession(val name: String)

/**
 * What arrives on an attached terminal, in the only three kinds a caller can act on.
 *
 * The wire has five message types and this has three, which is the reference's own reduction:
 * `set_size` and `stdin` only ever travel *to* the server, and of what comes back `setup` is a
 * readiness signal rather than content (`packages/services/src/terminal/default.ts:301-307`
 * ignores every frame until it arrives).
 */
sealed interface TerminalEvent {
  /**
   * The server's `["setup", {}]`, and the point from which this terminal may be written to.
   *
   * First on every attach, including a re-attach - `TermSocket.open` sends it before draining
   * `read_buffer`, so the scrollback that follows arrives *after* readiness rather than racing it.
   */
  data object Ready : TerminalEvent

  /** `["stdout", text]`. Bytes exactly as the pty produced them, escape sequences included. */
  data class Output(val text: String) : TerminalEvent

  /**
   * Terminal, and [reason] always says why - the shell exited (`["disconnect", 1]`), or the socket
   * went. There is no bare "closed": a shell that vanishes with no reason is the failure P4b spent
   * two commits diagnosing.
   */
  data class Ended(val reason: String) : TerminalEvent
}

/**
 * The terminals of one Space's `jupyter_server`.
 *
 * ```
 * val terminal = gateway.open(cwd = null)
 * gateway.attach(terminal).collect { event -> … }   // Ready, then Output
 * gateway.send(terminal, "ls\n")
 * ```
 */
interface TerminalGateway {
  /**
   * Starts a shell and returns the terminal the server named for it.
   *
   * [cwd] is a path **relative to the server's root**, or null for the root itself - which is what
   * JupyterLab's launcher sends and what its file browser overrides for "Open Terminal Here"
   * (`packages/terminal-extension/src/index.ts:489-514`, `cwd: item.path`). Relative rather than
   * absolute because that is what the handler resolves and what the app already speaks; it works
   * because the server's cwd is its root_dir (`docs/DECISIONS.md`).
   */
  suspend fun open(cwd: String? = null): TerminalSession

  /** Every terminal this server currently holds, which a restarted server answers as empty. */
  suspend fun running(): List<TerminalSession>

  /**
   * The bytes of [session], from the moment this flow is collected until it is cancelled.
   *
   * **Cold, and it owns the socket.** Nothing is read before there is a collector, because a
   * shell writes its prompt the instant it is spawned and a stream nobody is listening to would
   * drop it. Cancelling the collection closes the socket and leaves the shell running - which is
   * what makes leaving the screen survivable, since a later attach is a re-attach and the server
   * replays what was missed.
   */
  fun attach(session: TerminalSession): Flow<TerminalEvent>

  /**
   * Types [text] into the shell, verbatim: `"ls\n"` runs `ls`, `"\u0003"` is Ctrl-C.
   *
   * **Suspends until [attach] has this terminal ready**, rather than failing before it. The
   * reference queues for exactly as long (`default.ts:_sendMessage` pushes to `_pendingMessages`
   * until the `setup` frame arrives), and a keystroke a millisecond early is still a keystroke the
   * user typed. It is also what makes writing into a socket the server has not associated yet
   * impossible - which would otherwise be silent, the frame being accepted and dropped.
   */
  suspend fun send(session: TerminalSession, text: String)

  /**
   * Tells the shell how large its screen is, so `vi` and line editing wrap where the app draws.
   *
   * Rows before columns, which is the wire's order and not a preference: terminado reads the
   * frame as `command[1:3]` (`terminado/websocket.py`, `on_message`).
   *
   * Waits for an attach exactly as [send] does, and for the same reason: `set_size` is a frame on
   * the same socket, so a resize is a write and not a property of the terminal.
   */
  suspend fun resize(session: TerminalSession, rows: Int, cols: Int)

  /**
   * Ends the terminal: the shell is killed and its scrollback is gone.
   *
   * Distinct from cancelling [attach], which only stops reading. A terminal the server has already
   * forgotten is not an error - it is gone, which is what the caller asked for, and it is the
   * reference's own rule for a 404 here (`restapi.ts:107-113` warns and returns).
   */
  suspend fun close(session: TerminalSession)
}
