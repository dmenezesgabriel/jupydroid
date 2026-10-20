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

  @Test
  fun choosingAnOptionResolvesTheCallWithIt() = withOpenChat(chat(history = listOf(AWAITING))) { chat, transcripts ->
    val call = transcripts.receive().messages.single().toolCalls.single()

    chat.choose(call, ALLOW_ONCE)

    val resolved = transcripts.until { it.messages.single().toolCalls.single().permission != call.permission }
    assertEquals(ToolCallPermission.Resolved(ALLOW_ONCE), resolved.messages.single().toolCalls.single().permission)
  }

  /** The server answers 404 when no permission is pending (jupyter_ai_acp_client 0.3.3 `routes.py`). */
  @Test
  fun choosingForACallNothingAwaitsThrowsAndNamesTheCall() {
    val answered = AWAITING.toolCalls.single().copy(permission = ToolCallPermission.Resolved(ALLOW_ONCE))
    val history = listOf(AWAITING.copy(toolCalls = listOf(answered)))
    withOpenChat(chat(history = history)) { chat, transcripts ->
      val call = transcripts.receive().messages.single().toolCalls.single()

      val failure = runCatching { chat.choose(call, ALLOW_ONCE) }.exceptionOrNull()

      assertTrue("$failure", failure is AgentChatException)
      assertTrue("$failure", failure!!.message!!.contains("'Allow once' for 'echo probe'"))
    }
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

    val ALLOW_ONCE = PermissionOption(id = "once", name = "Allow once", rejects = false)
    val REJECT = PermissionOption(id = "reject", name = "Reject", rejects = true)

    /** An agent's message holding one call that waits for the user, as opencode's `bash` does. */
    val AWAITING = ChatMessage(
      id = "t",
      body = "",
      fromMe = false,
      toolCalls = listOf(
        ToolCall(
          id = "c1",
          title = "echo probe",
          status = ToolCallStatus.Running,
          permission = ToolCallPermission.Pending(sessionId = "s1", options = listOf(ALLOW_ONCE, REJECT)),
        ),
      ),
    )
  }
}
