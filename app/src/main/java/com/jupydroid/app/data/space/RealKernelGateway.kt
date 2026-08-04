package com.jupydroid.app.data.space

import com.jupydroid.app.domain.model.Space
import com.jupydroid.app.domain.port.CompletionResult
import com.jupydroid.app.domain.port.KernelEvent
import com.jupydroid.app.domain.port.KernelGateway
import com.jupydroid.app.domain.port.KernelSession
import java.util.UUID
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/** A kernel socket ended before the execution did. */
class KernelChannelException(reason: String) : IllegalStateException(reason)

/**
 * The real [KernelGateway]: one execution, streamed off a live kernel socket.
 *
 * ```
 * RealKernelGateway(OkHttpKernelSocketOpener(connection)).execute(session, cellId, "2 + 2")
 * ```
 *
 * Step 10.3 covers [execute]. [connect] still derives a session rather than starting a
 * kernel over REST, and [interrupt] is not wired - both land in 10.4. [complete] stays
 * unimplemented until Step 5's editor calls it, rather than shipping code nothing exercises.
 */
class RealKernelGateway(
  private val opener: KernelSocketOpener,
) : KernelGateway {
  override suspend fun connect(space: Space): KernelSession =
    KernelSession(
      id = UUID.randomUUID().toString(),
      spaceId = space.id,
      kernelId = space.kernels.firstOrNull()?.id ?: DEFAULT_KERNEL,
    )

  /**
   * Frames arrive on the socket's thread and are handed to the collector through
   * [callbackFlow]'s buffer, so a slow renderer cannot block the kernel.
   *
   * Everything is filtered by our own `msg_id`: a kernel is shared, so most of what arrives
   * belongs to another cell.
   */
  override suspend fun execute(
    session: KernelSession,
    cellId: String,
    code: String,
  ): Flow<KernelEvent> = callbackFlow {
    val requestMsgId = UUID.randomUUID().toString()
    val completion = ExecutionCompletion()

    val socket = opener.open(session.kernelId, session.id) { event ->
      when (event) {
        is KernelSocketEvent.Opened -> Unit
        is KernelSocketEvent.Received -> {
          emitEventsIn(event.frame, requestMsgId, cellId)
          if (completion.accept(event.frame, requestMsgId)) {
            trySend(KernelEvent.Idle)
            close()
          }
        }
        // A socket that ends mid-execution must fail the flow rather than complete it:
        // a silently truncated cell looks like a cell that produced nothing.
        is KernelSocketEvent.Ended -> close(if (event.failed) KernelChannelException(event.reason) else null)
      }
    }

    // Sent without waiting for Opened; frames queue until the connection completes.
    socket.send(JupyterMessage.executeRequest(code, session.id, requestMsgId))

    awaitClose { socket.close() }
  }

  override suspend fun interrupt(session: KernelSession) = TODO("Step 10.4")

  override suspend fun complete(
    session: KernelSession,
    code: String,
    cursorLine: Int,
    cursorColumn: Int,
  ): CompletionResult = TODO("Step 5, when the editor asks for completions")

  override suspend fun disconnect(session: KernelSession) = Unit

  private fun kotlinx.coroutines.channels.ProducerScope<KernelEvent>.emitEventsIn(
    frame: String,
    requestMsgId: String,
    cellId: String,
  ) {
    KernelOutputs.executionCountOf(frame, requestMsgId)?.let { trySend(KernelEvent.ExecutionCount(cellId, it)) }
    KernelOutputs.outputOf(frame, requestMsgId)?.let { trySend(KernelEvent.Output(cellId, it)) }
  }

  private companion object {
    const val DEFAULT_KERNEL = "python3"
  }
}
