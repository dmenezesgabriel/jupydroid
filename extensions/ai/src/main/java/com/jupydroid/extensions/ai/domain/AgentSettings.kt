package com.jupydroid.extensions.ai.domain

/**
 * One of the agent's general settings for a chat, and the value it uses now: jupyter-ai's
 * `SettingConfiguration`. Its ACP persona makes one of the agent's mode, as `__mode__`, and of every
 * config option that is neither the model nor a model setting, so of opencode's `effort`
 * (jupyter_ai_acp_client 0.3.3 `base_acp_persona.py`, `_build_awareness_config`, in the shipped rootfs).
 *
 * ```
 * AgentSetting("__mode__", "Session Mode", "build", listOf(AgentSettingValue("build", "build")))
 *   .nameInUse(picked = null) // "build"
 * ```
 *
 * @param id what a question names the setting by in [AgentSelection.settings].
 * @param name what the setting is, as "Session Mode"; its value is [nameInUse].
 */
data class AgentSetting(
  val id: String,
  val name: String,
  override val current: String?,
  override val offered: List<AgentSettingValue>,
) : AgentChoice

/** One value an [AgentSetting] can take. */
data class AgentSettingValue(override val id: String, override val name: String) : AgentOption
