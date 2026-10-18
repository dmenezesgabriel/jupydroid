package com.jupydroid.extensions.ai.domain

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [AgentChat] must do, whoever implements it.
 *
 * `runBlocking` rather than `runTest`: an implementation may answer from another thread, as a socket
 * does, and virtual time would not wait for it.
 */
abstract class AgentChatContract {
  /**
   * A chat already holding [history], whose agent answers each question in [replies] with the reply
   * it maps to, and every other question with nothing.
   */
  protected abstract fun chat(
    history: List<ChatMessage> = emptyList(),
    replies: Map<String, String> = emptyMap(),
  ): AgentChat

  /** What was asked before the chat was opened again is still the user's, and the answer the agent's. */
  @Test
  fun openingAChatShowsWhatItAlreadyHolds() = withOpenChat(
    chat(
      history = listOf(
        ChatMessage(id = "a", body = "earlier", fromMe = true),
        ChatMessage(id = "b", body = "answered", fromMe = false),
      ),
    ),
  ) { _, transcripts ->
    val shown = transcripts.receive().messages.map { it.body to it.fromMe }
    assertEquals(listOf("earlier" to true, "answered" to false), shown)
  }

  @Test
  fun aQuestionIsAnsweredBeneathItself() = withOpenChat(chat(replies = mapOf("hello?" to "hi"))) { chat, transcripts ->
    transcripts.receive()

    chat.ask("hello?")

    val answered = transcripts.until { it.messages.size == 2 }
    assertEquals(listOf("hello?" to true, "hi" to false), answered.messages.map { it.body to it.fromMe })
  }

  @Test
  fun askingAChatNobodyOpenedThrowsAndNamesTheQuestion() = runBlocking {
    val failure = runCatching { chat().ask("hello?") }.exceptionOrNull()

    assertTrue("$failure", failure is AgentChatException)
    assertTrue("$failure", failure!!.message!!.contains("'hello?'"))
  }

  private fun withOpenChat(chat: AgentChat, check: suspend (AgentChat, ReceiveChannel<ChatTranscript>) -> Unit) =
    runBlocking {
      withTimeout(TIMEOUT_MILLIS) {
        val transcripts = Channel<ChatTranscript>(Channel.UNLIMITED)
        val collecting = launch { chat.transcript().collect { transcripts.send(it) } }
        try {
          check(chat, transcripts)
        } finally {
          collecting.cancel()
        }
      }
    }

  private suspend fun ReceiveChannel<ChatTranscript>.until(test: (ChatTranscript) -> Boolean): ChatTranscript {
    while (true) receive().takeIf(test)?.let { return it }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
