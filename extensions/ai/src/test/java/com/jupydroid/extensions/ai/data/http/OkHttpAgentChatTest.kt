package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extensions.ai.data.logcat.RecordingChatSocketLog
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatContract
import com.jupydroid.extensions.ai.domain.AgentChatDroppedException
import com.jupydroid.extensions.ai.domain.AgentChatException
import com.jupydroid.extensions.ai.domain.AgentCommand
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentSetting
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.ChatReading
import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import com.jupydroid.extensions.ai.domain.ToolCallChoiceException
import com.jupydroid.extensions.ai.domain.ToolCallPermission
import com.jupydroid.extensions.ai.domain.ToolCallStatus
import com.jupydroid.extensions.ai.domain.readings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.WebSocket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** The contract, against a loopback socket scripted to answer the way jupyterlab-chat's does. */
class OkHttpAgentChatTest : AgentChatContract() {
  @get:Rule val serverRule = MockWebServerRule()

  override fun chat(
    history: List<ChatMessage>,
    replies: Map<String, String>,
    writing: Boolean,
    models: AgentModels,
    settings: List<AgentSetting>,
    commands: List<AgentCommand>,
  ): AgentChat {
    val events = PersonaEventsLikeTheTablets(models, settings, commands)
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(history, replies, writing = writing, events = events)
    return chatUnderTest()
  }

  /** The workspace root's `untitled.chat`, the token in the header as on every other request. */
  @Test
  fun transcript_opensTheRootsUntitledChatWithTheToken() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer()

    withTimeout(TIMEOUT_MILLIS) { chatUnderTest().transcript().first() }

    // The event bus is subscribed first (OkHttpAgentChatModelsTest.theEventBusIsSubscribedBeforeTheChatIsOpened).
    serverRule.server.takeRequest(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)!!
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

  /**
   * The first transcript says the chat is open, so what its collector does on seeing it finds it open.
   * Collected unconfined, the collector runs on the socket's own thread as the transcript is sent, the
   * order a loaded host can give it any time.
   */
  @Test
  fun stop_onTheFirstTranscriptFindsTheChatOpen() = runBlocking {
    val server = ChatLikeTheTabletsServer(writing = true)
    serverRule.server.dispatcher = server
    val chat = chatUnderTest()

    withTimeout(TIMEOUT_MILLIS) {
      withContext(Dispatchers.Unconfined) { chat.transcript().first { chat.stop().let { true } } }
    }

    assertEquals(1, server.cancels.size)
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
