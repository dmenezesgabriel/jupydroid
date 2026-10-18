package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.SpaceServerEndpoint
import com.jupydroid.extensions.ai.data.logcat.ChatSocketLog
import com.jupydroid.extensions.ai.data.logcat.chatFailureFields
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatDroppedException
import com.jupydroid.extensions.ai.domain.AgentChatException
import com.jupydroid.extensions.ai.domain.ChatTranscript
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.UUID
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * jupyterlab-chat's socket in the entered Space's `jupyter_server`, asking jupyter-ai's opencode persona.
 *
 * **The JSON socket, not Yjs**: jupyterlab-chat registers `/api/chat/ws/<path>` only when no RTC
 * provider is enabled, and the image enables none (jupyterlab_chat 0.25.2 `__init__.py`, `rtc_lib.py`;
 * ADR-0106).
 *
 * ```
 * OkHttpAgentChat(host.server!!).transcript().collect { shown = it }
 * ```
 *
 * @param heartbeatInterval how often an open chat touches `api/me`, so its server does not stop itself
 *   while the chat is open ([ApiActivityHeartbeat]). Half of the shortest idle setting, 1 minute: the
 *   server checks once a minute, so whatever the check's phase, the last activity it sees is never
 *   older than this.
 * @param chatPath the `.chat` file, relative to the workspace root. `untitled.chat` is what
 *   jupyterlab-chat names the first chat in its `defaultDirectory`, which is the root by default
 *   (`schemas/jupyterlab-chat-extension/factory.json`); the server writes it on the first message.
 */
class OkHttpAgentChat(
  private val server: SpaceServerEndpoint,
  private val client: OkHttpClient = OkHttpClient(),
  private val chatPath: String = "untitled.chat",
  private val persona: String = OPENCODE_PERSONA_ID,
  private val log: ChatSocketLog = ChatSocketLog.Silent,
  private val heartbeatInterval: Duration = HEARTBEAT_INTERVAL,
) : AgentChat {
  private val heartbeat = ApiActivityHeartbeat(client, heartbeatInterval, log, chatPath)

  /** The socket whose `connection` frame has arrived; null while no chat is open. */
  @Volatile private var open: WebSocket? = null

  // Each emission is the whole transcript, so a slow collector loses nothing by seeing only the latest.
  override fun transcript(): Flow<ChatTranscript> = callbackFlow {
    val address = server.address()
    val request = Request.Builder()
      .url(chatUrl(address.baseUrl))
      .header("Authorization", "token ${address.token}")
      .build()
    val socket = client.newWebSocket(request, TranscriptListener(this))
    // Scoped to this flow, so the server may stop itself again once the panel stops collecting.
    launch { heartbeat.beat(address) }
    awaitClose {
      open = null
      socket.close(NORMAL_CLOSURE, null)
    }
  }.conflate()

  override suspend fun ask(question: String) {
    val socket = open ?: throw AgentChatException("the chat is not open", question)
    val frame = ChatFrames.ask(id = UUID.randomUUID().toString(), question = question, persona = persona)
    if (!socket.send(frame)) throw AgentChatException("the chat's socket is closing", question)
  }

  private fun chatUrl(baseUrl: String): HttpUrl = baseUrl.toHttpUrl().newBuilder()
    .addPathSegments("api/chat/ws")
    .addPathSegment(chatPath)
    .build()

  /** OkHttp calls one listener's methods one at a time, from the socket's reader thread. */
  private inner class TranscriptListener(private val out: ProducerScope<ChatTranscript>) : WebSocketListener() {
    private var humans = emptySet<String>()
    private var shown = ChatTranscript()

    override fun onMessage(webSocket: WebSocket, text: String) {
      val frame = runCatching { ChatFrames.parse(text) }.getOrElse {
        out.close(it)
        return
      }
      when (frame) {
        is ServerFrame.Connection -> opened(webSocket, frame)
        is ServerFrame.Message -> shown = shown.with(frame.message.toChatMessage(humans))
        ServerFrame.Other -> return
      }
      out.trySend(shown)
    }

    /**
     * A server that exits drops the socket with no close frame, and OkHttp's `EOFException` for that
     * has a null message, so the class is what names it. Ending with no close frame is a drop, which
     * the chat opens again for (`readings`).
     */
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      val fields = chatFailureFields(chatPath, error = t, http = response?.code)
      log.reached("chat.socket.failed", fields)
      out.close(AgentChatDroppedException("the chat's socket failed: ${failureText(fields)}"))
    }

    /** The server closing first is a refusal: `1008` for a path outside the root (`websocket_handler.py`). */
    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      log.reached("chat.socket.closed", mapOf("code" to code.toString(), "reason" to reason, "chat" to chatPath))
      out.close(AgentChatException("the chat's socket was closed: $code $reason"))
    }

    private fun opened(webSocket: WebSocket, connection: ServerFrame.Connection) {
      humans = connection.humans
      shown = ChatTranscript(connection.messages.map { it.toChatMessage(humans) })
      open = webSocket
    }
  }

  companion object {
    val HEARTBEAT_INTERVAL: Duration = 30.seconds

    private const val NORMAL_CLOSURE = 1000

    /**
     * `EOFException`, or `ProtocolException: Expected HTTP 101 (http 403)`: the class, then its message
     * and the response's code when there are any, the way the kernel's socket reports an ending
     * (`app/src/main/java/com/jupydroid/app/data/space/JupyterSocket.kt`).
     */
    private fun failureText(fields: Map<String, String>): String = fields.getValue("error") +
      fields["message"]?.let { ": $it" }.orEmpty() +
      fields["http"]?.let { " (http $it)" }.orEmpty()
  }
}
