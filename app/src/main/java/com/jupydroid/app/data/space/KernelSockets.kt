package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.KernelSession
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.time.Duration.Companion.seconds

/**
 * One socket per kernel session, shared by every client that talks to it - `execute`, `complete`
 * and `executionHistory` today, a `debug_request` on the control channel once the debugger lands.
 *
 * One socket per session rather than per execution or per client, which is how JupyterLab
 * connects: the server counts connections against a kernel, and a socket per cell - or a second
 * one for a second client of the same session - is a WebSocket handshake it does not need.
 * [close] is what ends it.
 *
 * That socket carries more than one kind of traffic, and only one of them may replace the kernel
 * behind it. [SessionSocket.awaitReady] with `mayRestartTheKernel = true` recovers a silent
 * kernel by restarting it; [SessionSocket.askOnce] is a one-shot ask that takes the first reply
 * and never restarts anything.
 */
class KernelSockets(
  private val opener: KernelSocketOpener,
  // Not private: `RealKernelGateway` calls `connect`/`interrupt`/`restart` directly on the same
  // lifecycle this uses to recover a silent socket, rather than each holding its own reference.
  val lifecycle: KernelLifecycle,
  // Silent by default so the many tests that do not care about logging need not pass one; the
  // app wires the real thing in `AppContainer`. See [KernelLog] for what these lines are for.
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
) {
  private val sessionSockets = mutableMapOf<String, SessionSocket>()
  private val socketsLock = Mutex()

  suspend fun socketFor(session: KernelSession): SessionSocket = socketsLock.withLock {
    sessionSockets.getOrPut(session.id) { openSocket(session) }
  }

  /** Removes and closes the session's socket, if one was ever opened. */
  suspend fun close(session: KernelSession) {
    socketsLock.withLock { sessionSockets.remove(session.id) }?.socket?.close()
  }

  private fun openSocket(session: KernelSession): SessionSocket {
    // Buffered so the socket's thread is never blocked by a slow collector. A kernel's shell
    // channel is serial - one execution in flight at a time - so this is generous, and with
    // no replay the flow discards frames arriving between executions rather than growing.
    val events = MutableSharedFlow<JupyterSocketEvent>(extraBufferCapacity = FRAME_BUFFER)
    val connected = CompletableDeferred<Unit>()
    log.reached(KernelLog.SOCKET_OPENED, mapOf("kernelId" to session.kernelId, "sessionId" to session.id))
    val socket = opener.open(session.kernelId, session.id) { event ->
      // Not via the SessionSocket being constructed: an opener may report `Opened` synchronously
      // from inside `open()`, before there is anything to hold it in - which is exactly what the
      // scripted opener does, and a fake that mimics reality is the point of having one.
      settle(connected, event)
      report(log, event, subscribers = events.subscriptionCount.value, accepted = events.tryEmit(event))
    }
    return SessionSocket(
      socket,
      events,
      session.id,
      connected,
      restartKernel = { lifecycle.restart(session.kernelId) },
      log,
    )
  }

  private companion object {
    const val FRAME_BUFFER = 256
  }
}

/**
 * One session's socket, and whether it is connected yet.
 *
 * [connected] is the fix for the execution stall, four sightings and one diagnosis
 * (`evidence/kernel-observability/stall-diagnosed.md`). `onSubscription` runs the instant a
 * collector attaches, which is long before OkHttp has finished the handshake; the frame was
 * queued and flushed on open, reaching `jupyter_server` before its own `open()` had associated
 * the session, and dropped. The kernel then sent its startup statuses and nothing else.
 */
