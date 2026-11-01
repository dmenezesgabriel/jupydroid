@file:OptIn(ExperimentalCoroutinesApi::class)

package com.jupydroid.extensions.ai.domain

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.seconds

/**
 * When a chat opens again, after jupyter-chat@2c224bc `packages/jupyterlab-chat/src/websocket-handler.ts`:
 * only a socket that had opened and then dropped, and every second until it opens.
 */
class ChatReadingsTest {
  @Test
  fun aDroppedChatIsReconnectingThenReadBack() = runTest {
    val chat = ScriptedAgentChat(
      ChatOpening(listOf(BEFORE), ending = AgentChatDroppedException("dropped")),
      ChatOpening(listOf(AFTER)),
    )

    val readings = chat.readings().take(4).toList()

    assertEquals(
      listOf(
        ChatReading.Opening,
        ChatReading.Open(BEFORE),
        ChatReading.Reconnecting(BEFORE, "dropped"),
        ChatReading.Open(AFTER),
      ),
      readings,
    )
  }

  @Test
  fun aChatOpensAgainOnlyAfterTheDelay() = runTest {
    val chat = ScriptedAgentChat(
      ChatOpening(listOf(BEFORE), ending = AgentChatDroppedException("dropped")),
      ChatOpening(listOf(AFTER)),
    )

    chat.readings(reopenAfter = 3.seconds).take(4).toList()

    assertEquals(3.seconds.inWholeMilliseconds, currentTime)
  }

  /** jupyter-chat keeps retrying once a chat has opened, a reopen that fails included. */
  @Test
  fun aReopenThatDropsTooIsTriedAgain() = runTest {
    val chat = ScriptedAgentChat(
      ChatOpening(listOf(BEFORE), ending = AgentChatDroppedException("dropped")),
      ChatOpening(emptyList(), ending = AgentChatDroppedException("still down")),
      ChatOpening(listOf(AFTER)),
    )

    val last = chat.readings().take(5).toList().last()

    assertEquals(ChatReading.Open(AFTER), last)
    assertEquals(3, chat.opened)
  }

  /** A server that cannot be reached at all would be retried for ever, and nothing says so. */
  @Test
  fun aChatThatNeverOpenedFails() = runTest {
    val chat = ScriptedAgentChat(ChatOpening(emptyList(), ending = AgentChatDroppedException("refused")))

    assertEquals(listOf(ChatReading.Opening, ChatReading.Failed("refused")), chat.readings().toList())
  }

  /** A close frame is the server's answer, `1008` for a path outside its root (`websocket_handler.py`). */
  @Test
  fun aChatTheServerClosedFailsWithoutReopening() = runTest {
    val chat = ScriptedAgentChat(ChatOpening(listOf(BEFORE), ending = AgentChatException("closed: 1008")))

    val readings = chat.readings().toList()

    assertEquals(ChatReading.Failed("closed: 1008"), readings.last())
    assertEquals(1, chat.opened)
  }

  private companion object {
    val BEFORE = ChatTranscript(listOf(ChatMessage("q", "before the drop", fromMe = true)))
    val AFTER = BEFORE.with(ChatMessage("a", "read back", fromMe = false))
  }
}
