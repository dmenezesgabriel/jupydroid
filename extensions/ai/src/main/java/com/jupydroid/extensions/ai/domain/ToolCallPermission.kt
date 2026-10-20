package com.jupydroid.extensions.ai.domain

/**
 * Whether a [ToolCall] waits on the user to run. ACP lets the agent name its own options, and
 * opencode's `bash` offers three: Allow once, Always allow and Reject.
 *
 * ```
 * val allowOnce = PermissionOption(id = "once", name = "Allow once", rejects = false)
 * ToolCallPermission.Pending(sessionId = "s1", options = listOf(allowOnce))
 * ```
 */
sealed interface ToolCallPermission {
  /**
   * Waiting for one of [options] to be chosen.
   *
   * @param sessionId the agent's session, which the server finds the waiting call by.
   */
  data class Pending(val sessionId: String, val options: List<PermissionOption>) : ToolCallPermission

  /** Answered with [chosen], or with nothing when the reply was stopped while it waited. */
  data class Resolved(val chosen: PermissionOption?) : ToolCallPermission
}

/**
 * One answer the agent offers to a call it asks about.
 *
 * @param name what the agent labels it, which the row shows as is.
 * @param rejects whether choosing it refuses the call: ACP's `reject_once` and `reject_always` kinds.
 */
data class PermissionOption(val id: String, val name: String, val rejects: Boolean)

/** Choosing [option] for [call] failed; the message names both, as the person saw them. */
class ToolCallChoiceException(call: ToolCall, option: PermissionOption, reason: String) :
  AgentChatException("choosing '${option.name}' for '${call.title}' failed: $reason")
