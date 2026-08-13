package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transformWhile
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration.Companion.seconds

/** A kernel socket ended before the execution did. */
class KernelChannelException(reason: String) : IllegalStateException(reason)

/**
 * The real [KernelGateway]: executions streamed off one long-lived socket per session.
 *
 * ```
 * RealKernelGateway(OkHttpKernelSocketOpener(conn), OkHttpKernelLifecycle(conn))
 *   .execute(session, cellId, "2 + 2")
 * ```
 *
 * One socket per session rather than per execution, which is how JupyterLab connects: the
 * server counts connections against a kernel, and a socket per cell means a WebSocket
 * handshake on every run. [disconnect] is what closes it.
 *
 * [complete] stays unimplemented until Step 5's editor calls it, rather than shipping code
 * nothing exercises.
 */
class RealKernelGateway(
  private val opener: KernelSocketOpener,
  private val lifecycle: KernelLifecycle,
  // Silent by default so the many tests that do not care about logging need not pass one; the
  // app wires the real thing in `AppContainer`. See [KernelLog] for what these lines are for.
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
) : KernelGateway {
  private val sessionSockets = mutableMapOf<String, SessionSocket>()
  private val socketsLock = Mutex()

  /**
   * Starts a kernel and binds a session to it. The session id is ours and identifies this
   * client to the server; the kernel id is the server's.
   */
  override suspend fun connect(space: Space, documentPath: String): KernelSession =
    KernelSession(
      id = UUID.randomUUID().toString(),
      spaceId = space.id,
      kernelId = lifecycle.start(documentPath),
    ).also { log.reached(KernelLog.SESSION_OPENED, mapOf("kernelId" to it.kernelId, "sessionId" to it.id)) }

  /**
   * Everything is filtered by our own `msg_id`: one socket now carries every cell's traffic,
   * and a kernel may be shared with another notebook besides.
   *
   * The request is sent from `onSubscription`, not before, because a [MutableSharedFlow]
   * with no replay drops what it emits while nobody is listening - sending first would race
   * the kernel's first frames against our own subscription.
   */
  override suspend fun execute(
    session: KernelSession,
    cellId: String,
    code: String,
  ): Flow<KernelEvent> {
    val sessionSocket = socketFor(session)

    // Request id and completion state are built per collection, not once per call: the flow
    // is cold, so sharing them would make a second collection finish instantly on the first
    // frame, having already seen its reply and idle.
    return flow {
      val requestMsgId = UUID.randomUUID().toString()
      val completion = ExecutionCompletion()
      emitAll(
        sessionSocket.events
          .onSubscription {
            sessionSocket.awaitReady()
            // `enqueued`, not `sent`: OkHttp returns true when the frame is *queued*, so a true
            // here says the app let go of the request and nothing about the kernel having heard
            // it. That is why the await above exists rather than trusting this.
            val enqueued = sessionSocket.socket.send(JupyterMessage.executeRequest(code, session.id, requestMsgId))
            log.reached(
              KernelLog.EXECUTE_REQUEST,
              mapOf("cellId" to cellId, "msgId" to requestMsgId, "enqueued" to enqueued.toString()),
            )
          }
          .transformWhile { event -> emitUntilComplete(event, requestMsgId, cellId, completion) },
      )
    }
  }

  override suspend fun interrupt(session: KernelSession) = lifecycle.interrupt(session.kernelId)

  /**
   * The socket is deliberately left open. jupyter_server replaces the process behind the same
   * kernel id, so the connection this session already holds is still the right one - which is
   * why [KernelSession] survives a restart and callers keep using it.
   */
  override suspend fun restart(session: KernelSession) = lifecycle.restart(session.kernelId)

  /**
   * Sends one `complete_request` and returns the first reply that answers it.
   *
   * There is no timeout, and that is deliberate: the shell channel is serial, so a request sent
   * while a cell runs waits behind it, and the answer to a stale request is to cancel this
   * coroutine - which the caller does by typing again - not to invent a deadline for the kernel.
   */
  override suspend fun complete(
    session: KernelSession,
    code: String,
    cursorOffset: Int,
  ): CompletionResult {
    val sessionSocket = socketFor(session)
    val requestMsgId = UUID.randomUUID().toString()
    return sessionSocket.events
      .onSubscription {
        sessionSocket.awaitReady()
        sessionSocket.socket.send(JupyterMessage.completeRequest(code, cursorOffset, session.id, requestMsgId))
      }
      .mapNotNull { event -> completionOrFail(event, requestMsgId) }
      .first()
  }

  /** Closes the session's socket. The kernel keeps running; this only ends our connection. */
  override suspend fun disconnect(session: KernelSession) {
    socketsLock.withLock { sessionSockets.remove(session.id) }?.socket?.close()
  }

  /** Returns false once this execution is complete, which is what ends the flow. */
  private suspend fun FlowCollector<KernelEvent>.emitUntilComplete(
    event: KernelSocketEvent,
    requestMsgId: String,
    cellId: String,
    completion: ExecutionCompletion,
  ): Boolean {
    // A socket that ends mid-execution must fail the cell rather than complete it: a cell
    // that silently produced nothing looks exactly like one that ran and printed nothing.
    if (event is KernelSocketEvent.Ended) throw KernelChannelException(event.reason)
    if (event !is KernelSocketEvent.Received) return true

    KernelOutputs.executionCountOf(event.frame, requestMsgId)?.let { emit(KernelEvent.ExecutionCount(cellId, it)) }
    KernelOutputs.outputOf(event.frame, requestMsgId)?.let { emit(KernelEvent.Output(cellId, it)) }
    val complete = completion.accept(event.frame, requestMsgId)
    if (complete) {
      emit(KernelEvent.Idle)
      log.reached(KernelLog.EXECUTE_COMPLETE, mapOf("cellId" to cellId, "msgId" to requestMsgId))
    }
    return !complete
  }

  /** Null until the reply arrives; a socket that ends first fails rather than answering nothing. */
  private fun completionOrFail(event: KernelSocketEvent, requestMsgId: String): CompletionResult? {
    if (event is KernelSocketEvent.Ended) throw KernelChannelException(event.reason)
    if (event !is KernelSocketEvent.Received) return null
    return KernelCompletions.of(event.frame, requestMsgId)
  }

  private suspend fun socketFor(session: KernelSession): SessionSocket = socketsLock.withLock {
    sessionSockets.getOrPut(session.id) { openSocket(session) }
  }

  private fun openSocket(session: KernelSession): SessionSocket {
    // Buffered so the socket's thread is never blocked by a slow collector. A kernel's shell
    // channel is serial - one execution in flight at a time - so this is generous, and with
    // no replay the flow discards frames arriving between executions rather than growing.
    val events = MutableSharedFlow<KernelSocketEvent>(extraBufferCapacity = FRAME_BUFFER)
    val connected = CompletableDeferred<Unit>()
    log.reached(KernelLog.SOCKET_OPENED, mapOf("kernelId" to session.kernelId, "sessionId" to session.id))
    val socket = opener.open(session.kernelId, session.id) { event ->
      // Not via the SessionSocket being constructed: an opener may report `Opened` synchronously
      // from inside `open()`, before there is anything to hold it in - which is exactly what the
      // scripted opener does, and a fake that mimics reality is the point of having one.
      settle(connected, event)
      report(event, subscribers = events.subscriptionCount.value, accepted = events.tryEmit(event))
    }
    return SessionSocket(socket, events, session.id, connected)
  }

  /**
   * Says what came off the socket *before* anything downstream can swallow it.
   *
   * `subscribers` is the field that matters. `MutableSharedFlow` with `replay = 0` drops what it
   * emits to nobody and says nothing about it, so "the kernel never replied" and "the kernel
   * replied between executions and it was discarded" are indistinguishable after the fact - and
   * they are opposite defects. `accepted` catches the other loss, a full buffer.
   */
  private fun report(event: KernelSocketEvent, subscribers: Int, accepted: Boolean) {
    val fields = mapOf("subscribers" to subscribers.toString())
    when (event) {
      is KernelSocketEvent.Opened -> log.reached(KernelLog.SOCKET_CONNECTED, fields)
      is KernelSocketEvent.Ended -> {
        val why = mapOf("reason" to event.reason, "failed" to event.failed.toString())
        log.reached(KernelLog.SOCKET_ENDED, fields + why)
      }
      is KernelSocketEvent.Received -> {
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
   * One session's socket, and whether it is connected yet.
   *
   * [connected] is the fix for the execution stall, four sightings and one diagnosis
   * (`evidence/kernel-observability/stall-diagnosed.md`). `onSubscription` runs the instant a
   * collector attaches, which is long before OkHttp has finished the handshake; the frame was
   * queued and flushed on open, reaching `jupyter_server` before its own `open()` had associated
   * the session, and dropped. The kernel then sent its startup statuses and nothing else.
   */
  private class SessionSocket(
    val socket: KernelSocket,
    val events: MutableSharedFlow<KernelSocketEvent>,
    private val sessionId: String,
    private val connected: CompletableDeferred<Unit>,
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
     */
    suspend fun awaitReady() {
      connected.await()
      handshake.withLock {
        if (greeted) return
        greetKernel()
        greeted = true
      }
    }

    /**
     * Asks, and gives up after [KERNEL_INFO_TIMEOUT] rather than waiting for ever.
     *
     * The failsafe is the reference's too, and their standing note says why: "if sent while zmq
     * subscriptions are not established, kernelInfo may not resolve, so use a timeout to ensure we
     * don't hang forever". Without it this would trade a stall that recovers on retry for one that
     * never does.
     */
    private suspend fun greetKernel() {
      val msgId = UUID.randomUUID().toString()
      withTimeoutOrNull(KERNEL_INFO_TIMEOUT) {
        events
          .onSubscription { socket.send(JupyterMessage.kernelInfoRequest(sessionId, msgId)) }
          .first { it is KernelSocketEvent.Received && JupyterFrame.ofType(it.frame, msgId, KERNEL_INFO_REPLY) != null }
      }
    }
  }

  private companion object {
    const val FRAME_BUFFER = 256

    /** JupyterLab's `DEFAULT_KERNEL_INFO_TIMEOUT`, which is 3000ms. */
    val KERNEL_INFO_TIMEOUT = 3.seconds
    const val KERNEL_INFO_REPLY = "kernel_info_reply"
  }
}

/**
 * Releases whoever is waiting to send, or fails them when the socket dies first.
 *
 * Completing exceptionally matters as much as completing: waiting for a handshake that never lands
 * would turn a lost frame into a hang, which is worse than the defect being fixed.
 */
private fun settle(connected: CompletableDeferred<Unit>, event: KernelSocketEvent) {
  when (event) {
    is KernelSocketEvent.Opened -> connected.complete(Unit)
    is KernelSocketEvent.Ended -> connected.completeExceptionally(KernelChannelException(event.reason))
    is KernelSocketEvent.Received -> Unit
  }
}
