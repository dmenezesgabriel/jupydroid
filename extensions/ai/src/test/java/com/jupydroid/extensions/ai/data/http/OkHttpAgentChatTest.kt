package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extensions.ai.data.logcat.RecordingChatSocketLog
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatContract
import com.jupydroid.extensions.ai.domain.AgentChatDroppedException
import com.jupydroid.extensions.ai.domain.AgentChatException
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.ChatReading
import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import com.jupydroid.extensions.ai.domain.ToolCallChoiceException
import com.jupydroid.extensions.ai.domain.ToolCallPermission
import com.jupydroid.extensions.ai.domain.ToolCallStatus
import com.jupydroid.extensions.ai.domain.readings
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The id the scripted server's chat carries, as the tablet's `untitled.chat` did. */
private const val CHAT_ID = "02ef3329e61d445dbfb24985d749ba04"

/** The contract, against a loopback socket scripted to answer the way jupyterlab-chat's does. */
class OkHttpAgentChatTest : AgentChatContract() {
  @get:Rule val serverRule = MockWebServerRule()

  override fun chat(history: List<ChatMessage>, replies: Map<String, String>, writing: Boolean): AgentChat {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(history, replies, writing = writing)
    return chatUnderTest()
  }

  /** The workspace root's `untitled.chat`, the token in the header as on every other request. */
  @Test
  fun transcript_opensTheRootsUntitledChatWithTheToken() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer()

    withTimeout(TIMEOUT_MILLIS) { chatUnderTest().transcript().first() }

    val request = serverRule.server.takeRequest(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)!!
    assertEquals("/api/chat/ws/untitled.chat", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  /** A persona sends its answer once and then updates it by id as the agent's chunks arrive. */
  @Test
  fun transcript_showsAStreamedAnswerAsOneMessage() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(streamed = listOf("Open", "OpenCode"))

    val whole = withTimeout(TIMEOUT_MILLIS) {
      chatUnderTest().transcript().first { it.messages.singleOrNull()?.body == "OpenCode" }
    }

    assertEquals(false, whole.messages.single().fromMe)
  }

  /**
   * A server that exits drops the socket with no close frame, and OkHttp's `EOFException` for that
   * has a null message: the panel once read "the chat's socket failed: null".
   */
  @Test
  fun transcript_namesWhatFailedWhenTheServerDropsTheSocket() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(ending = WebSocket::cancel)
    val log = RecordingChatSocketLog()

    val failure = endingOf(chatUnderTest(log))

