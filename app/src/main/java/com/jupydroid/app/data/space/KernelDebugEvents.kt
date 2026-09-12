package com.jupydroid.app.data.space

import com.jupydroid.app.data.space.JupyterFrame.content
import com.jupydroid.app.data.space.JupyterFrame.msgType
import com.jupydroid.app.domain.port.DebugEvent
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Reads a `debug_event` off iopub - the one frame this app correlates by `msg_type` alone.
 *
 * `debug_event` carries no `parent_header`: it is not a reply to anything, confirmed against
 * JupyterLab's own `DebuggerSession._handleEvent` (`session.ts:266`), which filters the same way.
 * Phone scope keeps only the two events a breakpoint/step flow needs - anything else is null, the
 * same total shape [KernelOutputs.outputFrom] uses for a message this app does not model.
 *
 * ```
 * KernelDebugEvents.eventOf(frame)?.let { emit(it) }
 * ```
 */
internal object KernelDebugEvents {
  fun eventOf(frame: String): DebugEvent? =
    JupyterFrame.any(frame)?.takeIf { it.msgType() == "debug_event" }?.content()?.let { content ->
      when (content["event"]?.jsonPrimitive?.content) {
        "stopped" -> DebugEvent.Stopped(threadId = content.threadId())
        "continued" -> DebugEvent.Continued
        else -> null
      }
    }

  private fun JsonObject.threadId(): Int = get("body")?.jsonObject?.get("threadId")?.jsonPrimitive?.intOrNull ?: 0
}
