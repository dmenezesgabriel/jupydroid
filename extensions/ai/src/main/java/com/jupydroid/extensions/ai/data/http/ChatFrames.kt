package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extensions.ai.domain.AgentChatException
import com.jupydroid.extensions.ai.domain.ChatMessage
import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import com.jupydroid.extensions.ai.domain.ToolCallPermission
import com.jupydroid.extensions.ai.domain.ToolCallStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * opencode's persona, as jupyter-ai's router reads it from `metadata.to_persona`:
 * `jupyter-ai-personas::<top-level module>::<class>` (jupyter_ai_persona_manager 0.2.2
 * `base_persona.py`, `BasePersona.id`) for `OpenCodeAcpPersona` (jupyter_ai_acp_client 0.3.3
 * `acp_personas/opencode.py`). A message naming no persona is answered by none (`persona_manager.py`,
 * `on_chat_message`).
 */
internal const val OPENCODE_PERSONA_ID = "jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"

/** A message as the socket carries it; `sender` is a username, a persona's being its id. */
@Serializable
internal data class WireMessage(
  val id: String,
  val body: String = "",
  val sender: String,
  val metadata: WireMetadata = WireMetadata(),
) {
  fun toChatMessage(humans: Set<String>): ChatMessage = ChatMessage(
    id = id,
    body = body,
    fromMe = sender in humans,
    toolCalls = metadata.toolCalls.map(WireToolCall::toToolCall),
  )
}

/**
 * The part of a message's metadata the panel reads. jupyter-ai's ACP client replaces it with
 * `{"tool_calls": [...]}` each time one of the message's calls changes (jupyter_ai_acp_client 0.3.3
 * `tool_call_manager.py`, `flush_tool_call`), each call a `ToolCallState` dumped with
 * `exclude_none`, so its keys are snake_case and an unset one is absent (`tool_call_renderer.py`).
 */
@Serializable
internal data class WireMetadata(@SerialName("tool_calls") val toolCalls: List<WireToolCall> = emptyList())

/**
 * One call. `request_permission` adds the agent's options, `pending` and the session to a call it
 * asks about, and once answered sets `resolved` and the option chosen, if any
 * (jupyter_ai_acp_client 0.3.3 `default_acp_client.py`).
 */
@Serializable
internal data class WireToolCall(
  @SerialName("tool_call_id") val id: String,
  val title: String,
  val status: String? = null,
  @SerialName("permission_status") val permissionStatus: String? = null,
  @SerialName("permission_options") val permissionOptions: List<WirePermissionOption> = emptyList(),
  @SerialName("selected_option_id") val selectedOptionId: String? = null,
  @SerialName("session_id") val sessionId: String? = null,
) {
  /**
   * An absent status is a running call, as jupyter-chat-components reads it
   * (jupyter-chat-components@992c6fb `src/components/grouped-tool-calls.tsx`, `ToolCallRow`).
   */
  fun toToolCall(): ToolCall = ToolCall(
    id = id,
    title = title,
    status = when (status) {
      "completed" -> ToolCallStatus.Completed
      "failed" -> ToolCallStatus.Failed
      else -> ToolCallStatus.Running
    },
    permission = permission(),
  )

  private fun permission(): ToolCallPermission? {
    val options = permissionOptions.map(WirePermissionOption::toPermissionOption)
    return when (permissionStatus) {
      "pending" -> sessionId?.let { ToolCallPermission.Pending(it, options) }
      "resolved" -> ToolCallPermission.Resolved(options.find { it.id == selectedOptionId })
      else -> null
    }
  }
}

/** ACP's `PermissionOption` dumped by field name (`acp/schema.py`); `kind` is one of ACP's four. */
@Serializable
internal data class WirePermissionOption(@SerialName("option_id") val id: String, val name: String, val kind: String) {
  /** jupyter-chat-components counts a kind as a rejection when it contains `reject` (`grouped-tool-calls.tsx`). */
  fun toPermissionOption(): PermissionOption = PermissionOption(id = id, name = name, rejects = "reject" in kind)
}

