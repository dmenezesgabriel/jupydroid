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
data class AgentModels(override val current: String? = null, override val offered: List<AgentModel> = emptyList()) :
  AgentChoice

/** One model the agent offers; [name] is what the user reads, [id] what a question is asked with. */
data class AgentModel(override val id: String, override val name: String) : AgentOption

/**
 * What the user picked for the questions they send next; null, or a setting left out, keeps what the
 * agent uses now. jupyter-ai stamps it on each outgoing message (jupyter-ai-persona-manager@d5ed04b
 * `src/metadata.ts`).
 *
 * ```
 * chat.ask("Which model are you?", AgentSelection(model = "opencode/gpt-5-nano"))
 * chat.ask("Plan it first", AgentSelection(settings = mapOf("__mode__" to "plan")))
 * ```
 *
 * @param settings a value per [AgentSetting.id].
 */
data class AgentSelection(val model: String? = null, val settings: Map<String, String> = emptyMap()) {
  /**
   * This selection with only the settings in [listed]: the agent's settings change with its model, as
   * opencode offers `effort` only for a model with variants (anomalyco/opencode@e3c4eb24
   * `packages/opencode/src/acp/config-option.ts`, `buildEffortSelectOption`).
   */
  fun listedIn(listed: List<AgentSetting>): AgentSelection {
    val ids = listed.mapTo(mutableSetOf()) { it.id }
    return copy(settings = settings.filterKeys { it in ids })
  }
}
