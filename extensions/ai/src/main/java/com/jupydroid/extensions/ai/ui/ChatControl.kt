package com.jupydroid.extensions.ai.ui

import com.jupydroid.extensions.ai.domain.AgentChoice
import com.jupydroid.extensions.ai.domain.AgentPick
import com.jupydroid.extensions.ai.domain.ChatTranscript

/**
 * A chip's [tag], which its list and search field extend, whether its list opens on a search field, and
 * what it says while nothing is in use.
 */
internal data class ChoiceChip(val tag: String, val searchable: Boolean, val unchosen: String = "")

/**
 * One control of the Chat's toolbar: the [choice] it picks from, under its [label], as jupyter-ai heads each
 * control in its overflow menu (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`,
 * `OverflowControlsMenu`), drawn as its own [chip] or as a section of the one chip's menu.
 *
 * @param picked the value picked for the next question, or null to keep the agent's.
 */
internal data class ChatControl(
  val label: String,
  val choice: AgentChoice,
  val picked: String?,
  val chip: ChoiceChip,
  val onPick: (String) -> Unit,
) {
  /** The name of the value in use, or what the chip says while there is none. */
  val valueName: String get() = choice.nameInUse(picked) ?: chip.unchosen
}

/**
 * What the one chip says: the model in use, as VS Code's phone chip names only its model
 * (vscode@3f3380c `src/vs/workbench/contrib/chat/browser/widget/input/chatPhoneInputPresenter.ts`,
 * `_updateTrigger`); the agent while it offers no models.
 *
 * ```
 * controlsName(listOf(agent, model, mode)) // "Big Pickle"
 * ```
 */
internal fun controlsName(controls: List<ChatControl>): String =
  // The model is the one control whose list is searched ([chatControls]).
  (controls.find { it.chip.searchable } ?: controls.first()).valueName

/**
 * Every control and its value, for a screen reader to read the one chip by, as VS Code's aria label names its
 * mode and its model (`chatPhoneInputPresenter.ts`, `_updateTrigger`).
 *
 * ```
 * controlsLabel(listOf(agent, model)) // "Agent: OpenCode, Model: Big Pickle"
 * ```
 */
internal fun controlsLabel(controls: List<ChatControl>): String =
  controls.joinToString(", ") { "${it.label}: ${it.valueName}" }

/**
 * The controls [transcript] offers with [pick], in jupyter-ai's order, its persona menu first
 * (jupyter-ai-persona-manager@d5ed04b `src/persona-controls.tsx`, `buildControls`): the agent, then, once there
 * is one, its model and each of its general settings (its mode among them). One offering no values is left
 * out, as `buildControls` draws no control for it.
 *
 * ```
 * chatControls(transcript, pick) { pick = it }.map { it.label } // ["Agent", "Model", "Session Mode"]
 * ```
 */
internal fun chatControls(transcript: ChatTranscript, pick: AgentPick, onPick: (AgentPick) -> Unit): List<ChatControl> {
  // With several agents and nothing to go by, the chip asks rather than guessing. jupyter-ai shows "No one"
  // there, but a question to no one is answered by none (jupyter-ai-persona-manager@d5ed04b
  // `jupyter_ai_persona_manager/persona_manager.py`, `on_chat_message`), so nothing is sent until a pick.
  val agentChip = ChoiceChip("chat-agent", searchable = false, unchosen = "Choose agent")
  val agent = ChatControl("Agent", transcript.agents, pick.agent, agentChip) { onPick(pick.withAgent(it)) }
  if (pick.agentIn(transcript) == null) return listOf(agent).filter { it.choice.choosable }
  return (listOf(agent, modelControl(transcript, pick, onPick)) + settingControls(transcript, pick, onPick))
    .filter { it.choice.choosable }
}

/**
 * Only the models are searched, as VS Code searches its model picker and not its mode picker: an agent
 * signed in to OpenRouter offers hundreds of models, and a setting two or three values.
 */
private fun modelControl(transcript: ChatTranscript, pick: AgentPick, onPick: (AgentPick) -> Unit): ChatControl {
  val selection = pick.selection
  return ChatControl("Model", transcript.agentModels, selection.model, ChoiceChip("chat-model", searchable = true)) {
    onPick(pick.copy(selection = selection.copy(model = it)))
  }
}

private fun settingControls(
  transcript: ChatTranscript,
  pick: AgentPick,
  onPick: (AgentPick) -> Unit,
): List<ChatControl> = transcript.agentSettings.map { setting ->
  val selection = pick.selection
  val chip = ChoiceChip("chat-setting-${setting.id}", searchable = false)
  ChatControl(setting.name, setting, selection.settings[setting.id], chip) {
    onPick(pick.copy(selection = selection.copy(settings = selection.settings + (setting.id to it))))
  }
}
