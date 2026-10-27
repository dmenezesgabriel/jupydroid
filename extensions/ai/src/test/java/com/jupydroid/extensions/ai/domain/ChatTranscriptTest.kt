package com.jupydroid.extensions.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatTranscriptTest {
  private val question = ChatMessage(id = "q", body = "hello?", fromMe = true)

  @Test
  fun aNewMessageGoesBeneathTheOthers() {
    val reply = ChatMessage(id = "r", body = "hi", fromMe = false)

    assertEquals(listOf(question, reply), ChatTranscript(listOf(question)).with(reply).messages)
  }

  /** A persona streams its answer as updates to one message (jupyter_ai_acp_client 0.3.3 `base_acp_persona.py`). */
  @Test
  fun anUpdateReplacesTheMessageWithItsIdInPlace() {
    val partial = ChatMessage(id = "r", body = "Open", fromMe = false)
    val whole = partial.copy(body = "OpenCode isn't configured yet.")

    val transcript = ChatTranscript(listOf(question, partial)).with(whole)

    assertEquals(listOf(question, whole), transcript.messages)
  }

  /** jupyter-ai's ACP client creates each of an agent's messages empty, and fills a text one by its chunks. */
  @Test
  fun aMessageWithNoTextYetIsLeftOutUntilItsFirstText() {
    val opened = ChatMessage(id = "r", body = "", fromMe = false)
    val transcript = ChatTranscript(listOf(question, opened))

    assertEquals(listOf(question), transcript.messagesShown)
    assertEquals(listOf(question, opened.copy(body = "1")), transcript.with(opened.copy(body = "1")).messagesShown)
  }

  /** jupyter-ai's ACP client leaves a message of tool calls empty, its calls in metadata (`tool_call_manager.py`). */
  @Test
  fun aMessageHoldingOnlyToolCallsIsShown() {
    val read = ToolCall(id = "c", title = "probe.txt", status = ToolCallStatus.Completed)
    val calls = ChatMessage(id = "t", body = "", fromMe = false, toolCalls = listOf(read))

    assertEquals(listOf(question, calls), ChatTranscript(listOf(question, calls)).messagesShown)
  }

  /** A message streams in while the agent writes, and its writing is no message's to end. */
  @Test
  fun aMessageArrivingKeepsWhetherTheAgentWrites() {
    val writing = ChatTranscript(listOf(question), agentWriting = true)

    assertEquals(true, writing.with(ChatMessage(id = "r", body = "1", fromMe = false)).agentWriting)
  }

  /** jupyter-chat names a sender by `display_name`, and draws no name above the reader's own message. */
  @Test
  fun anAnswerIsNamedByItsSenderAndAQuestionIsNot() {
    val answer = ChatMessage(id = "r", body = "hi", fromMe = false, sender = OPENCODE)
    val transcript = ChatTranscript(listOf(question, answer), senderNames = mapOf(OPENCODE to "OpenCode"))

    assertEquals(listOf(null, "OpenCode"), transcript.messagesShown.map(transcript::senderName))
  }

  /** jupyter-chat stacks a message under the one before when both are the same sender's (`model.ts`). */
  @Test
  fun anAnswerUnderTheSameSendersShownOneIsNotNamedAgain() {
    val calls = ChatMessage(id = "t", body = "", fromMe = false, sender = OPENCODE, toolCalls = listOf(read))
    val empty = ChatMessage(id = "e", body = "", fromMe = false, sender = CLAUDE)
    val answer = ChatMessage(id = "r", body = "done", fromMe = false, sender = OPENCODE)
    val names = mapOf(OPENCODE to "OpenCode", CLAUDE to "Claude")
    val transcript = ChatTranscript(listOf(question, calls, empty, answer), senderNames = names)

    assertEquals(listOf(null, "OpenCode", null), transcript.namesAbove)
  }

  /** A sender the `users` map does not name yet is named by its username, as jupyter-chat falls back. */
  @Test
  fun aSenderWithNoNameYetIsNamedByItsUsername() {
    val answer = ChatMessage(id = "r", body = "hi", fromMe = false, sender = OPENCODE)

    assertEquals(OPENCODE, ChatTranscript(listOf(answer)).senderName(answer))
  }

  private val read = ToolCall(id = "c", title = "probe.txt", status = ToolCallStatus.Completed)

  private companion object {
    const val OPENCODE = "jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"
    const val CLAUDE = "jupyter-ai-personas::jupyter_ai_acp_client::ClaudeAcpPersona"
  }
}
