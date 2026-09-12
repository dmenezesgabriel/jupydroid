package com.jupydroid.app.data.space

import com.jupydroid.app.data.space.JupyterFrame.content
import com.jupydroid.app.domain.port.DebugScope
import com.jupydroid.app.domain.port.DebugStackFrame
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.domain.port.VerifiedBreakpoint
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * A `debug_reply`'s body, or the failure it reports.
 *
 * Unlike [KernelOutputs]/[KernelCompletions], a DAP-level failure is not traffic to skip - it is
 * exactly the defect a caller needs to see, so [of] throws rather than returning null for it.
 * Correlation is still total: a frame belonging to another request or of the wrong type is null,
 * the same as every other reader here.
 *
 * ```
 * KernelDebugReplies.of(frame, requestMsgId)?.let { KernelDebugReplies.sourcePath(it) }
 * ```
 */
internal object KernelDebugReplies {
  fun of(frame: String, requestMsgId: String): JsonObject? =
    JupyterFrame.ofType(frame, requestMsgId, "debug_reply")?.content()?.let(::bodyOrThrow)

  fun sourcePath(body: JsonObject): String = body.string("sourcePath")

  /** DAP's own guarantee: the response array corresponds element-for-element to the request's. */
  fun verifiedBreakpoints(body: JsonObject): List<VerifiedBreakpoint> =
    body["breakpoints"]?.jsonArray.orEmpty().map { it.jsonObject.toVerifiedBreakpoint() }

  fun stackFrames(body: JsonObject): List<DebugStackFrame> =
    body["stackFrames"]?.jsonArray.orEmpty().map { it.jsonObject.toStackFrame() }

  fun scopes(body: JsonObject): List<DebugScope> = body["scopes"]?.jsonArray.orEmpty().map { it.jsonObject.toScope() }

  fun variables(body: JsonObject): List<DebugVariable> =
    body["variables"]?.jsonArray.orEmpty().map { it.jsonObject.toVariable() }

  private fun bodyOrThrow(content: JsonObject): JsonObject {
    if (!content.boolean("success")) {
      throw DebugRequestFailedException(content.string("command"), content["message"]?.jsonPrimitive?.content)
    }
    return content["body"]?.jsonObject ?: JsonObject(emptyMap())
  }

  private fun JsonObject.toVerifiedBreakpoint() = VerifiedBreakpoint(line = int("line"), verified = boolean("verified"))

  private fun JsonObject.toStackFrame() =
    DebugStackFrame(id = int("id"), name = string("name"), line = int("line"), column = int("column"))

  private fun JsonObject.toScope() =
    DebugScope(name = string("name"), variablesReference = int("variablesReference"), expensive = boolean("expensive"))

  private fun JsonObject.toVariable() = DebugVariable(
    name = string("name"),
    value = string("value"),
    type = get("type")?.jsonPrimitive?.content,
    variablesReference = int("variablesReference"),
  )
}

// Kept at file scope, not as object members: KernelDebugReplies was already at detekt's
// eleven-function cap (P6a-3a's own precedent for this file's neighbours), and a field reader
// shared by every DAP body shape here is not specific to the reply-body reading it happens to serve.
private fun JsonObject.string(key: String): String = get(key)?.jsonPrimitive?.content.orEmpty()

private fun JsonObject.int(key: String): Int = get(key)?.jsonPrimitive?.intOrNull ?: 0

private fun JsonObject.boolean(key: String): Boolean = get(key)?.jsonPrimitive?.booleanOrNull ?: false
