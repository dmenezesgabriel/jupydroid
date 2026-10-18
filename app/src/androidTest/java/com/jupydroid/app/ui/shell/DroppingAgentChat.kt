package com.jupydroid.app.ui.shell

import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentChatDroppedException
import com.jupydroid.extensions.ai.domain.ChatTranscript
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * A chat that shows [before] and drops, as a socket does when its server exits, then opens again
 * showing [after] once [reopen] is called - so a test can look at the panel while it reconnects.
 *
 * ```
 * val chat = DroppingAgentChat(before, after) // the panel reconnects ...
 * chat.reopen()                                // ... and reads [after] back
 * ```
 */
class DroppingAgentChat(private val before: ChatTranscript, private val after: ChatTranscript) : AgentChat {
  private val reopened = CompletableDeferred<Unit>()
  private var openings = 0

  /** Lets the reopening answer. */
  fun reopen() {
    reopened.complete(Unit)
  }

  override fun transcript(): Flow<ChatTranscript> = flow {
    if (openings++ == 0) {
      emit(before)
      throw AgentChatDroppedException(DROPPED)
    }
    reopened.await()
    emit(after)
    awaitCancellation()
  }

  override suspend fun ask(question: String) = Unit

  companion object {
    const val DROPPED = "the chat's socket failed: EOFException"
  }
}
