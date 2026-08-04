package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.ServerConnection
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** How an execution ended. Anything other than [Result] carries why, never a bare null. */
sealed interface ExecutionOutcome {
  data class Result(val text: String) : ExecutionOutcome

  data class KernelError(val message: String) : ExecutionOutcome

  data class ChannelFailure(val message: String) : ExecutionOutcome

  data class TimedOut(val after: Duration) : ExecutionOutcome
}

/**
 * One execution over a kernel's WebSocket, for the Step 8.6 gate.
 *
 * The real [com.jupydroid.app.domain.port.KernelGateway] in Step 10 keeps the socket
 * open and streams every output; this deliberately does the smallest honest thing -
 * connect, run one statement, report what came back, close.
 *
 * No subprotocol is requested, so jupyter_server uses its original JSON protocol.
 */
class KernelChannel(
  private val connection: ServerConnection,
  private val client: OkHttpClient = OkHttpClient(),
  private val timeout: Duration = EXECUTE_TIMEOUT,
) {
  suspend fun execute(kernelId: String, code: String): ExecutionOutcome {
    val requestMsgId = UUID.randomUUID().toString()
    val sessionId = UUID.randomUUID().toString()
    return withTimeoutOrNull(timeout) {
      awaitOutcome(kernelId, code, requestMsgId, sessionId)
    } ?: ExecutionOutcome.TimedOut(timeout)
  }

  private suspend fun awaitOutcome(
    kernelId: String,
    code: String,
    requestMsgId: String,
    sessionId: String,
  ): ExecutionOutcome = suspendCancellableCoroutine { continuation ->
    val socket = client.newWebSocket(channelRequest(kernelId, sessionId), object : WebSocketListener() {
      override fun onOpen(webSocket: WebSocket, response: Response) {
        webSocket.send(JupyterMessage.executeRequest(code, sessionId, requestMsgId))
      }

      override fun onMessage(webSocket: WebSocket, text: String) {
        val outcome = JupyterMessage.resultOf(text, requestMsgId)?.let(ExecutionOutcome::Result)
          ?: JupyterMessage.errorOf(text, requestMsgId)?.let(ExecutionOutcome::KernelError)
        if (outcome != null && continuation.isActive) continuation.resume(outcome)
      }

      // A closed or failed socket must end the wait: silently hanging until the
      // timeout would hide the real reason behind a misleading "timed out".
      override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        if (continuation.isActive) {
          continuation.resume(
            ExecutionOutcome.ChannelFailure("${t::class.simpleName}: ${t.message} (http ${response?.code})"),
          )
        }
      }

      override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        if (continuation.isActive) {
          continuation.resume(ExecutionOutcome.ChannelFailure("closed early: $code $reason"))
        }
      }
    })
    continuation.invokeOnCancellation { socket.cancel() }
  }

  private fun channelRequest(kernelId: String, sessionId: String): Request =
    Request.Builder()
      .url("${connection.baseUrl}api/kernels/$kernelId/channels?session_id=$sessionId")
      .header("Authorization", "token ${connection.token}")
      .build()

  private companion object {
    val EXECUTE_TIMEOUT = 30.seconds
  }
}
