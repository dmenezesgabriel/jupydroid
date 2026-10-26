package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentSetting
import com.jupydroid.extensions.ai.domain.ChatTranscript
import kotlinx.coroutines.channels.SendChannel

/**
 * The transcript one open chat shows, folded from two sockets: the chat's own frames and the persona
 * states on the server's event bus, each read on its own thread. Every change sends the whole
 * transcript to [out].
 *
 * A state can arrive before the chat says its id, so each is kept by the chat it names and the one for
 * this chat is shown once the id is known.
 *
 * ```
 * val fold = TranscriptFold(out, OPENCODE_PERSONA_ID)
 * fold.opened(chatId, ChatTranscript(messages))
 * fold.stated(PersonaStateFrames.parse(text)!!)
 * ```
 *
 * @param persona the one persona whose state is shown; the bus carries every persona in every chat.
 */
internal class TranscriptFold(private val out: SendChannel<ChatTranscript>, private val persona: String) {
  private var chatId: String? = null
  private var transcript = ChatTranscript()
  private val modelsByChat = mutableMapOf<String, AgentModels>()
  private val settingsByChat = mutableMapOf<String, List<AgentSetting>>()

  /** The chat said its id, and what it holds. */
  @Synchronized
  fun opened(id: String, held: ChatTranscript) {
    chatId = id
    transcript = held
    send()
  }

  /** The chat's messages or writing changed as [change] says. */
  @Synchronized
  fun changed(change: (ChatTranscript) -> ChatTranscript) {
    transcript = change(transcript)
    send()
  }

  /** A persona's state arrived; only [persona]'s is kept, and only what it says, each part apart. */
  @Synchronized
  fun stated(state: PersonaState) {
    if (state.personaId != persona) return
    state.models?.let { modelsByChat[state.chatId] = it }
    state.settings?.let { settingsByChat[state.chatId] = it }
    if (state.chatId == chatId) send()
  }

  private fun send() {
    val id = chatId
    val shown = transcript.copy(
      agentModels = id?.let(modelsByChat::get) ?: AgentModels(),
      agentSettings = id?.let(settingsByChat::get).orEmpty(),
    )
    out.trySend(shown)
  }
}