    assertEquals("the chat's socket failed: EOFException", failure.message)
    assertTrue("a drop is what the chat opens again for", failure is AgentChatDroppedException)
    assertEquals(
      mapOf("error" to "EOFException", "chat" to "untitled.chat"),
      log.fieldsOf("chat.socket.failed"),
    )
  }

  /** `WSChatHandler` closes `1008` for a path outside the server root (`websocket_handler.py`). */
  @Test
  fun transcript_logsTheCodeAndReasonWhenTheServerClosesTheSocket() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(ending = { it.close(POLICY_VIOLATION, "outside root") })
    val log = RecordingChatSocketLog()

    val failure = endingOf(chatUnderTest(log))

    assertFalse(failure.message.orEmpty().contains("null"))
    assertFalse("a close frame is the server's answer", failure is AgentChatDroppedException)
    assertEquals(
      mapOf("code" to "1008", "reason" to "outside root", "chat" to "untitled.chat"),
      log.fieldsOf("chat.socket.closed"),
    )
  }

  /**
   * The server that dropped the chat is asked for again and answers with the chat as its file holds
   * it, and the reopened chat is the one asked.
   */
  @Test
  fun readings_reopensADroppedChatAndAsksTheNewSocket() = runBlocking {
    val earlier = ChatMessage(id = "a", body = "earlier", fromMe = true)
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(history = listOf(earlier), dropOn = DROP)
    val chat = chatUnderTest()
    val readings = Channel<ChatReading>(Channel.UNLIMITED)

    val open = launch { chat.readings(reopenAfter = 10.milliseconds).collect { readings.send(it) } }
    val reopened = withTimeout(TIMEOUT_MILLIS) {
      readings.receiveUntil { it is ChatReading.Open }
      chat.ask(DROP)
      readings.receiveUntil { it is ChatReading.Reconnecting }
      readings.receiveUntil { it is ChatReading.Open } as ChatReading.Open
    }
    chat.ask("again?")
    val asked = withTimeout(TIMEOUT_MILLIS) {
      readings.receiveUntil { (it as? ChatReading.Open)?.transcript?.messages?.size == 2 } as ChatReading.Open
    }
    open.cancelAndJoin()

    assertEquals(listOf("earlier"), reopened.transcript.messages.map { it.body })
    assertEquals(listOf("earlier", "again?"), asked.transcript.messages.map { it.body })
  }

  /**
   * jupyter_server stops itself once no kernel exists and no API request has come for the idle
   * setting, and a chat socket's frames are not one (jupyter_server 2.20.0 `serverapp.py`
   * `shutdown_no_activity`): an open chat once lost its server that way.
   */
  @Test
  fun transcript_touchesApiMeWithTheTokenWhileTheChatIsOpen() = runBlocking {
    val server = ChatLikeTheTabletsServer()
    serverRule.server.dispatcher = server

    val open = launch { chatUnderTest(heartbeat = HEARTBEAT).transcript().collect {} }
    awaitTouches(server, count = 2)
    open.cancelAndJoin()

    assertEquals(listOf("token secret-token", "token secret-token"), server.touches.take(2))
  }

  /** A closed panel is what lets the server stop itself when idle, as the person set it to. */
  @Test
  fun transcript_stopsTouchingApiMeOnceTheChatIsClosed() = runBlocking {
    val server = ChatLikeTheTabletsServer()
    serverRule.server.dispatcher = server

    val open = launch { chatUnderTest(heartbeat = HEARTBEAT).transcript().collect {} }
    awaitTouches(server, count = 1)
    open.cancelAndJoin()
    delay(HEARTBEAT) // a touch already sent may still land
    val touchesWhenClosed = server.touches.size
    delay(HEARTBEAT * QUIET_INTERVALS)

    assertEquals(touchesWhenClosed, server.touches.size)
  }

  /** A refused touch is logged, and the chat it keeps alive stays open to ask. */
  @Test
  fun transcript_logsARefusedTouchAndStaysOpen() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(meStatus = SERVICE_UNAVAILABLE)
    val log = RecordingChatSocketLog()
    val chat = chatUnderTest(log, heartbeat = HEARTBEAT)

    val open = launch { chat.transcript().collect {} }
    withTimeout(TIMEOUT_MILLIS) { while ("chat.heartbeat.failed" !in log.stages) delay(HEARTBEAT) }
    chat.ask("still open?")
    open.cancelAndJoin()

    assertEquals(mapOf("http" to "503", "chat" to "untitled.chat"), log.fieldsOf("chat.heartbeat.failed"))
  }

  /**
   * jupyter-ai-acp-client@77016f6's frontend posts the decision as `submitPermissionDecision` does
   * (`src/request.ts`), the fields `PermissionHandler` reads (jupyter_ai_acp_client 0.3.3 `routes.py`).
   */
  @Test
  fun choose_postsTheSessionTheCallAndTheOptionWithTheToken() = runBlocking {
    val server =
      ChatLikeTheTabletsServer(history = listOf(ChatMessage("t", "", fromMe = false, toolCalls = listOf(PENDING))))
    serverRule.server.dispatcher = server

    chatUnderTest().choose(PENDING, ALLOW)

    val decision = server.decisions.single()
    assertEquals("token secret-token", decision.headers["Authorization"])
    val fields = Json.parseToJsonElement(decision.body!!.utf8()).jsonObject.mapValues { it.value.jsonPrimitive.content }
    assertEquals(mapOf("session_id" to "s1", "tool_call_id" to "c1", "option_id" to "once"), fields)
  }

  /**
   * The panel can still show a call waiting that the server no longer holds, when a stopped reply
   * cancelled it (`default_acp_client.py`, `stop_streaming`): the 404 is the answer, quoted.
   */
  @Test
  fun choose_failsWithTheServersAnswerWhenItHoldsNoSuchCall() {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer()

    val failure = runCatching { runBlocking { chatUnderTest().choose(PENDING, ALLOW) } }.exceptionOrNull()

    assertTrue("$failure", failure is ToolCallChoiceException)
    assertTrue("$failure", failure!!.message!!.contains("http 404"))
    assertTrue("$failure", failure.message!!.contains("No pending permission request"))
  }

  /**
   * The chat's id from its `connection` frame, as a query parameter, which is what `CancelHandler`
   * finds the chat's personas by (jupyter_ai_persona_manager `handlers.py`, in the shipped rootfs).
   */
  @Test
  fun stop_postsTheChatsIdWithTheToken() = runBlocking {
    val server = ChatLikeTheTabletsServer(writing = true)
    serverRule.server.dispatcher = server
    val chat = chatUnderTest()

    whileOpen(chat) { chat.stop() }

    val cancel = server.cancels.single()
    assertEquals("POST", cancel.method)
    assertEquals("token secret-token", cancel.headers["Authorization"])
    assertEquals(CHAT_ID, cancel.url.queryParameter("chat_id"))
  }

  /** Until its personas are initialized a chat has no persona manager, and `CancelHandler` answers 404. */
  @Test
  fun stop_failsWithTheServersAnswerWhenTheChatHasNoPersonasYet() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(writing = true, personasReady = false)
    val chat = chatUnderTest()

    val failure = whileOpen(chat) { runCatching { chat.stop() }.exceptionOrNull() }

    assertTrue("$failure", failure is AgentChatException)
    assertTrue("$failure", failure!!.message!!.contains("http 404"))
    assertTrue("$failure", failure.message!!.contains("Chat not initialized: $CHAT_ID"))
  }

  /** Runs [action] once [chat] has opened, while its transcript is still collected. */
  private suspend fun <T> whileOpen(chat: AgentChat, action: suspend () -> T): T = withTimeout(TIMEOUT_MILLIS) {
    val opened = Channel<Unit>(Channel.CONFLATED)
    val collecting = launch { chat.transcript().collect { opened.send(Unit) } }
    opened.receive()
    try {
      action()
    } finally {
      collecting.cancelAndJoin()
    }
  }

  private suspend fun awaitTouches(server: ChatLikeTheTabletsServer, count: Int) = withTimeout(TIMEOUT_MILLIS) {
    while (server.touches.size < count) delay(HEARTBEAT)
  }

  private suspend fun Channel<ChatReading>.receiveUntil(test: (ChatReading) -> Boolean): ChatReading {
    while (true) receive().takeIf(test)?.let { return it }
  }

  private suspend fun endingOf(chat: AgentChat): AgentChatException = withTimeout(TIMEOUT_MILLIS) {
    runCatching { chat.transcript().collect {} }.exceptionOrNull() as AgentChatException
  }

  private fun chatUnderTest(
    log: RecordingChatSocketLog = RecordingChatSocketLog(),
    heartbeat: Duration = OkHttpAgentChat.HEARTBEAT_INTERVAL,
  ): AgentChat = OkHttpAgentChat(
    server = { ServerAddress(baseUrl = serverRule.server.url("/").toString(), token = "secret-token") },
    log = log,
    heartbeatInterval = heartbeat,
  )

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
    const val POLICY_VIOLATION = 1008
    const val SERVICE_UNAVAILABLE = 503
    const val DROP = "drop the socket"
    const val QUIET_INTERVALS = 5
    val HEARTBEAT = 50.milliseconds
    val ALLOW = PermissionOption(id = "once", name = "Allow once", rejects = false)
    val PENDING = ToolCall(
      id = "c1",
      title = "echo probe",
      status = ToolCallStatus.Running,
      permission = ToolCallPermission.Pending(sessionId = "s1", options = listOf(ALLOW)),
    )
  }
}