class SessionSocket(
  val socket: JupyterSocket,
  val events: MutableSharedFlow<JupyterSocketEvent>,
  private val sessionId: String,
  private val connected: CompletableDeferred<Unit>,
  /** Replaces the process behind this socket; see [recoverOnce] for when that is the answer. */
  private val restartKernel: suspend () -> Unit,
  private val log: SpaceLifecycleLog,
) {
  private val handshake = Mutex()
  private var greeted = false

  /**
   * Waits until this socket can actually be asked to run something.
   *
   * Two parts, both taken from JupyterLab's `_updateConnectionStatus`: the handshake must have
   * completed, **and** the kernel must have answered a `kernel_info_request`. The second is the
   * one that matters - `jupyter_server` finishes the websocket handshake before it has
   * subscribed the session to the kernel's ZMQ channels, so a request sent in between is
   * dropped without a word. That is the execution stall, five sightings
   * (`evidence/kernel-observability/`).
   *
   * Once per socket, not once per cell, so every later cell returns here immediately.
   *
   * [mayRestartTheKernel] is what separates the two callers, and it has no default so that
   * neither gets recovery by accident. A cell is worth replacing a silent kernel for; a
   * completion popup is not - it has an answer for "the kernel cannot say", which is nothing,
   * and restarting would discard the variables of a kernel the user had merely opened onto.
   */
  suspend fun awaitReady(mayRestartTheKernel: Boolean) {
    connected.await()
    handshake.withLock {
      if (greeted) return
      greeted = greetOrRecover(mayRestartTheKernel)
    }
  }

  /**
   * Sends one request and returns the first reply that answers it.
   *
   * The two questions this app asks the shell channel besides running a cell - what completes the
   * word under the cursor, and what has been run before - are the same round trip: mint a
   * `msg_id`, wait for the socket, send, and take the first frame [readReply] recognises as an
   * answer to it. [readReply] is total and returns null for everything else, because a shared
   * kernel's channel carries another notebook's traffic and this one's own chatter.
   *
   * **There is no timeout, and that is deliberate:** the shell channel is serial, so a request
   * sent while a cell runs waits behind it, and the answer to a stale request is to cancel this
   * coroutine - which the caller does by typing again - not to invent a deadline for the kernel.
   *
   * **And it never restarts the kernel**, which is why [awaitReady]'s flag is settled here rather
   * than passed in: this is the read-only half of the socket. A cell is worth replacing a silent
   * kernel for; asking what a word completes to, or what was run an hour ago, is not - both have
   * an answer for "the kernel cannot say", and restarting would discard the variables of a kernel
   * the user had merely opened onto.
   */
  suspend fun <T> askOnce(request: (msgId: String) -> String, readReply: (frame: String, msgId: String) -> T?): T {
    val requestMsgId = UUID.randomUUID().toString()
    return events
      .onSubscription {
        awaitReady(mayRestartTheKernel = false)
        socket.send(request(requestMsgId))
      }
      .mapNotNull { event ->
        // A socket that ends before the reply fails the ask rather than answering nothing, for
        // the reason a dying execution fails its cell: silence and "there is no answer" are
        // different claims and only one of them is true.
        if (event is JupyterSocketEvent.Ended) throw KernelChannelException(event.reason)
        (event as? JupyterSocketEvent.Received)?.let { readReply(it.frame, requestMsgId) }
      }
      .first()
  }

  /**
   * Whether this socket can now be asked to run something.
   *
   * False only for the caller that may not restart: the greeting went unanswered, and a
   * completion has an answer for that. [recoverOnce] either makes the socket usable or throws,
   * so there is no third outcome to report.
   */
  private suspend fun greetOrRecover(mayRestartTheKernel: Boolean): Boolean = when {
    greetKernel() -> true
    !mayRestartTheKernel -> false
    else -> {
      recoverOnce()
      true
    }
  }

  /**
   * Replaces the kernel and greets the new one, once.
   *
   * An unanswered greeting means the shell channel is not carrying replies, and no amount of
   * asking makes it - the seventh sighting put the greeting on `jupyter_server`'s own 0.5 s
   * nudge cadence for 60 s and got 120 asks and no reply. Every sighting did clear on the next
   * launch with a fresh kernel, so replacing the process is the act that has ever worked.
   *
   * **The socket is not reopened with it.** JupyterLab's `restart()` follows the REST call with
   * `reconnect()` "to address cases where kernel ports have changed during the restart"
   * (`packages/services/src/kernel/default.ts:547-563`), but `jupyter_server` restarts with
   * `newports=False` and its `on_kernel_restarted` only publishes a status - so the endpoints
   * are the same ones this socket is already attached to.
   *
   * **What it costs, stated rather than discovered.** A reopened notebook reattaches to the
   * kernel it already had, so a restart here discards that kernel's variables. They were
   * already unreachable - the channel that would run a cell is the one that answered nothing -
   * and the alternative is a cell that waits for ever.
   *
   * Bounded at one. A loop of restarts is the same defect wearing a different hat.
   */
  private suspend fun recoverOnce() {
    log.reached(KernelLog.GREET_RESTARTING, mapOf("sessionId" to sessionId))
    restartKernel()
    if (greetKernel()) return
    throw KernelChannelException(
      "kernel for session '$sessionId' answered no kernel_info_request within " +
        "$KERNEL_INFO_TIMEOUT, before or after a restart; its shell channel is not delivering",
    )
  }

  /**
   * Asks once and reports whether it was answered, giving up after [KERNEL_INFO_TIMEOUT].
   *
   * The failsafe is the reference's too, and their standing note says why: "if sent while zmq
   * subscriptions are not established, kernelInfo may not resolve, so use a timeout to ensure we
   * don't hang forever". Without it this would trade a stall that recovers on retry for one that
   * never does.
   *
   * **Asking the same kernel again does not work**, which is why the answer to `false` is
   * [recoverOnce] and not another call to this. The same note muses that "it may be preferable
   * to retry kernelInfo rather than give up after one timeout"
   * (`packages/services/src/kernel/default.ts:1589-1591`), so the greeting was put on
   * `jupyter_server`'s own 0.5 s nudge cadence for up to the server's 60 s and measured on the
   * tablet: **2 stalls in 80 runs**, both trails showing 120 asks and not one `kernel_info_reply`
   * (`evidence/kernel-observability/seventh-sighting/`). What arrives during a stall is iopub
   * traffic parented to the *server's* nudges while the shell channel delivers nothing at all, so
   * asking that channel again is asking a channel that is not carrying replies. The 60 s only
   * bought a cell a minute in Queued ahead of a request it lost anyway.
   */
  private suspend fun greetKernel(): Boolean {
    val msgId = UUID.randomUUID().toString()
    log.reached(KernelLog.GREET_SENT, mapOf("sessionId" to sessionId))
    val answered = withTimeoutOrNull(KERNEL_INFO_TIMEOUT) {
      events
        .onSubscription { socket.send(JupyterMessage.kernelInfoRequest(sessionId, msgId)) }
        .first {
          it is JupyterSocketEvent.Received && JupyterFrame.ofType(it.frame, msgId, KERNEL_INFO_REPLY) != null
        }
    }
    log.reached(
      if (answered != null) KernelLog.GREET_ANSWERED else KernelLog.GREET_TIMED_OUT,
      mapOf("sessionId" to sessionId),
    )
    return answered != null
  }

  private companion object {
    /** JupyterLab's `DEFAULT_KERNEL_INFO_TIMEOUT`, which is 3000ms. */
    val KERNEL_INFO_TIMEOUT = 3.seconds
    const val KERNEL_INFO_REPLY = "kernel_info_reply"
  }
}

