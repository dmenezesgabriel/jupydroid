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

  /**
   * What a list of the values shows for [query]: those whose name or id holds it in any case, as
   * jupyter-ai's `filterChoices` (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`) — the id
   * too, since it names the provider a display name leaves out. The value [inUse] comes first, where
   * jupyter-ai heads its list with it (`defaultChoiceLabel`), then the rest in the agent's order.
   *
   * ```
   * models.listing(picked = null, query = "cohere") // [North Mini Code (free)]
   * ```
   */
  fun listing(picked: String?, query: String): List<AgentOption> {
    val wanted = query.trim()
    val inUse = inUse(picked)
    return offered
      .filter { it.name.contains(wanted, ignoreCase = true) || it.id.contains(wanted, ignoreCase = true) }
      .sortedByDescending { it.id == inUse }
  }
}

/** One value an [AgentChoice] offers; [name] is what the user reads, [id] what a question is asked with. */
sealed interface AgentOption {
  val id: String
  val name: String
}
