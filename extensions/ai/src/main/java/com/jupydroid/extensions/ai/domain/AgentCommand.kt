package com.jupydroid.extensions.ai.domain

/**
 * A slash command the agent offers for a chat: jupyter-ai's `CommandOption`, which its ACP client fills
 * from the agent's `available_commands_update` (jupyter_ai_acp_client 0.3.3 `default_acp_client.py`, in
 * the shipped rootfs). The command travels as the question's text; the agent runs it.
 *
 * ```
 * AgentCommand("/compact", "compact the session").typed // "/compact "
 * ```
 *
 * @param name with its leading `/`.
 * @param description what it does, if the agent says.
 */
data class AgentCommand(val name: String, val description: String?) {
  /** What picking it puts in the field: its name and a space, as jupyter-chat's `spaceOnAccept` does. */
  val typed: String get() = "$name "
}

/**
 * The commands [question] could still become, in the agent's order: none unless it starts with `/` and is
 * still one word. jupyter-ai completes the word under the caret wherever it is
 * (jupyter-ai-persona-manager@d5ed04b `src/slash-commands.ts`), but opencode runs a command only as the
 * prompt's first word (anomalyco/opencode@e3c4eb24 `packages/opencode/src/acp/service.ts`, `prompt`).
 *
 * ```
 * listOf(AgentCommand("/compact", null), AgentCommand("/init", null)).completing("/co") // [/compact]
 * ```
 */
fun List<AgentCommand>.completing(question: String): List<AgentCommand> {
  if (!question.startsWith("/") || question.any(Char::isWhitespace)) return emptyList()
  return filter { it.name.startsWith(question) }
}