/**
 * Says what came off the socket *before* anything downstream can swallow it.
 *
 * `subscribers` is the field that matters. `MutableSharedFlow` with `replay = 0` drops what it
 * emits to nobody and says nothing about it, so "the kernel never replied" and "the kernel
 * replied between executions and it was discarded" are indistinguishable after the fact - and
 * they are opposite defects. `accepted` catches the other loss, a full buffer.
 *
 * Beside [settle] rather than inside the gateway for the same reason that one is: it reads an
 * event and a log and touches nothing the class holds.
 */
private fun report(log: SpaceLifecycleLog, event: JupyterSocketEvent, subscribers: Int, accepted: Boolean) {
  val fields = mapOf("subscribers" to subscribers.toString())
  when (event) {
    is JupyterSocketEvent.Opened -> log.reached(KernelLog.SOCKET_CONNECTED, fields)
    is JupyterSocketEvent.Ended -> {
      val why = mapOf("reason" to event.reason, "failed" to event.failed.toString())
      log.reached(KernelLog.SOCKET_ENDED, fields + why)
    }
    is JupyterSocketEvent.Received -> {
      val what = mapOf(
        "msgType" to (JupyterFrame.msgTypeOf(event.frame) ?: "?"),
        // Empty means the frame answers no request of ours - the kernel's own startup chatter.
        "parent" to (JupyterFrame.parentMsgIdOf(event.frame) ?: ""),
      )
      log.reached(KernelLog.FRAME_RECEIVED, fields + what + mapOf("bytes" to event.frame.length.toString()))
    }
  }
  if (!accepted) log.reached(KernelLog.FRAME_DROPPED, fields)
}

/**
 * Releases whoever is waiting to send, or fails them when the socket dies first.
 *
 * Completing exceptionally matters as much as completing: waiting for a handshake that never lands
 * would turn a lost frame into a hang, which is worse than the defect being fixed.
 */
private fun settle(connected: CompletableDeferred<Unit>, event: JupyterSocketEvent) {
  when (event) {
    is JupyterSocketEvent.Opened -> connected.complete(Unit)
    is JupyterSocketEvent.Ended -> connected.completeExceptionally(KernelChannelException(event.reason))
    is JupyterSocketEvent.Received -> Unit
  }
}
