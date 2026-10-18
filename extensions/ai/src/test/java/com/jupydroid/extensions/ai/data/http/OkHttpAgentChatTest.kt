package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extensions.ai.data.logcat.RecordingChatSocketLog
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatContract
import com.jupydroid.extensions.ai.domain.AgentChatException
import com.jupydroid.extensions.ai.domain.ChatMessage
import kotlinx.coroutines.flow.first
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
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

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
    assertEquals(
      mapOf("code" to "1008", "reason" to "outside root", "chat" to "untitled.chat"),
      log.fieldsOf("chat.socket.closed"),
    )
  }

  private suspend fun endingOf(chat: AgentChat): AgentChatException = withTimeout(TIMEOUT_MILLIS) {
    runCatching { chat.transcript().collect {} }.exceptionOrNull() as AgentChatException
  }

  private fun chatUnderTest(log: RecordingChatSocketLog = RecordingChatSocketLog()): AgentChat = OkHttpAgentChat(
    server = { ServerAddress(baseUrl = serverRule.server.url("/").toString(), token = "secret-token") },
    log = log,
  )

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
    const val POLICY_VIOLATION = 1008
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
 */
private class ChatLikeTheTabletsServer(
  private val history: List<ChatMessage> = emptyList(),
  private val replies: Map<String, String> = emptyMap(),
  private val streamed: List<String> = emptyList(),
  private val ending: (WebSocket) -> Unit = {},
) : Dispatcher() {
  override fun dispatch(request: RecordedRequest): MockResponse =
    MockResponse.Builder().webSocketUpgrade(Speaker()).build()

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
