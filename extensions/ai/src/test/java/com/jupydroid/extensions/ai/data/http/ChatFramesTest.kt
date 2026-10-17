package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extensions.ai.domain.AgentChatException
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

    assertEquals(ServerFrame.Connection(username = "0f7fbf88210940d7bc0811bf662d373e", messages = emptyList()), frame)
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

  private fun resource(name: String): String = javaClass.classLoader!!.getResource(name)!!.readText()
}