/**
 * Upgrades `/api/chat/ws/<path>` and speaks as `WSChatHandler` does (jupyterlab_chat 0.25.2
 * `websocket_handler.py`): `connection` first, with the history and every user it names, the user's
 * own messages in it sent by an earlier socket's anonymous user; each `send` echoed back as a
 * `message` from the sender; then, if it named the opencode persona, the persona's reply. A
 * question naming no persona goes unanswered, as the router leaves it (`persona_manager.py`).
 *
 * @param ending what the server does to the socket once it has spoken, for a test of how it ends.
 * @param dropOn a question the server drops the socket for instead of echoing it, as an exiting server
 *   would; asked only once the chat is open, so the drop never races the `connection` frame.
 * @param meStatus what `GET /api/me` answers; every one is kept in [touches].
 * @param writing whether the persona is writing a reply as the chat opens: a `writing` frame follows
 *   `connection`, as `set_status` broadcasts one (jupyter_ai_persona_manager `base_persona.py`).
 * @param personasReady whether the chat has its persona manager, without which `POST
 *   /api/ai/personas/cancel` answers 404; with it, the cancel stops the persona's writing.
 *
 * A history message's tool calls go out in its `metadata.tool_calls` ([toolCallsOnTheWire]). A
 * `POST /ai/acp/permissions` for a call awaiting the user resolves it with the option and sends the
 * message again, as `request_permission` flushes it; for any other it answers 404, as `PermissionHandler`
 * does (jupyter_ai_acp_client 0.3.3 `default_acp_client.py`, `routes.py`).
 */
