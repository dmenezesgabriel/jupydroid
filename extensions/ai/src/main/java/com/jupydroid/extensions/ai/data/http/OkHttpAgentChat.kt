package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.NotebookCells
import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extension.api.SpaceServerEndpoint
import com.jupydroid.extensions.ai.data.logcat.ChatSocketLog
import com.jupydroid.extensions.ai.data.logcat.chatFailureFields
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatDroppedException
import com.jupydroid.extensions.ai.domain.AgentChatException
import com.jupydroid.extensions.ai.domain.AgentSelection
import com.jupydroid.extensions.ai.domain.ChatTranscript
import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import com.jupydroid.extensions.ai.domain.ToolCallChoiceException
import com.jupydroid.extensions.ai.domain.ToolCallPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
 * jupyterlab-chat's socket in the entered Space's `jupyter_server`, asking jupyter-ai's personas.
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
  private val log: ChatSocketLog = ChatSocketLog.Silent,
  private val heartbeatInterval: Duration = HEARTBEAT_INTERVAL,
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
  private val notebookCells: NotebookCells? = null,
  private val labCommands: LabCommandWebClient = LabCommandWebClient(),
  private val uiDispatcher: CoroutineDispatcher = Dispatchers.Main,
) : AgentChat {
  private val heartbeat = ApiActivityHeartbeat(client, heartbeatInterval, log, chatPath)
  private val events = PersonaEventsSubscription(client, log, chatPath)

  /** The chat whose `connection` frame has arrived; null while none is open. */
  @Volatile private var open: OpenChat? = null

  // Each emission is the whole transcript, so a slow collector loses nothing by seeing only the latest.
  override fun transcript(): Flow<ChatTranscript> = callbackFlow { openChat() }.conflate()

  /**
   * Opens the chat's socket and persona-event bus, then holds them open until the collector goes
   * away. `address()` starts the Space's server when none runs, and what it throws is the
   * launcher's to choose (`SpaceServer.startSpace`): [AgentChat.transcript] promises
   * [AgentChatException] when the chat cannot be opened, and `openOnce` catches nothing else.
   */
  @Suppress("TooGenericExceptionCaught")
  private suspend fun ProducerScope<ChatTranscript>.openChat() {
    val opened = try {
      opening()
    } catch (failure: Exception) {
      throw when (failure) {
        is CancellationException -> failure
        else -> AgentChatException(
          "the chat could not be opened: ${failure::class.java.simpleName}: ${failure.message}",
        )
      }
    }
    // Scoped to this flow, so the server may stop itself again once the panel stops collecting.
    launch { heartbeat.beat(opened.address) }
    awaitClose {
      open = null
      opened.socket.close(NORMAL_CLOSURE, null)
      opened.bus?.close(NORMAL_CLOSURE, null)
    }
  }

  /** The chat's socket and persona-event bus, opened against [server.address]. */
  private suspend fun ProducerScope<ChatTranscript>.opening(): OpenedChat {
    val address = server.address()
    val fold = TranscriptFold(this)
    val bus = events.open(address, fold) { command ->
      if (labCommands.take(command)) launch { answer(command) }
    }
    val request = Request.Builder()
      .url(chatUrl(address.baseUrl))
      .header("Authorization", "token ${address.token}")
      .build()
    return OpenedChat(address, bus, client.newWebSocket(request, TranscriptListener(this, fold)))
  }

  override suspend fun ask(question: String, persona: String, selection: AgentSelection) {
    val socket = open?.socket ?: throw AgentChatException("the chat is not open", question)
    val frame = ChatFrames.ask(UUID.randomUUID().toString(), question, persona, selection)
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
    val (code, body) = client.callWithTokenOr(server, ioDispatcher, { message ->
      ToolCallChoiceException(call, option, message)
    }) { base ->
      url(
        base.newBuilder().addPathSegments("ai/acp/permissions").build(),
      ).post(decision.toRequestBody(JSON_MEDIA_TYPE))
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
    val (code, body) = client.callWithTokenOr(server, ioDispatcher, { message ->
      AgentChatException("stopping the reply failed: $message")
    }) { base ->
      val cancel = base.newBuilder().addPathSegments("api/ai/personas/cancel").addQueryParameter("chat_id", chatId)
      url(cancel.build()).post(ByteArray(0).toRequestBody())
    }
    if (code != HTTP_OK) throw AgentChatException("stopping the reply failed: http $code, ${body.take(BODY_EXCERPT)}")
  }

  /**
   * Shows [persona]'s state, then `POST api/events` selecting it in the chat, so it prepares and says what
   * it offers before the first question, as jupyter-ai's toolbar does on selection
   * (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`). Refused, the persona prepares at the
   * first question instead.
   */
  override suspend fun select(persona: String) {
    val chat = open ?: throw AgentChatException("the chat is not open")
    chat.fold.selected(persona)
    emit(PersonaStateFrames.personaSelected(chat.chatId, persona), "chat.persona.select.failed")
  }

  /**
   * A command for this app's web client, acked before it runs so the toolkit waits for its result rather
   * than giving up after `ack_timeout`; run on [uiDispatcher], where the editor changes its cells; and its
   * result posted (jupyterlab-commands-toolkit@7c87c5d `src/index.ts`, `handleCommand`).
   */
  private suspend fun answer(command: LabCommand) {
    val requestId = command.requestId ?: return
    emit(LabCommandFrames.ack(requestId, labCommands.id), "chat.command.ack.failed")
    val outcome = withContext(uiDispatcher) {
      notebookCells?.let { NotebookLabCommands(it).answer(command.name, command.args) }
        ?: LabCommandOutcome.Failed("${command.name}: no notebook reaches this chat")
    }
    log.reached("chat.command.answered", answeredFields(command, outcome))
    emit(LabCommandFrames.result(requestId, outcome), "chat.command.result.failed")
  }

  private fun answeredFields(command: LabCommand, outcome: LabCommandOutcome): Map<String, String> = mapOf(
    "command" to command.name,
    "success" to (outcome is LabCommandOutcome.Done).toString(),
    "chat" to chatPath,
  ) + ((outcome as? LabCommandOutcome.Failed)?.let { mapOf("error" to it.error) } ?: emptyMap())

  /** `POST api/events` with [body]; refused or failed, it is logged as [failure] and the chat goes on. */
  private suspend fun emit(body: String, failure: String) {
    val code = runCatching {
      client.callWithToken(server, ioDispatcher) { base ->
        url(base.newBuilder().addPathSegments("api/events").build()).post(body.toRequestBody(JSON_MEDIA_TYPE))
      }.code
    }.getOrElse { error -> return log.reached(failure, chatFailureFields(chatPath, error)) }
    if (code != HTTP_NO_CONTENT) log.reached(failure, chatFailureFields(chatPath, http = code))
  }

  private fun chatUrl(baseUrl: String): HttpUrl = baseUrl.toHttpUrl().newBuilder()
    .addPathSegments("api/chat/ws")
    .addPathSegment(chatPath)
    .build()

  /** OkHttp calls one listener's methods one at a time, from the socket's reader thread. */
  private inner class TranscriptListener(
    private val out: ProducerScope<ChatTranscript>,
    private val fold: TranscriptFold,
  ) : WebSocketListener() {
    private var humans = emptySet<String>()

    /** The bots writing a reply now, by username. */
    private val writers = mutableSetOf<String>()

    override fun onMessage(webSocket: WebSocket, text: String) {
      val frame = runCatching { ChatFrames.parse(text) }.getOrElse {
        out.close(it)
        return
      }
      when (frame) {
        is ServerFrame.Connection -> opened(webSocket, frame)
        is ServerFrame.Message -> fold.changed { it.with(frame.message.toChatMessage(humans)) }
        is ServerFrame.Writing -> writing(frame)
        is ServerFrame.Users -> fold.changed { it.copy(senderNames = it.senderNames + frame.names) }
        ServerFrame.Other -> return
      }
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
      // Open before the transcript says so: a collector may ask or stop on this thread as it is sent
      // (OkHttpAgentChatTest.stop_onTheFirstTranscriptFindsTheChatOpen).
      open = OpenChat(webSocket, connection.chatId, fold)
      val messages = connection.messages.map { it.toChatMessage(humans) }
      fold.opened(connection.chatId, ChatTranscript(messages, senderNames = connection.names))
    }

    private fun writing(frame: ServerFrame.Writing) {
      if (frame.writing) writers += frame.agent else writers -= frame.agent
      fold.changed { it.copy(agentWriting = writers.isNotEmpty()) }
    }
  }

  /** An open chat's socket, the id the server knows the chat by, and what it shows. */
  private class OpenChat(val socket: WebSocket, val chatId: String, val fold: TranscriptFold)

  /** What opening the chat produced: the address its requests go to, and the socket and bus to close. */
  private class OpenedChat(val address: ServerAddress, val bus: WebSocket?, val socket: WebSocket)

  companion object {
    val HEARTBEAT_INTERVAL: Duration = 30.seconds

    private const val NORMAL_CLOSURE = 1000
    private const val HTTP_OK = 200
    private const val HTTP_NO_CONTENT = 204

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
