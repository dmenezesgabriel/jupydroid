package com.jupydroid.extensions.ai.domain

/**
 * Something the agent lets the user pick one value of for a chat, and the value it uses now: its model,
 * or one of its settings. jupyter-ai builds one control from each (jupyter-ai-persona-manager@d5ed04b
 * `src/persona-controls.tsx`, `buildControls`).
 *
 * ```
 * AgentModels("opencode/big-pickle", listOf(AgentModel("opencode/big-pickle", "Big Pickle")))
 *   .nameInUse(picked = null) // "Big Pickle"
 * ```
 */
sealed interface AgentChoice {
  /** The value's id; null until the agent's session has started, or when it keeps its default. */
  val current: String?

  /** What can be picked, in the agent's order. */
  val offered: List<AgentOption>

  /** Whether there is anything to pick: jupyter-ai draws a control only then (`buildControls`). */
  val choosable: Boolean get() = offered.isNotEmpty()

  /** [picked] if the user picked one for their next question, as jupyter-ai's `effectiveValue`; else [current]. */
  fun inUse(picked: String?): String? = picked ?: current

  /** The name of the value [inUse], or its id when nothing offered carries it (`currentControlLabel`). */
  fun nameInUse(picked: String?): String? {
    val id = inUse(picked) ?: return null
    return offered.find { it.id == id }?.name ?: id
  }
}

/** One value an [AgentChoice] offers; [name] is what the user reads, [id] what a question is asked with. */
sealed interface AgentOption {
  val id: String
  val name: String
}
