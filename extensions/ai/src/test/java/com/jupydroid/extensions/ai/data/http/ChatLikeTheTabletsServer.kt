package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentPersona
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.ToolCall
import com.jupydroid.extensions.ai.domain.ToolCallPermission
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.RecordedRequest
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.CopyOnWriteArrayList

/** The id the scripted server's chat carries, as the tablet's `untitled.chat` did. */
const val CHAT_ID = "02ef3329e61d445dbfb24985d749ba04"

/**
 * Upgrades `/api/chat/ws/<path>` and speaks as `WSChatHandler` does (jupyterlab_chat 0.25.2
 * `websocket_handler.py`): `connection` first, with the history and every user it names, the user's
 * own messages in it sent by an earlier socket's anonymous user; each `send` echoed back as a
 * `message` from the sender, its `metadata` kept; then, if it named one of [personas], that persona's
 * reply. A question naming no persona goes unanswered, as the router leaves it (`persona_manager.py`).
 *
 * @param ending what the server does to the socket once it has spoken, for a test of how it ends.
 * @param dropOn a question the server drops the socket for instead of echoing it, as an exiting server
 *   would; asked only once the chat is open, so the drop never races the `connection` frame.
 * @param meStatus what `GET /api/me` answers; every one is kept in [touches].
 * @param writing whether the persona is writing a reply as the chat opens: a `writing` frame follows
 *   `connection`, as `set_status` broadcasts one (jupyter_ai_persona_manager `base_persona.py`).
 * @param personasReady whether the chat has its persona manager, without which `POST
 *   /api/ai/personas/cancel` answers 404; with it, the cancel stops the persona's writing.
 * @param events `/api/events/subscribe` and `POST /api/events`, told when the chat socket opens and
 *   what each question picked; null answers both 404, as a server with no events API would.
 * @param personas the personas loaded in the chat, each named in the `users` map as `as_user` names it;
 *   opencode's answers a history message, as it did every one on the tablet.
 *
 * A history message's tool calls go out in its `metadata.tool_calls` ([toolCallsOnTheWire]). A
 * `POST /ai/acp/permissions` for a call awaiting the user resolves it with the option and sends the
 * message again, as `request_permission` flushes it; for any other it answers 404, as `PermissionHandler`
 * does (jupyter_ai_acp_client 0.3.3 `default_acp_client.py`, `routes.py`).
 */
internal class ChatLikeTheTabletsServer(
  private val history: List<ChatMessage> = emptyList(),
  private val replies: Map<String, String> = emptyMap(),
  private val streamed: List<String> = emptyList(),
  private val ending: (WebSocket) -> Unit = {},
  private val dropOn: String? = null,
  private val meStatus: Int = 200,
  private val writing: Boolean = false,
  private val personasReady: Boolean = true,
  private val events: PersonaEventsLikeTheTablets? = PersonaEventsLikeTheTablets(AgentModels()),
  private val personas: List<AgentPersona> = listOf(AgentPersona(OPENCODE_PERSONA_ID, "OpenCode")),
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
    "/api/events/subscribe" -> events?.subscribe() ?: notFound()
    "/api/events" -> events?.emit(request) ?: notFound()
    else -> if (request.url.encodedPath == "/api/ai/personas/cancel") cancel(request) else upgrade()
  }

  private fun notFound(): MockResponse = MockResponse.Builder().code(NOT_FOUND).build()

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
      // The `.chat` file keeps the users who wrote in it (`ychat.py`); a persona that has not answered
      // yet registers once the socket is open, and `set_user` broadcasts it (`websocket_model.py`).
      val answered = held.any { !it.fromMe }
      val known = listOf(EARLIER_ME, ME) + listOfNotNull(PERSONA.takeIf { answered })
      val users = buildJsonObject { known.forEach { put(it, user(it)) } }
      val id = "id" to JsonPrimitive(CHAT_ID)
      webSocket.send(frame("connection", id, "user" to user(ME), "messages" to messages, "users" to users))
      val registering = personas.map { it.id }.filterNot { it in known }
      if (registering.isNotEmpty()) {
        webSocket.send(frame("users", "users" to buildJsonObject { registering.forEach { put(it, user(it)) } }))
      }
      events?.clientConnected()
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
      val metadata = send["metadata"]?.jsonObject
      val question = wire(ChatMessage(send.getValue("id").jsonPrimitive.content, body, fromMe = true), ME, metadata)
      webSocket.send(frame("message", "message" to question))
      val persona = metadata?.get("to_persona")?.jsonPrimitive?.content
      if (persona == OPENCODE_PERSONA_ID) {
        val settings = metadata["settings"]?.jsonObject.orEmpty().mapValues { it.value.jsonPrimitive.content }
        events?.asked(metadata["model"]?.jsonObject?.get("id")?.jsonPrimitive?.contentOrNull, settings)
      }
      val reply = replies[body]?.takeIf { personas.any { it.id == persona } } ?: return
      webSocket.send(frame("message", "message" to wire("reply-to-$body", reply, persona!!)))
    }
  }

  private fun wire(id: String, body: String, sender: String) = wire(ChatMessage(id, body, fromMe = false), sender)

  private fun wire(message: ChatMessage, sender: String, metadata: JsonObject? = null) = buildJsonObject {
    put("id", JsonPrimitive(message.id))
    put("body", JsonPrimitive(message.body))
    put("sender", JsonPrimitive(sender))
    put("time", JsonPrimitive(0.0))
    if (message.toolCalls.isNotEmpty()) put("metadata", toolCallsOnTheWire(message.toolCalls))
    metadata?.let { put("metadata", it) }
  }

  private fun writingFrame(state: Boolean): String =
    frame("writing", "user" to user(PERSONA), "state" to JsonPrimitive(state))

  /** As `as_user` names a persona (`base_persona.py`) and the tablet's server named its anonymous users. */
  private fun user(username: String) = buildJsonObject {
    val persona = personas.find { it.id == username }
    put("username", JsonPrimitive(username))
    put("display_name", JsonPrimitive(persona?.name ?: "Anonymous Leda"))
    put("bot", JsonPrimitive(persona != null))
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
