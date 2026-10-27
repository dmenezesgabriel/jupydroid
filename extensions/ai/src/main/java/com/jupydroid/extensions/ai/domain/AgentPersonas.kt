package com.jupydroid.extensions.ai.domain

/**
 * One agent a chat can ask: a persona the server lists in the chat, as jupyter-ai's `PersonaOption`
 * (jupyter-ai-persona-manager@d5ed04b `jupyter_ai_persona_manager/awareness_models.py`).
 *
 * @param id what a question is addressed to, as `metadata.to_persona`.
 * @param name what the user reads, as "Claude".
 */
data class AgentPersona(override val id: String, override val name: String) : AgentOption

/**
 * The agents a chat lists, and the one asked when the user picks none in the panel.
 *
 * ```
 * ChatTranscript(agentPersonas = listOf(AgentPersona("p", "Claude"))).agents.nameInUse(picked = null) // "Claude"
 * ```
 *
 * @param current the agent the newest question went to, while it is listed (the server keeps each question's
 *   `metadata.to_persona` in the `.chat` file: jupyter-chat@2c224bc
 *   `python/jupyterlab-chat/jupyterlab_chat/websocket_handler.py`, `_handle_new_message`), else the sole one
 *   listed (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`, `reconcileSelection`), else none.
 *   None is never guessed from the list's order, which is entry-point load order and ranks nothing.
 */
data class AgentPersonas(override val current: String?, override val offered: List<AgentPersona>) : AgentChoice {
  /**
   * [picked] while it is listed, as VS Code reuses a remembered agent only while it is usable
   * (vscode@3f3380c `src/vs/workbench/contrib/chat/common/constants.ts`, `getUsableRememberedSessionType`);
   * else [current]. Null asks no one, so nothing is sent: a question naming no persona is answered by none
   * (jupyter-ai-persona-manager@d5ed04b `jupyter_ai_persona_manager/persona_manager.py`, `on_chat_message`).
   */
  override fun inUse(picked: String?): String? = picked?.takeIf { id -> offered.any { it.id == id } } ?: current

  companion object {
    /** [listed], the one asked being the newest of [messages]' questions' persona, as [current] says. */
    fun of(listed: List<AgentPersona>, messages: List<ChatMessage>): AgentPersonas {
      val ids = listed.mapTo(mutableSetOf()) { it.id }
      val newestAsked = messages.lastOrNull { it.fromMe && it.askedOf != null }?.askedOf
      val current = newestAsked?.takeIf { it in ids } ?: listed.singleOrNull()?.id
      return AgentPersonas(current, listed)
    }
  }
}
