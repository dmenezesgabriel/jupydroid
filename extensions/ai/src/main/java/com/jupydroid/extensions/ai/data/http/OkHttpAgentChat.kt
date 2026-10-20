package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.SpaceServerEndpoint
import com.jupydroid.extensions.ai.data.logcat.ChatSocketLog
import com.jupydroid.extensions.ai.data.logcat.chatFailureFields
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatDroppedException
import com.jupydroid.extensions.ai.domain.AgentChatException
import com.jupydroid.extensions.ai.domain.ChatTranscript
import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import com.jupydroid.extensions.ai.domain.ToolCallChoiceException
import com.jupydroid.extensions.ai.domain.ToolCallPermission
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
 * @param ioDispatcher where a blocking request to the server runs, as [choose]'s and [stop]'s do.
 */
class OkHttpAgentChat(
  private val server: SpaceServerEndpoint,
  private val client: OkHttpClient = OkHttpClient(),
  private val chatPath: String = "untitled.chat",
  private val persona: String = OPENCODE_PERSONA_ID,
  private val log: ChatSocketLog = ChatSocketLog.Silent,
  private val heartbeatInterval: Duration = HEARTBEAT_INTERVAL,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : AgentChat {
  private val heartbeat = ApiActivityHeartbeat(client, heartbeatInterval, log, chatPath)

  /** The chat whose `connection` frame has arrived; null while none is open. */
  @Volatile private var open: OpenChat? = null

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
    val socket = open?.socket ?: throw AgentChatException("the chat is not open", question)
    val frame = ChatFrames.ask(id = UUID.randomUUID().toString(), question = question, persona = persona)
    if (!socket.send(frame)) throw AgentChatException("the chat's socket is closing", question)
  }

  /**
   * `POST ai/acp/permissions` with the call's session, its id and the option's, as jupyter-ai's frontend
   * sends them (jupyter-ai-acp-client@77016f6 `src/request.ts`, `submitPermissionDecision`).
   * `PermissionHandler` answers 404 once nothing waits on the call (jupyter_ai_acp_client 0.3.3 `routes.py`).
   */
  override suspend fun choose(call: ToolCall, option: PermissionOption) {
    val pending = call.permission as? ToolCallPermission.Pending
      ?: throw ToolCallChoiceException(call, option, "nothing waits on it")
    val decision = buildJsonObject {
      put("session_id", pending.sessionId)
      put("tool_call_id", call.id)
      put("option_id", option.id)
    }.toString()
    val (code, body) = client.callWithToken(server, ioDispatcher) { base ->
      url(base.newBuilder().addPathSegments("ai/acp/permissions").build()).post(decision.toRequestBody(JSON_MEDIA_TYPE))
    }
    if (code != HTTP_OK) throw ToolCallChoiceException(call, option, "http $code, ${body.take(BODY_EXCERPT)}")
  }

  /**
   * `POST api/ai/personas/cancel?chat_id=<id>`, the id the `connection` frame carried: `CancelHandler`
   * finds the chat's personas by it and stops those that are processing, answering 404 while the chat
   * has no persona manager yet (jupyter_ai_persona_manager `handlers.py`, in the shipped rootfs). It
   * reads no body.
   */
  override suspend fun stop() {
    val chatId = open?.chatId ?: throw AgentChatException("the chat is not open")
    val (code, body) = client.callWithToken(server, ioDispatcher) { base ->
      val cancel = base.newBuilder().addPathSegments("api/ai/personas/cancel").addQueryParameter("chat_id", chatId)
      url(cancel.build()).post(ByteArray(0).toRequestBody())
    }
    if (code != HTTP_OK) throw AgentChatException("stopping the reply failed: http $code, ${body.take(BODY_EXCERPT)}")
  }

  private fun chatUrl(baseUrl: String): HttpUrl = baseUrl.toHttpUrl().newBuilder()
    .addPathSegments("api/chat/ws")
    .addPathSegment(chatPath)
    .build()

  /** OkHttp calls one listener's methods one at a time, from the socket's reader thread. */
  private inner class TranscriptListener(private val out: ProducerScope<ChatTranscript>) : WebSocketListener() {
    private var humans = emptySet<String>()
    private var shown = ChatTranscript()

    /** The bots writing a reply now, by username. */
    private val writers = mutableSetOf<String>()

    override fun onMessage(webSocket: WebSocket, text: String) {
      val frame = runCatching { ChatFrames.parse(text) }.getOrElse {
        out.close(it)
        return
      }
      when (frame) {
        is ServerFrame.Connection -> opened(webSocket, frame)
        is ServerFrame.Message -> shown = shown.with(frame.message.toChatMessage(humans))
        is ServerFrame.Writing -> writing(frame)
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
      open = OpenChat(webSocket, connection.chatId)
    }

    private fun writing(frame: ServerFrame.Writing) {
      if (frame.writing) writers += frame.agent else writers -= frame.agent
      shown = shown.copy(agentWriting = writers.isNotEmpty())
    }
  }

  /** An open chat's socket, and the id the server knows the chat by. */
  private class OpenChat(val socket: WebSocket, val chatId: String)

  companion object {
    val HEARTBEAT_INTERVAL: Duration = 30.seconds

    private const val NORMAL_CLOSURE = 1000
    private const val HTTP_OK = 200

    /** Enough of an unexpected answer to recognise it by. */
    private const val BODY_EXCERPT = 200

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
