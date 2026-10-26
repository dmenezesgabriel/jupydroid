package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentModel
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.ChatTranscript
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * How [OkHttpAgentChat] reads the persona's models off the server's event bus, beside the chat's own
 * socket: what the contract cannot say, being about the wire.
 */
class OkHttpAgentChatModelsTest {
  @get:Rule val serverRule = MockWebServerRule()

  /** Events are fire-and-forget, so the catch-up `client_connected` sends reaches only a subscriber already there. */
  @Test
  fun theEventBusIsSubscribedBeforeTheChatIsOpened() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(events = PersonaEventsLikeTheTablets(MODELS))

    withOpenChat(chatUnderTest()) { transcripts -> transcripts.until { it.agentModels.choosable } }

    val first = serverRule.server.takeRequest(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)!!
    val second = serverRule.server.takeRequest(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)!!
    assertEquals("/api/events/subscribe", first.target)
    assertEquals("token secret-token", first.headers["Authorization"])
    assertEquals("/api/chat/ws/untitled.chat", second.target)
  }

  @Test
  fun anotherChatsOrAnotherPersonasStateIsNeverShown() = runBlocking {
    serverRule.server.dispatcher =
      ChatLikeTheTabletsServer(events = PersonaEventsLikeTheTablets(MODELS, strangers = true))

    val seen = mutableListOf<ChatTranscript>()
    withOpenChat(chatUnderTest()) { transcripts ->
      seen += transcripts.until { it.agentModels.choosable }
    }

    assertTrue("$seen", seen.all { it.agentModels.current in setOf(null, MODELS.current) })
  }

  /** `report_usage` after a turn carries no `model`, and the models stay as they were. */
  @Test
  fun aTurnsUsageLeavesTheModelsAsTheyWere() = runBlocking {
    serverRule.server.dispatcher =
      ChatLikeTheTabletsServer(replies = mapOf("hello?" to "hi"), events = PersonaEventsLikeTheTablets(MODELS))
    val chat = chatUnderTest()

    withOpenChat(chat) { transcripts ->
      transcripts.until { it.agentModels.choosable }
      chat.ask("hello?")
      assertEquals(MODELS, transcripts.until { it.messages.size == 2 }.agentModels)
    }
  }

  /** The pickers are the chat's extras: a server with no event bus still answers. */
  @Test
  fun aChatWhoseServerRefusesTheEventBusStillAnswers() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(replies = mapOf("hello?" to "hi"), events = null)
    val chat = chatUnderTest()

    withOpenChat(chat) { transcripts ->
      transcripts.receive()
      chat.ask("hello?")
      val answered = transcripts.until { it.messages.size == 2 }
      assertEquals(AgentModels(), answered.agentModels)
    }
  }

  /** So the persona prepares, and says what it offers, before the first question (`_on_persona_selected`). */
  @Test
  fun openingTheChatSelectsOpencodesPersonaInIt() = runBlocking {
    val events = PersonaEventsLikeTheTablets(MODELS)
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(events = events)

    withOpenChat(chatUnderTest()) { transcripts ->
      transcripts.receive()
      withTimeout(TIMEOUT_MILLIS) { while (events.emitted.isEmpty()) kotlinx.coroutines.delay(POLL_MILLIS) }
    }

    val selected = events.emitted.single()
    assertEquals("token secret-token", selected.headers["Authorization"])
    val data = Json.parseToJsonElement(selected.body!!.utf8()).jsonObject.getValue("data").jsonObject
    assertEquals(CHAT_ID, data.getValue("chat_id").jsonPrimitive.content)
    assertEquals(OPENCODE_PERSONA_ID, data.getValue("persona_id").jsonPrimitive.content)
  }

  private suspend fun withOpenChat(chat: AgentChat, check: suspend (ReceiveChannel<ChatTranscript>) -> Unit) =
    withTimeout(TIMEOUT_MILLIS) {
      val transcripts = Channel<ChatTranscript>(Channel.UNLIMITED)
      val collecting = launch { chat.transcript().collect { transcripts.send(it) } }
      try {
        check(transcripts)
      } finally {
        collecting.cancel()
      }
    }

  private suspend fun ReceiveChannel<ChatTranscript>.until(test: (ChatTranscript) -> Boolean): ChatTranscript {
    while (true) receive().takeIf(test)?.let { return it }
  }

  private fun chatUnderTest(): AgentChat = OkHttpAgentChat(
    server = { ServerAddress(baseUrl = serverRule.server.url("/").toString(), token = "secret-token") },
  )

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
    const val POLL_MILLIS = 10L

    val MODELS = AgentModels(
      current = "opencode/big-pickle",
      offered = listOf(
        AgentModel("opencode/big-pickle", "Big Pickle"),
        AgentModel("opencode/gpt-5-nano", "GPT-5 Nano"),
      ),
    )
  }
}
