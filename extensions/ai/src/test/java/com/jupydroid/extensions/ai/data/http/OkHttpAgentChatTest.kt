package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extensions.ai.data.logcat.RecordingChatSocketLog
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatContract
import com.jupydroid.extensions.ai.domain.AgentChatDroppedException
import com.jupydroid.extensions.ai.domain.AgentChatException
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.ChatReading
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

/** The contract, against a loopback socket scripted to answer the way jupyterlab-chat's does. */
class OkHttpAgentChatTest : AgentChatContract() {
  @get:Rule val serverRule = MockWebServerRule()

  override fun chat(history: List<ChatMessage>, replies: Map<String, String>): AgentChat {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(history, replies)
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
 */
private class ChatLikeTheTabletsServer(
  private val history: List<ChatMessage> = emptyList(),
  private val replies: Map<String, String> = emptyMap(),
  private val streamed: List<String> = emptyList(),
  private val ending: (WebSocket) -> Unit = {},
  private val dropOn: String? = null,
  private val meStatus: Int = 200,
) : Dispatcher() {
  /** The `Authorization` header of every `GET /api/me`, in the order they came. */
  val touches: MutableList<String> = CopyOnWriteArrayList()

  override fun dispatch(request: RecordedRequest): MockResponse {
    if (request.target != "/api/me") return MockResponse.Builder().webSocketUpgrade(Speaker()).build()
    touches += request.headers["Authorization"].orEmpty()
    return MockResponse.Builder().code(meStatus).body("{}").build()
  }

  private inner class Speaker : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      val messages = buildJsonArray {
        history.forEach { add(wire(it.id, it.body, if (it.fromMe) EARLIER_ME else PERSONA)) }
      }
      val users = buildJsonObject { listOf(EARLIER_ME, ME, PERSONA).forEach { put(it, user(it)) } }
      webSocket.send(frame("connection", "user" to user(ME), "messages" to messages, "users" to users))
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

  private fun wire(id: String, body: String, sender: String) = buildJsonObject {
    put("id", JsonPrimitive(id))
    put("body", JsonPrimitive(body))
    put("sender", JsonPrimitive(sender))
    put("time", JsonPrimitive(0.0))
  }

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
  }
}
