package com.jupydroid.extensions.ai.domain

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Where a chat stands, as its panel draws it. */
sealed interface ChatReading {
  data object Opening : ChatReading

  data class Open(val transcript: ChatTranscript) : ChatReading

  /** The chat dropped for [reason] and is being opened again; [transcript] is what it last showed. */
  data class Reconnecting(val transcript: ChatTranscript, val reason: String) : ChatReading

  data class Failed(val message: String) : ChatReading
}

/**
 * The chat's [ChatReading]s while this is collected, opening it again [reopenAfter] a drop.
 *
 * jupyter-chat's rule (jupyter-chat@2c224bc `packages/jupyterlab-chat/src/websocket-handler.ts`): only
 * a chat that has opened is opened again, only after a drop ([AgentChatDroppedException]), and for as
 * long as it takes. A close frame is the server's answer, and a chat that never opened has a server
 * that does not answer, so both fail. Unlike jupyter-chat, which reopens silently, the reopening is a
 * reading of its own, so the panel can say why Send is off.
 *
 * ```
 * chat.readings().collect { shown = it }
 * ```
 */
fun AgentChat.readings(reopenAfter: Duration = 1.seconds): Flow<ChatReading> = flow {
  emit(ChatReading.Opening)
  var lastShown: ChatTranscript? = null
  while (true) {
    val ending = openOnce { lastShown = it.also { shown -> emit(ChatReading.Open(shown)) } } ?: return@flow
    val shown = lastShown
    if (ending !is AgentChatDroppedException || shown == null) {
      return@flow emit(ChatReading.Failed(ending.message.orEmpty()))
    }
    emit(ChatReading.Reconnecting(shown, ending.message.orEmpty()))
    delay(reopenAfter)
  }
}

/** Collects the transcript of one opening; what ended it, or null when it ended without a failure. */
private suspend fun AgentChat.openOnce(show: suspend (ChatTranscript) -> Unit): AgentChatException? = try {
  transcript().collect(show)
  null
} catch (ending: AgentChatException) {
  ending
}
