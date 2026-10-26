package com.jupydroid.extensions.ai.domain

/**
 * The models the agent offers for a chat, and the one it answers with now: jupyter-ai's
 * `ModelConfiguration`, which its ACP persona fills from the agent's `model` config option
 * (jupyter_ai_acp_client 0.3.3 `base_acp_persona.py`, `_build_awareness_config`, in the shipped rootfs).
 *
 * ```
 * AgentModels("opencode/big-pickle", listOf(AgentModel("opencode/big-pickle", "Big Pickle")))
 *   .nameInUse(picked = null) // "Big Pickle"
 * ```
 *
 * @param current the model's id; null until the agent's session has started, or when it keeps its default.
 * @param offered what can be picked, in the agent's order.
 */
data class AgentModels(val current: String? = null, val offered: List<AgentModel> = emptyList()) {
  /**
   * Whether there is anything to pick: jupyter-ai draws its Model control only then
   * (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`, `buildControls`).
   */
  val choosable: Boolean get() = offered.isNotEmpty()

  /** [picked] if the user picked one for their next question, as jupyter-ai's `effectiveValue`; else [current]. */
  fun inUse(picked: String?): String? = picked ?: current

  /** The name of the model [inUse], or its id when nothing offered carries it (`currentControlLabel`). */
  fun nameInUse(picked: String?): String? {
    val id = inUse(picked) ?: return null
    return offered.find { it.id == id }?.name ?: id
  }
}

/** One model the agent offers; [name] is what the user reads, [id] what a question is asked with. */
data class AgentModel(val id: String, val name: String)

/**
 * What the user picked for the questions they send next; null keeps what the agent uses now. jupyter-ai
 * stamps it on each outgoing message (jupyter-ai-persona-manager@d5ed04b `src/metadata.ts`).
 *
 * ```
 * chat.ask("Which model are you?", AgentSelection(model = "opencode/gpt-5-nano"))
 * ```
 */
data class AgentSelection(val model: String? = null)
