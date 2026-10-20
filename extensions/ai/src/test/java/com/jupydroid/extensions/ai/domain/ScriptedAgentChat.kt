package com.jupydroid.extensions.ai.domain

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * An [AgentChat] whose every opening plays the next of [openings]: its transcripts, then its ending,
 * or stays open for good when its ending is null. Opened more often than scripted, it stays open with
 * nothing to show.
 *
 * ```
 * ScriptedAgentChat(ChatOpening(listOf(transcript), ending = AgentChatDroppedException("dropped")))
 * ```
 */
class ScriptedAgentChat(vararg openings: ChatOpening) : AgentChat {
  private val remaining = ArrayDeque(openings.toList())

  /** How many times [transcript] was collected. */
  var opened = 0
    private set

  override fun transcript(): Flow<ChatTranscript> = flow {
    opened++
    val opening = remaining.removeFirstOrNull() ?: awaitCancellation()
    opening.transcripts.forEach { emit(it) }
    throw opening.ending ?: awaitCancellation()
  }

  override suspend fun ask(question: String) = Unit

  override suspend fun choose(call: ToolCall, option: PermissionOption) = Unit

  override suspend fun stop() = Unit
}

/** One opening of a [ScriptedAgentChat]: what it shows, then how it ends; null never ends. */
data class ChatOpening(val transcripts: List<ChatTranscript>, val ending: AgentChatException? = null)
