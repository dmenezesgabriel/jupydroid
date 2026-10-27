package com.jupydroid.extensions.ai.domain

/**
 * What the user picked in a chat's panel for the questions they send next: the agent, and what it answers
 * with. jupyter-ai keeps the same pair in its persona controls (jupyter-ai-persona-manager@d5ed04b
 * `src/persona-controls.tsx`, `selectedId` and `settings`).
 *
 * ```
 * AgentPick().withAgent("claude").agentIn(transcript) // "claude", while the chat lists it
 * ```
 *
 * @param agent the agent picked, or null to ask the one the chat goes by ([ChatTranscript.agents]).
 * @param selection what was picked for [agent]; what it leaves out keeps the agent's.
 */
data class AgentPick(val agent: String? = null, val selection: AgentSelection = AgentSelection()) {
  /** The agent a question in [transcript] goes to; null asks no one, so nothing is sent. */
  fun agentIn(transcript: ChatTranscript): String? = transcript.agents.inUse(agent)

  /** [id] picked, with nothing picked for it yet: a model picked for another agent may not be offered by it. */
  fun withAgent(id: String): AgentPick = AgentPick(agent = id)
}
