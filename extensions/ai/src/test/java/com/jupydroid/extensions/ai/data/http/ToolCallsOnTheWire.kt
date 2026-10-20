package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extensions.ai.domain.PermissionOption
import com.jupydroid.extensions.ai.domain.ToolCall
import com.jupydroid.extensions.ai.domain.ToolCallPermission
import com.jupydroid.extensions.ai.domain.ToolCallStatus
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A message's metadata holding [calls] as jupyter-ai's ACP client writes it: `{"tool_calls": [...]}`,
 * each a `ToolCallState` dumped by field name with `exclude_none` (jupyter_ai_acp_client 0.3.3
 * `tool_call_manager.py`, `tool_call_renderer.py`). A resolved call keeps only the option chosen,
 * which is all the panel reads of it.
 *
 * ```
 * toolCallsOnTheWire(listOf(ToolCall("c", "ls", ToolCallStatus.Completed))) // {"tool_calls": [...]}
 * ```
 */
fun toolCallsOnTheWire(calls: List<ToolCall>): JsonObject = buildJsonObject {
  put("tool_calls", buildJsonArray { calls.forEach { add(callOnTheWire(it)) } })
}

private fun callOnTheWire(call: ToolCall): JsonObject = buildJsonObject {
  put("tool_call_id", call.id)
  put("title", call.title)
  put("status", call.status.onTheWire)
  when (val permission = call.permission) {
    null -> Unit
    is ToolCallPermission.Pending -> {
      put("permission_status", "pending")
      put("session_id", permission.sessionId)
      put("permission_options", optionsOnTheWire(permission.options))
    }
    is ToolCallPermission.Resolved -> {
      put("permission_status", "resolved")
      permission.chosen?.let { put("selected_option_id", it.id) }
      put("permission_options", optionsOnTheWire(listOfNotNull(permission.chosen)))
    }
  }
}

private fun optionsOnTheWire(options: List<PermissionOption>) = buildJsonArray {
  options.forEach { option ->
    add(
      buildJsonObject {
        put("option_id", option.id)
        put("name", option.name)
        put("kind", JsonPrimitive(if (option.rejects) "reject_once" else "allow_once"))
      },
    )
  }
}

private val ToolCallStatus.onTheWire: String
  get() = when (this) {
    ToolCallStatus.Running -> "in_progress"
    ToolCallStatus.Completed -> "completed"
    ToolCallStatus.Failed -> "failed"
  }
