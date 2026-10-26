package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extensions.ai.domain.AgentCommand
import com.jupydroid.extensions.ai.domain.AgentModel
import com.jupydroid.extensions.ai.domain.AgentModels
import com.jupydroid.extensions.ai.domain.AgentSetting
import com.jupydroid.extensions.ai.domain.AgentSettingValue
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A persona's state for one chat, as one `persona_state` event carries it.
 *
 * @param models null when the event changed something else: a setter emits only what it changed, and
 *   what it leaves out is as it was (jupyter_ai_persona_manager 0.2.2 `persona_events.py`,
 *   `PersonaSessionState._emit`, in the shipped rootfs).
 * @param settings null, likewise, when the event changed something other than the general settings.
 * @param commands null, likewise, when the event changed something other than the slash commands.
 */
internal data class PersonaState(
  val chatId: String,
  val personaId: String,
  val models: AgentModels?,
  val settings: List<AgentSetting>? = null,
  val commands: List<AgentCommand>? = null,
)

/**
 * The server's event bus, `/api/events/subscribe`: every event the server emits, each frame
 * `{"schema_id": …, **data}` (jupyter_server 2.20.0 `services/events/handlers.py`,
 * `SubscribeWebsocket.event_listener`). The persona manager publishes each persona's state on it
 * (`persona_events.py`).
 *
 * ```
 * PersonaStateFrames.parse(text)?.models // the models, if this frame is a persona's state saying them
 * ```
 */
internal object PersonaStateFrames {
  private const val PERSONA_STATE = "https://schema.jupyter.org/jupyter_ai_persona_manager/persona_state/v1"
  private const val PERSONA_SELECTED = "https://schema.jupyter.org/jupyter_ai_persona_manager/persona_selected/v1"

  private val lenient = Json { ignoreUnknownKeys = true }

  /** [text] as a persona's state, or null for any other event: the bus is the whole server's. */
  fun parse(text: String): PersonaState? = runCatching { lenient.decodeFromString<WirePersonaState>(text) }
    .getOrNull()
    ?.takeIf { it.schemaId == PERSONA_STATE }
    ?.toPersonaState()

  /**
   * `POST /api/events`'s body selecting [persona] in [chatId], which `_on_persona_selected` answers by
   * preparing it, so it says what it offers before the first question (`persona_manager.py`).
   */
  fun personaSelected(chatId: String, persona: String): String = buildJsonObject {
    put("schema_id", PERSONA_SELECTED)
    put("version", "1")
    put(
      "data",
      buildJsonObject {
        put("chat_id", chatId)
        put("persona_id", persona)
      },
    )
  }.toString()
}

@Serializable
private data class WirePersonaState(
  @SerialName("schema_id") val schemaId: String? = null,
  @SerialName("chat_id") val chatId: String? = null,
  @SerialName("persona_id") val personaId: String? = null,
  val model: WireModelConfiguration? = null,
  val settings: List<WireSettingConfiguration>? = null,
  @SerialName("slash_commands") val slashCommands: List<WireCommandOption>? = null,
) {
  /** Null for a state that names no chat or no persona, which no chat could show. */
  fun toPersonaState(): PersonaState? {
    if (chatId == null || personaId == null) return null
    val general = settings?.map(WireSettingConfiguration::toAgentSetting)
    val commands = slashCommands?.map(WireCommandOption::toAgentCommand)
    return PersonaState(chatId, personaId, model?.toAgentModels(), general, commands)
  }
}

/** `ModelConfiguration` dumped (`awareness_models.py`); its `settings` are not read here. */
@Serializable
private data class WireModelConfiguration(
  val current: String? = null,
  val options: List<WireModelOption> = emptyList(),
) {
  fun toAgentModels(): AgentModels = AgentModels(current, options.map { AgentModel(it.id, it.name ?: it.id) })
}

/** `ModelOption`: its `name` "falls back to `id` when None". */
@Serializable
private data class WireModelOption(val id: String, val name: String? = null)

/** `SettingConfiguration` dumped (`awareness_models.py`); its `name`, like each option's, falls back to `id`. */
@Serializable
private data class WireSettingConfiguration(
  val id: String,
  val current: String? = null,
  val name: String? = null,
  val options: List<WireModelOption> = emptyList(),
) {
  fun toAgentSetting(): AgentSetting =
    AgentSetting(id, name ?: id, current, options.map { AgentSettingValue(it.id, it.name ?: it.id) })
}

/**
 * `CommandOption` dumped (`awareness_models.py`). jupyter-ai's ACP client gives each name its `/`, but the
 * model does not require one, so a name without it is given one, as jupyter-ai's frontend does
 * (jupyter-ai-persona-manager@d5ed04b `src/slash-commands.ts`).
 */
@Serializable
private data class WireCommandOption(val name: String, val description: String? = null) {
  fun toAgentCommand(): AgentCommand = AgentCommand(if (name.startsWith("/")) name else "/$name", description)
}
