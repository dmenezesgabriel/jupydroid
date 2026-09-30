package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.TerminalEvent
import com.jupydroid.app.domain.port.TerminalGateway
import com.jupydroid.app.domain.port.TerminalSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** A terminal socket ended before the caller let go of it. */
class TerminalChannelException(reason: String) : IllegalStateException(reason)

/**
 * The real [TerminalGateway]: REST for the lifecycle, one socket per attached terminal.
 *
 * ```
 * RealTerminalGateway(OkHttpTerminalSocketOpener(conn), OkHttpTerminalLifecycle(conn))
 *   .attach(TerminalSession("1"))
 * ```
 *
 * **The socket belongs to the attach, not to the gateway**, which is where this parts company with
 * [RealKernelGateway]. A kernel's socket is opened on the first execution and held until the
 * document is closed, because the server counts connections against the kernel and a socket per
 * cell would be a handshake per run. A terminal has no such per-request traffic: its socket *is*
 * the reading of it, so it is opened when the flow is collected and closed when the collection
 * stops - which is also what makes leaving the screen cheap, since the shell lives on and
 * `TermSocket.open` replays `read_buffer` to whatever attaches next.
 */
class RealTerminalGateway(
  private val opener: TerminalSocketOpener,
  private val lifecycle: TerminalLifecycle,
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
) : TerminalGateway {
  /**
   * The live attach per terminal name, as state rather than a map behind a lock, so a writer can
   * *wait* for one to appear instead of failing because it arrived first.
   */
  private val attached = MutableStateFlow<Map<String, AttachedTerminal>>(emptyMap())

  override suspend fun open(cwd: String?): TerminalSession = TerminalSession(lifecycle.create(cwd)).also {
    log.reached(TerminalLog.OPENED, mapOf("terminal" to it.name, "cwd" to (cwd ?: "<server root>")))
  }

  override suspend fun running(): List<TerminalSession> = lifecycle.list().map(::TerminalSession)

  override fun attach(session: TerminalSession): Flow<TerminalEvent> = flow {
    val terminal = AttachedTerminal(session.name)
    attached.update { it + (session.name to terminal) }
    try {
      emitAll(terminal.events.onSubscription { terminal.connect() }.upToEnd())
    } finally {
      // Removed by identity: a re-attach registers its own before this flow's `finally` runs, and
      // removing by name alone would take the new attach out along with the old.
      attached.update { if (it[session.name] === terminal) it - session.name else it }
      terminal.close()
    }
  }

  override suspend fun send(session: TerminalSession, text: String) = write(session, TerminalMessage.stdin(text))

  override suspend fun resize(session: TerminalSession, rows: Int, cols: Int) =
    write(session, TerminalMessage.setSize(rows, cols))

  override suspend fun close(session: TerminalSession) {
    lifecycle.terminate(session.name)
    log.reached(TerminalLog.CLOSED, mapOf("terminal" to session.name))
  }

  /**
   * Waits for [session] to be attached and ready, then puts one frame on its socket.
   *
   * **It waits rather than refusing, and that is the reference's behaviour rather than leniency.**
   * `default.ts:_sendMessage` pushes to `_pendingMessages` until the socket reports itself
   * connected, because a screen composes its key handler and its collector independently and a
   * keystroke can genuinely arrive first. Refusing here would make that a crash on a race the UI
   * cannot order.
   *
   * Bounded, because the failure it replaces is worse than an error: a caller that never attaches
   * would otherwise suspend for the life of the app with nothing logged - a silent forever-wait is
   * the one failure mode this app has already spent a week diagnosing once.
   */
  private suspend fun write(session: TerminalSession, frame: String) {
    val terminal = withTimeoutOrNull(ATTACH_TIMEOUT) {
      attached.map { it[session.name] }.filterNotNull().first()
    } ?: throw TerminalChannelException(
      "terminal '${session.name}' was not attached within $ATTACH_TIMEOUT; collect attach() to write to it",
    )
    terminal.awaitReady()
    terminal.send(frame)
  }

  /** [TerminalEvent.Ended] is terminal, so the flow stops on it rather than waiting for a close. */
  private fun Flow<TerminalEvent>.upToEnd(): Flow<TerminalEvent> = transformWhile { event ->
    emit(event)
    event !is TerminalEvent.Ended
  }

  /**
   * One terminal's socket and the frames it has turned into events.
   *
   * [ready] is completed by the server's `setup` frame and by nothing else. That is the reference's
   * connection rule (`default.ts:301-307`) and the reason it matters here is silent failure: a
   * frame written before `TermSocket.open` has appended this client to `terminal.clients` is
   * accepted by the socket and dropped by the server, with nothing raised at either end.
   */
  private inner class AttachedTerminal(private val name: String) {
    /**
     * Buffered so OkHttp's reader thread is never blocked by a slow collector, and **buffered at
     * all** because a zero-capacity [MutableSharedFlow] refuses every `tryEmit` the moment it has
     * a subscriber - which would drop a shell's entire output while reporting each drop.
     *
     * The same size the kernel's socket uses. A shell is burstier than a kernel's shell channel -
     * `pty_read` hands over up to 65536 bytes at a time and `ls` of a large directory arrives as
     * one frame - but the depth that matters is frames in flight, not bytes, and with no replay
     * the flow does not grow between reads.
     */
    val events = MutableSharedFlow<TerminalEvent>(extraBufferCapacity = FRAME_BUFFER)

    private val ready = CompletableDeferred<Unit>()
    private var socket: JupyterSocket? = null

    fun connect() {
      socket = opener.open(name) { event -> onSocketEvent(event) }
      log.reached(TerminalLog.SOCKET_OPENED, mapOf("terminal" to name))
    }

    suspend fun awaitReady() = ready.await()

    fun send(frame: String) {
      val queued = socket?.send(frame) ?: false
      if (!queued) throw TerminalChannelException("terminal '$name' would not take a frame; its socket is ending")
    }

    fun close() {
      socket?.close()
      socket = null
    }

    private fun onSocketEvent(event: JupyterSocketEvent) {
      when (event) {
        // The handshake completing is not readiness: `setup` is, and it is a round trip later.
        JupyterSocketEvent.Opened -> Unit
        is JupyterSocketEvent.Received -> TerminalFrame.read(event.frame)?.let(::publish)
        is JupyterSocketEvent.Ended -> onEnded(event)
      }
    }

    private fun onEnded(event: JupyterSocketEvent.Ended) {
      log.reached(TerminalLog.SOCKET_ENDED, mapOf("terminal" to name, "reason" to event.reason))
      // A socket that ends before `setup` would otherwise leave every writer suspended forever -
      // a stale name answers 404 at upgrade, which is exactly that case.
      ready.completeExceptionally(TerminalChannelException("terminal '$name' ended: ${event.reason}"))
      publish(TerminalEvent.Ended(event.reason))
    }

    /**
     * `tryEmit` rather than `emit`, because this runs on OkHttp's reader thread and must not
     * block it. A drop is reported rather than swallowed: it is output the user will never see.
     */
    private fun publish(event: TerminalEvent) {
      if (events.tryEmit(event)) {
        if (event is TerminalEvent.Ready) {
          // `setup` is the server's "this pty is live" mark - the cold path's own line between
          // SOCKET_OPENED and the first output the user can see.
          log.reached(TerminalLog.READY, mapOf("terminal" to name))
          ready.complete(Unit)
        }
        return
      }
      log.reached(
        TerminalLog.FRAME_DROPPED,
        mapOf("terminal" to name, "subscribers" to "${events.subscriptionCount.value}"),
      )
    }
  }

  private companion object {
    const val FRAME_BUFFER = 256

    /** Long enough for a screen to compose its collector, short enough to be an error not a hang. */
    val ATTACH_TIMEOUT = 10.seconds
  }
}

/**
 * The stages a terminal reports, as constants so a log line and the code that writes it can be
 * found from each other - [KernelLog]'s convention, for a surface that will need it for the same
 * reason: a shell that shows nothing has the same three possible causes a stalled cell had.
 */
internal object TerminalLog {
  const val OPENED = "terminal.opened"
  const val CLOSED = "terminal.closed"
  const val SOCKET_OPENED = "terminal.socket.opened"

  /** The server's `setup` frame - the moment an attached terminal is actually usable. */
  const val READY = "terminal.ready"
  const val SOCKET_ENDED = "terminal.socket.ended"

  /** It printed and the buffer refused it, which loses a shell's output silently. */
  const val FRAME_DROPPED = "terminal.frame.dropped"
}
