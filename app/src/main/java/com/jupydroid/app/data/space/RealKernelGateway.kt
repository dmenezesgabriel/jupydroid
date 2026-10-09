package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.KernelInputRequest
import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelChoice
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import com.jupydroid.app.domain.port.SessionType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.transformWhile
import java.util.UUID

/** A kernel socket ended before the execution did. */
class KernelChannelException(reason: String) : IllegalStateException(reason)

/**
 * The real [KernelGateway]: executions streamed off one long-lived socket per session.
 *
 * ```
 * RealKernelGateway(KernelSockets(OkHttpKernelSocketOpener(conn), OkHttpKernelLifecycle(conn)))
 *   .execute(session, cellId, "2 + 2")
 * ```
 *
 * [sockets] is taken rather than built here - a second client of the same session (a
 * `debug_request` on the control channel) shares the identical instance instead of opening
 * another socket, wired once at the composition root (`BaseSpaceKernelGateway`/
 * `BaseSpaceKernelDebugger` behind one `PerRunningServer<KernelSockets>`).
 */
class RealKernelGateway(
  private val sockets: KernelSockets,
  // Silent by default so the many tests that do not care about logging need not pass one; the
  // app wires the real thing in `AppContainer`. See [KernelLog] for what these lines are for.
  private val log: SpaceLifecycleLog = SpaceLifecycleLog.Silent,
) : KernelGateway {
  private val lifecycle: KernelLifecycle get() = sockets.lifecycle

  /**
   * Starts a kernel and binds a session to it. The session id is ours and identifies this
   * client to the server; the kernel id is the server's.
   */
  override suspend fun connect(
    space: Space,
    documentPath: String,
    kernel: KernelChoice?,
    sessionType: SessionType?,
  ): KernelSession {
    log.reached(KernelLog.SESSION_REQUESTED, mapOf("path" to documentPath))
    val started = lifecycle.start(documentPath, kernel, sessionType)
    return KernelSession(
      id = UUID.randomUUID().toString(),
      spaceId = space.id,
      kernelId = started.kernelId,
      kernelName = started.kernelName,
      serverSessionId = started.serverSessionId,
    ).also { log.reached(KernelLog.SESSION_OPENED, mapOf("kernelId" to it.kernelId, "sessionId" to it.id)) }
  }

  /**
   * `PATCH api/sessions/<id>` keeps the session and binds another kernel to it - the returned
   * model's `kernel.{id,name}` is the new truth. The socket was opened *for the old kernel id*,
   * so it is closed with the change: `socketFor` keys on `session.id`, and the next ask opens
   * one for the kernel now behind it.
   */
  override suspend fun changeKernel(session: KernelSession, kernel: KernelChoice): KernelSession {
    val changed = lifecycle.changeKernel(session.serverSessionId, kernel)
    sockets.close(session)
    return session.copy(kernelId = changed.kernelId, kernelName = changed.kernelName)
      .also { log.reached(KernelLog.SESSION_KERNEL_CHANGED, mapOf("kernelId" to it.kernelId, "sessionId" to it.id)) }
  }

  /**
   * Everything is filtered by our own `msg_id`: one socket now carries every cell's traffic,
   * and a kernel may be shared with another notebook besides.
   *
   * The request is sent from `onSubscription`, not before, because a [MutableSharedFlow]
   * with no replay drops what it emits while nobody is listening - sending first would race
   * the kernel's first frames against our own subscription.
   */
  override suspend fun execute(session: KernelSession, cellId: String, code: String): Flow<KernelEvent> {
    val sessionSocket = sockets.socketFor(session)

    // Request id and completion state are built per collection, not once per call: the flow
    // is cold, so sharing them would make a second collection finish instantly on the first
    // frame, having already seen its reply and idle.
    return flow {
      val requestMsgId = UUID.randomUUID().toString()
      val completion = ExecutionCompletion()
      emitAll(
        sessionSocket.events
          .onSubscription {
            sessionSocket.awaitReady(mayRestartTheKernel = true)
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

  /**
   * Sent on the socket the execution is streaming from, which is already open: the kernel is
   * blocked inside `input()` on a request it sent down that socket.
   */
  override suspend fun replyInput(session: KernelSession, request: KernelInputRequest, value: String) {
    val msgId = UUID.randomUUID().toString()
    sockets.socketFor(session).socket.send(JupyterMessage.inputReply(value, request.requestHeader, session.id, msgId))
  }

  override suspend fun interrupt(session: KernelSession) = lifecycle.interrupt(session.kernelId)

  /**
   * The socket is deliberately left open. jupyter_server replaces the process behind the same
   * kernel id, so the connection this session already holds is still the right one - which is
   * why [KernelSession] survives a restart and callers keep using it.
   */
  override suspend fun restart(session: KernelSession) = lifecycle.restart(session.kernelId)

  override suspend fun complete(session: KernelSession, code: String, cursorOffset: Int): CompletionResult =
    sockets.socketFor(session).askOnce(
      request = { msgId -> JupyterMessage.completeRequest(code, cursorOffset, session.id, msgId) },
      readReply = KernelCompletions::of,
    )

  override suspend fun executionHistory(session: KernelSession, entries: Int): List<String> =
    sockets.socketFor(session).askOnce(
      request = { msgId -> JupyterMessage.historyRequest(entries, session.id, msgId) },
      readReply = KernelHistoryEntries::of,
    )

  /** Closes the session's socket. The kernel keeps running; this only ends our connection. */
  override suspend fun disconnect(session: KernelSession) = sockets.close(session)

  /**
   * Ends the session, and the kernel with it. The socket is closed **first**: a `DELETE` that
   * lands while we are still attached leaves a socket pointed at a process that has gone.
   */
  override suspend fun shutdown(session: KernelSession) {
    disconnect(session)
    lifecycle.shutdownSession(session.serverSessionId)
  }

  /** Returns false once this execution is complete, which is what ends the flow. */
  private suspend fun FlowCollector<KernelEvent>.emitUntilComplete(
    event: JupyterSocketEvent,
    requestMsgId: String,
    cellId: String,
    completion: ExecutionCompletion,
  ): Boolean {
    // A socket that ends mid-execution must fail the cell rather than complete it: a cell
    // that silently produced nothing looks exactly like one that ran and printed nothing.
    if (event is JupyterSocketEvent.Ended) throw KernelChannelException(event.reason)
    if (event !is JupyterSocketEvent.Received) return true

    KernelOutputs.executionCountOf(event.frame, requestMsgId)?.let { emit(KernelEvent.ExecutionCount(cellId, it)) }
    KernelOutputs.outputOf(event.frame, requestMsgId)?.let { emit(KernelEvent.Output(cellId, it)) }
    KernelOutputs.inputRequestOf(event.frame, requestMsgId)?.let { emit(KernelEvent.InputRequested(cellId, it)) }
    val complete = completion.accept(event.frame, requestMsgId)
    if (complete) {
      emit(KernelEvent.Idle)
      log.reached(KernelLog.EXECUTE_COMPLETE, mapOf("cellId" to cellId, "msgId" to requestMsgId))
    }
    return !complete
  }
}
