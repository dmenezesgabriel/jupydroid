package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extensions.ai.domain.AgentChatException
import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import com.jupydroid.extensions.ai.domain.ToolCallPermission
import com.jupydroid.extensions.ai.domain.ToolCallStatus
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatFramesTest {
  /** The first frame the tablet's server sent on a chat nobody had written to yet. */
  @Test
  fun theConnectionFrameNamesWhoIAmAndWhatTheChatHolds() {
    val frame = ChatFrames.parse(resource("connection-on-a-new-chat.json"))

    assertEquals(
      ServerFrame.Connection(humans = setOf("0f7fbf88210940d7bc0811bf662d373e"), messages = emptyList()),
      frame,
    )
  }

  /**
   * After a relaunch the socket is a new anonymous user, and the question in the history is an
   * earlier one's; both are people, while the persona and jupyter-ai's system user are bots.
   */
  @Test
  fun theConnectionFrameNamesEveryPersonTheChatHasSeen() {
    val frame = ChatFrames.parse(resource("connection-after-a-relaunch.json")) as ServerFrame.Connection

    assertEquals(setOf("7e0614b6251348fe8361f4254fed2f73", "b80d82e1618d4c79b80c1923f0c241a9"), frame.humans)
  }

  /** Shaped as `ServerMessageMessage` serializes a persona's `Message` (jupyterlab_chat 0.25.2 `ws_messages.py`). */
  @Test
  fun aMessageFrameCarriesOneMessageAndWhoSentIt() {
    val frame = ChatFrames.parse(resource("message-from-the-persona.json"))

    val expected = WireMessage(
      id = "7c1e4a52-0f4b-4f0e-9a51-2b7d3c9e8f10",
      body = "OpenCode isn't configured yet.",
      sender = OPENCODE_PERSONA_ID,
    )
    assertEquals(ServerFrame.Message(expected), frame)
  }

  /**
   * The empty message opencode's persona sent around its read tool on the tablet, its calls in
   * `metadata.tool_calls` (jupyter_ai_acp_client 0.3.3 `tool_call_manager.py`, `flush_tool_call`).
   */
  @Test
  fun aMessageFrameCarriesTheToolCallsInItsMetadata() {
    val frame = ChatFrames.parse(resource("message-with-a-tool-call.json")) as ServerFrame.Message

    val read = ToolCall(
      id = "765737ab-0c32-4ea7-ab51-a379c2ba7d58",
      title = "home/jupydroid/probe.txt",
      status = ToolCallStatus.Completed,
    )
    assertEquals(listOf(read), frame.message.toChatMessage(humans = emptySet()).toolCalls)
  }

  /**
   * ACP's four statuses (`acp/schema.py`, `ToolCallStatus`), and none at all, which `exclude_none`
   * leaves off the wire and jupyter-chat-components reads as `in_progress`
   * (jupyter-chat-components@992c6fb `src/components/grouped-tool-calls.tsx`, `ToolCallRow`).
   */
  @Test
  fun aCallIsRunningUntilItCompletesOrFails() {
    val statuses = listOf(""""pending"""", """"in_progress"""", """"completed"""", """"failed"""", null)

    val read = statuses.map { status -> callWith(status).status }

    val running = ToolCallStatus.Running
    assertEquals(listOf(running, running, ToolCallStatus.Completed, ToolCallStatus.Failed, running), read)
  }

  /**
   * A call the agent asks about: `request_permission` sets the options, `pending` and the session on
   * the call and flushes it (jupyter_ai_acp_client 0.3.3 `default_acp_client.py`), each option an ACP
   * `PermissionOption` dumped by field name (`acp/schema.py`), with the three names opencode's request
   * was logged with.
   */
  @Test
  fun aCallAwaitingApprovalCarriesItsSessionAndTheAgentsOptions() {
    val frame = ChatFrames.parse(resource("message-awaiting-permission.json")) as ServerFrame.Message

    val call = frame.message.toChatMessage(humans = emptySet()).toolCalls.single()

    val options = listOf(
      PermissionOption(id = "once", name = "Allow once", rejects = false),
      PermissionOption(id = "always", name = "Always allow", rejects = false),
      PermissionOption(id = "reject", name = "Reject", rejects = true),
    )
    assertEquals(ToolCallPermission.Pending(sessionId = "ses_3a9f0c1e7bffe2", options = options), call.permission)
  }

  /** Once answered the call is `resolved` with `selected_option_id`, and its options stay on it. */
  @Test
  fun aResolvedCallCarriesTheOptionChosen() {
    val call = callWith(
      status = null,
      permission = """, "permission_status": "resolved", "selected_option_id": "no", "session_id": "s",""" +
        """ "permission_options": [{"option_id": "no", "name": "Reject", "kind": "reject_always"}]""",
    )

    val chosen = PermissionOption(id = "no", name = "Reject", rejects = true)
    assertEquals(ToolCallPermission.Resolved(chosen), call.permission)
  }

  /** The cancelled outcome resolves the call with no option selected (`default_acp_client.py`). */
  @Test
  fun aCallResolvedWithNoOptionWasCancelled() {
    val call = callWith(status = null, permission = """, "permission_status": "resolved", "session_id": "s"""")

    assertEquals(ToolCallPermission.Resolved(chosen = null), call.permission)
  }

  @Test
  fun aCallNobodyAskedAboutHasNoPermission() {
    assertEquals(null, callWith(status = null).permission)
  }

  /** The second frame the tablet's server sent; nothing the panel draws comes from it. */
  @Test
  fun everyOtherActionIsNothingToDraw() {
    val users = """{"users": {"hidden::jupyter_ai_system": {"username": "hidden::jupyter_ai_system"}},""" +
      """ "type": "server", "action": "users"}"""

    assertEquals(ServerFrame.Other, ChatFrames.parse(users))
  }

  @Test
  fun aFrameThatIsNotTheProtocolsThrowsAndQuotesIt() {
    val failure = runCatching { ChatFrames.parse("<html>502</html>") }.exceptionOrNull()

    assertTrue("$failure", failure is AgentChatException)
    assertTrue("$failure", failure!!.message!!.contains("<html>502</html>"))
  }

  /** `parse_client_message` reads `id`, `body` and `metadata`; the router reads `metadata.to_persona`. */
  @Test
  fun aQuestionIsASendFrameAddressedToThePersona() {
    val frame = Json.parseToJsonElement(ChatFrames.ask(id = "q1", question = "hello?", persona = OPENCODE_PERSONA_ID))
      .jsonObject

    assertEquals("client", frame.text("type"))
    assertEquals("send", frame.text("action"))
    assertEquals("q1", frame.text("id"))
    assertEquals("hello?", frame.text("body"))
    assertEquals(OPENCODE_PERSONA_ID, frame.getValue("metadata").jsonObject.text("to_persona"))
  }

  private fun JsonObject.text(key: String): String = getValue(key).jsonPrimitive.content

  private fun callWith(status: String?, permission: String = ""): ToolCall {
    val statusField = status?.let { """, "status": $it""" }.orEmpty()
    val call = """{"tool_call_id": "c", "title": "ls"$statusField$permission}"""
    val message = """{"id": "m", "body": "", "sender": "p", "metadata": {"tool_calls": [$call]}}"""
    val frame = ChatFrames.parse("""{"type": "server", "action": "message", "message": $message}""")
    return (frame as ServerFrame.Message).message.toChatMessage(humans = emptySet()).toolCalls.single()
  }

  private fun resource(name: String): String = javaClass.classLoader!!.getResource(name)!!.readText()
}
