package com.jupydroid.extensions.ai.data.logcat

import android.util.Log
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * Where the chat's socket says how it ended.
 *
 * The module's own, because `:app`'s log is out of an extension's reach (ADR-0104). It exists because
 * a socket dropped by the Space's server once showed only "failed: null" and left nothing in logcat.
 * An interface so the adapter that reports through it stays testable on the JVM, where
 * `android.util.Log` throws.
 *
 * ```
 * adb logcat -s JupydroidChat
 * ```
 */
interface ChatSocketLog {
  /** One thing that happened; [fields] carry its values so [stage] stays one greppable constant. */
  fun reached(stage: String, fields: Map<String, String>)

  /** Discards everything, for a caller that does not want the noise. */
  object Silent : ChatSocketLog {
    override fun reached(stage: String, fields: Map<String, String>) = Unit
  }
}

/**
 * The stage and its fields as one JSON object, in the order given. Built with the serializer because
 * a close frame's reason is whatever the server said, quotes and newlines included.
 */
internal fun chatLogLine(stage: String, fields: Map<String, String>): String = JsonObject(
  linkedMapOf<String, JsonPrimitive>("stage" to JsonPrimitive(stage)).apply {
    fields.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
  },
).toString()

/**
 * What failed, as [ChatSocketLog.reached]'s fields: the throwable's class, then its message and the
 * response's code when there are any. The class comes first because OkHttp's `EOFException` for a
 * socket the server dropped has a null message.
 */
internal fun chatFailureFields(chatPath: String, error: Throwable? = null, http: Int? = null): Map<String, String> =
  buildMap {
    error?.let { put("error", it::class.simpleName.orEmpty()) }
    error?.message?.let { put("message", it) }
    http?.let { put("http", it.toString()) }
    put("chat", chatPath)
  }

/** Logcat, under one tag so the chat can be followed with a single filter. */
object LogcatChatSocketLog : ChatSocketLog {
  override fun reached(stage: String, fields: Map<String, String>) {
    Log.i(TAG, chatLogLine(stage, fields))
  }

  private const val TAG = "JupydroidChat"
}
