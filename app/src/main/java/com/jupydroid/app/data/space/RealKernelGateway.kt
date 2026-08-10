package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import java.util.UUID
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
) : KernelGateway {
  private val sessionSockets = mutableMapOf<String, SessionSocket>()
  private val socketsLock = Mutex()

  /**
   * Starts a kernel and binds a session to it. The session id is ours and identifies this
   * client to the server; the kernel id is the server's.
   */
  override suspend fun connect(space: Space): KernelSession =
    KernelSession(
      id = UUID.randomUUID().toString(),
      spaceId = space.id,
      kernelId = lifecycle.start(),
    )

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
          .onSubscription { sessionSocket.socket.send(JupyterMessage.executeRequest(code, session.id, requestMsgId)) }
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
    if (complete) emit(KernelEvent.Idle)
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
    val socket = opener.open(session.kernelId, session.id) { events.tryEmit(it) }
    return SessionSocket(socket, events)
  }

  private class SessionSocket(val socket: KernelSocket, val events: MutableSharedFlow<KernelSocketEvent>)

  private companion object {
    const val FRAME_BUFFER = 256
  }
}
