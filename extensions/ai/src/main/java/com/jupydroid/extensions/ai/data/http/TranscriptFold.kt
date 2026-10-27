package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extensions.ai.domain.AgentCommand
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentPersona
import com.jupydroid.extensions.ai.domain.AgentSetting
import com.jupydroid.extensions.ai.domain.ChatTranscript
import kotlinx.coroutines.channels.SendChannel

/**
 * The transcript one open chat shows, folded from two sockets: the chat's own frames and the persona
 * lists and states on the server's event bus, each read on its own thread. Every change sends the whole
 * transcript to [out].
 *
 * A list or state can arrive before the chat says its id, so each is kept by the chat it names, and a
 * state by its persona too, and the one for this chat is shown once the id is known. Only the [selected]
 * persona's state is shown, as jupyter-ai's controls follow the persona picked
 * (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`).
 *
 * ```
 * val fold = TranscriptFold(out)
 * fold.opened(chatId, ChatTranscript(messages))
 * fold.selected(personaId)
 * fold.stated(PersonaStateFrames.parse(text)!!)
 * ```
 */
internal class TranscriptFold(private val out: SendChannel<ChatTranscript>) {
  private var chatId: String? = null
  private var persona: String? = null
  private var transcript = ChatTranscript()
  private val personasByChat = mutableMapOf<String, List<AgentPersona>>()
  private val modelsByPersona = mutableMapOf<Pair<String, String>, AgentModels>()
  private val settingsByPersona = mutableMapOf<Pair<String, String>, List<AgentSetting>>()
  private val commandsByPersona = mutableMapOf<Pair<String, String>, List<AgentCommand>>()

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

  /** [id]'s state is the one shown from now on. */
  @Synchronized
  fun selected(id: String) {
    persona = id
    if (chatId != null) send()
  }

  /** A chat's persona list arrived. */
  @Synchronized
  fun listed(list: PersonaList) {
    personasByChat[list.chatId] = list.personas
    if (list.chatId == chatId) send()
  }

  /** A persona's state arrived; only what it says is kept, each part apart. */
  @Synchronized
  fun stated(state: PersonaState) {
    val key = state.chatId to state.personaId
    state.models?.let { modelsByPersona[key] = it }
    state.settings?.let { settingsByPersona[key] = it }
    state.commands?.let { commandsByPersona[key] = it }
    if (state.chatId == chatId && state.personaId == persona) send()
  }

  private fun send() {
    val id = chatId
    val key = id?.let { chat -> persona?.let { chat to it } }
    val shown = transcript.copy(
      agentModels = key?.let(modelsByPersona::get) ?: AgentModels(),
      agentSettings = key?.let(settingsByPersona::get).orEmpty(),
      agentCommands = key?.let(commandsByPersona::get).orEmpty(),
      agentPersonas = id?.let(personasByChat::get).orEmpty(),
    )
    out.trySend(shown)
  }
}