private class ChatLikeTheTabletsServer(
  private val history: List<ChatMessage> = emptyList(),
  private val replies: Map<String, String> = emptyMap(),
  private val streamed: List<String> = emptyList(),
  private val ending: (WebSocket) -> Unit = {},
  private val dropOn: String? = null,
  private val meStatus: Int = 200,
  private val writing: Boolean = false,
  private val personasReady: Boolean = true,
) : Dispatcher() {
  /** The `Authorization` header of every `GET /api/me`, in the order they came. */
  val touches: MutableList<String> = CopyOnWriteArrayList()

  /** Every `POST /ai/acp/permissions`, in the order they came. */
  val decisions: MutableList<RecordedRequest> = CopyOnWriteArrayList()

  /** Every `POST /api/ai/personas/cancel`, in the order they came. */
  val cancels: MutableList<RecordedRequest> = CopyOnWriteArrayList()

  private val held = history.toMutableList()

  @Volatile private var open: WebSocket? = null

  override fun dispatch(request: RecordedRequest): MockResponse = when (request.target) {
    "/api/me" -> {
      touches += request.headers["Authorization"].orEmpty()
      MockResponse.Builder().code(meStatus).body("{}").build()
    }
    "/ai/acp/permissions" -> decide(request)
    else -> if (request.url.encodedPath == "/api/ai/personas/cancel") cancel(request) else upgrade()
  }

  private fun upgrade(): MockResponse = MockResponse.Builder().webSocketUpgrade(Speaker()).build()

  private fun cancel(request: RecordedRequest): MockResponse {
    cancels += request
    val chatId = request.url.queryParameter("chat_id")
    if (!personasReady || chatId != CHAT_ID) {
      return MockResponse.Builder().code(NOT_FOUND).body("""{"message": "Chat not initialized: $chatId"}""").build()
    }
    open?.send(writingFrame(false))
    return MockResponse.Builder().body("""{"status": "cancelled", "cancelled": ["$PERSONA"]}""").build()
  }

  private fun decide(request: RecordedRequest): MockResponse {
    decisions += request
    val fields = Json.parseToJsonElement(request.body!!.utf8()).jsonObject.mapValues { it.value.jsonPrimitive.content }
    val index = held.indexOfFirst { message -> message.toolCalls.any { it.awaits(fields) } }
    if (index < 0) return MockResponse.Builder().code(NOT_FOUND).body(NOTHING_PENDING).build()
    val resolved = held[index].copy(toolCalls = held[index].toolCalls.map { it.resolvedBy(fields) })
    held[index] = resolved
    open?.send(frame("message", "message" to wire(resolved, PERSONA)))
    return MockResponse.Builder().body("""{"status": "ok"}""").build()
  }

  private fun ToolCall.awaits(fields: Map<String, String>): Boolean {
    val pending = permission as? ToolCallPermission.Pending ?: return false
    return id == fields["tool_call_id"] && pending.sessionId == fields["session_id"]
  }

  private fun ToolCall.resolvedBy(fields: Map<String, String>): ToolCall {
    if (!awaits(fields)) return this
    val options = (permission as ToolCallPermission.Pending).options
    return copy(permission = ToolCallPermission.Resolved(options.single { it.id == fields["option_id"] }))
  }

  private inner class Speaker : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      open = webSocket
      val messages = buildJsonArray {
        held.forEach { add(wire(it, if (it.fromMe) EARLIER_ME else PERSONA)) }
      }
      val users = buildJsonObject { listOf(EARLIER_ME, ME, PERSONA).forEach { put(it, user(it)) } }
      val id = "id" to JsonPrimitive(CHAT_ID)
      webSocket.send(frame("connection", id, "user" to user(ME), "messages" to messages, "users" to users))
      if (writing) webSocket.send(writingFrame(true))
      streamed.forEach { webSocket.send(frame("message", "message" to wire("streamed", it, PERSONA))) }
      ending(webSocket)
    }

    /** Answers the panel's close as Tornado does, or the server would wait on the socket forever. */
    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      webSocket.close(code, null)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      val send = Json.parseToJsonElement(text).jsonObject
      val body = send.getValue("body").jsonPrimitive.content
      if (body == dropOn) return webSocket.cancel()
      webSocket.send(frame("message", "message" to wire(send.getValue("id").jsonPrimitive.content, body, ME)))
      val persona = send["metadata"]?.jsonObject?.get("to_persona")?.jsonPrimitive?.content
      val reply = replies[body]?.takeIf { persona == OPENCODE_PERSONA_ID } ?: return
      webSocket.send(frame("message", "message" to wire("reply-to-$body", reply, PERSONA)))
    }
  }

  private fun wire(id: String, body: String, sender: String) = wire(ChatMessage(id, body, fromMe = false), sender)

  private fun wire(message: ChatMessage, sender: String) = buildJsonObject {
    put("id", JsonPrimitive(message.id))
    put("body", JsonPrimitive(message.body))
    put("sender", JsonPrimitive(sender))
    put("time", JsonPrimitive(0.0))
    if (message.toolCalls.isNotEmpty()) put("metadata", toolCallsOnTheWire(message.toolCalls))
  }

  private fun writingFrame(state: Boolean): String =
    frame("writing", "user" to user(PERSONA), "state" to JsonPrimitive(state))

  private fun user(username: String) = buildJsonObject {
    put("username", JsonPrimitive(username))
    put("bot", JsonPrimitive(username == PERSONA))
  }

  private fun frame(action: String, vararg fields: Pair<String, JsonElement>): String =
    JsonObject(mapOf("type" to JsonPrimitive("server"), "action" to JsonPrimitive(action)) + fields).toString()

  private companion object {
    const val ME = "0f7fbf88210940d7bc0811bf662d373e"

    /** jupyter_server makes a new anonymous user for every token-authenticated socket. */
    const val EARLIER_ME = "7e0614b6251348fe8361f4254fed2f73"
    const val PERSONA = OPENCODE_PERSONA_ID
    const val NOT_FOUND = 404

    /** Tornado's error body for `PermissionHandler`'s 404. */
    const val NOTHING_PENDING =
      """{"message": "No pending permission request found for this session", "reason": null}"""
  }
}