/** What the panel draws from a server frame; `users` and `metadata` draw nothing yet. */
internal sealed interface ServerFrame {
  /**
   * @param chatId the chat's stable id (jupyterlab_chat `websocket_model.py`, `get_id`, in the shipped
   *   rootfs), which a request about the chat rather than its socket names it by.
   * @param humans every username the chat knows that is no bot, this socket's own among them. A
   *   device has one person, and jupyter_server names them afresh on every socket: a token with no
   *   login cookie gets `generate_anonymous_user`, a new `uuid4` (jupyter_server 2.20.0
   *   `auth/identity.py`, `IdentityProvider.get_user_token`). So the question asked before a relaunch
   *   is an earlier user's, and only `bot` tells it from a persona's answer (ADR-0106).
   */
  data class Connection(val chatId: String, val humans: Set<String>, val messages: List<WireMessage>) : ServerFrame

  data class Message(val message: WireMessage) : ServerFrame

  /**
   * [agent], a bot, started writing a reply or stopped. A persona says so when its reply starts and
   * once it ends or is stopped (jupyter_ai_acp_client `default_acp_client.py`, `prompt_and_reply` and
   * `stop_streaming`, in the shipped rootfs); the frame is never in the `.chat` file.
   */
  data class Writing(val agent: String, val writing: Boolean) : ServerFrame

  data object Other : ServerFrame
}

/**
 * jupyterlab-chat's `/api/chat/ws/<path>` frames (jupyterlab_chat 0.25.2 `ws_messages.py`).
 *
 * ```
 * ChatFrames.parse("""{"type": "server", "action": "users", "users": {}}""") // ServerFrame.Other
 * ```
 */
internal object ChatFrames {
  private val lenient = Json { ignoreUnknownKeys = true }

  /** Enough of an unexpected frame to recognise it by. */
  private const val FRAME_EXCERPT = 200

  /**
   * One server frame; `connection` and `message` are the two the transcript is made of. A person's
   * `writing` is nothing to draw: jupyter-chat offers Stop only while a bot writes
   * (jupyter-chat@2c224bc `packages/jupyter-chat/src/components/input/buttons/stop-button.tsx`).
   */
  fun parse(text: String): ServerFrame {
    val frame = runCatching { lenient.decodeFromString<IncomingFrame>(text) }.getOrNull()
    val user = frame?.user
    return when {
      frame?.action == "connection" && user != null && frame.id != null ->
        ServerFrame.Connection(frame.id, frame.humans() + user.username, frame.messages)
      frame?.action == "message" && frame.message != null -> ServerFrame.Message(frame.message)
      frame?.action == "writing" && user?.bot == true -> ServerFrame.Writing(user.username, frame.state)
      frame != null && frame.action !in TRANSCRIPT_ACTIONS -> ServerFrame.Other
      else -> throw AgentChatException(
        "the chat sent a frame that is not jupyterlab-chat's: ${text.take(FRAME_EXCERPT)}",
      )
    }
  }

  /** A `send` frame: `parse_client_message` reads its `id`, `body` and `metadata`. */
  fun ask(id: String, question: String, persona: String): String = buildJsonObject {
    put("type", "client")
    put("action", "send")
    put("id", id)
    put("body", question)
    put("metadata", buildJsonObject { put("to_persona", JsonPrimitive(persona)) })
  }.toString()

  private val TRANSCRIPT_ACTIONS = setOf("connection", "message")
}

@Serializable
private data class IncomingFrame(
  val action: String,
  val id: String? = null,
  val user: FrameUser? = null,
  val state: Boolean = false,
  val messages: List<WireMessage> = emptyList(),
  val message: WireMessage? = null,
  val users: Map<String, FrameUser> = emptyMap(),
) {
  fun humans(): Set<String> = users.values.filterNot { it.bot }.mapTo(mutableSetOf()) { it.username }
}

@Serializable
private data class FrameUser(val username: String, val bot: Boolean = false)
