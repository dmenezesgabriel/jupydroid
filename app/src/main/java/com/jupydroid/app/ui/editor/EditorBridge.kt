package com.jupydroid.app.ui.editor

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** What the editor page tells the app. */
sealed interface EditorMessage {
  data object Ready : EditorMessage

  /** The page's rendered height, which the cell sizes itself to. */
  data class HeightChanged(val px: Int) : EditorMessage

  data class SourceEdited(val source: String) : EditorMessage

  /** `Mod-Enter` inside the editor. */
  data object RunRequested : EditorMessage

  data class CompletionRequested(
    val id: String,
    val source: String,
    val line: Int,
    val column: Int,
  ) : EditorMessage
}

/**
 * The protocol between a cell's editor page and the app, as pure Kotlin.
 *
 * Both directions live here so the WebView is left with nothing but plumbing, and so the part
 * that actually breaks - encoding user text into a JavaScript expression - is unit-testable.
 * The shape follows the one observed in `reference/callisto/FINDINGS.md` §2b.
 *
 * ```
 * webView.evaluateJavascript(EditorBridge.setSource(cell.source), null)
 * ```
 */
object EditorBridge {
  private val json = Json { ignoreUnknownKeys = true }

  /**
   * Null for anything unrecognised. The page is the untrusted side of this bridge - a WebView
   * can be fed anything - and one bad message must not take the editor down.
   */
  fun parse(rawMessage: String): EditorMessage? {
    val message = decode(rawMessage) ?: return null
    return when (message.string("type")) {
      "ready" -> EditorMessage.Ready
      "run" -> EditorMessage.RunRequested
      "height" -> message.int("px")?.let(EditorMessage::HeightChanged)
      "edit" -> message.string("source")?.let(EditorMessage::SourceEdited)
      "complete" -> message.toCompletionRequest()
      else -> null
    }
  }

  /**
   * Source is user text going into a JavaScript expression, so it is JSON-encoded rather than
   * concatenated. A quote or a newline in someone's code would otherwise be a syntax error,
   * and text crafted to close the call would be script.
   */
  fun setSource(source: String): String = call("setSource", JsonPrimitive(source))

  /** Design tokens the page styles itself with, so Compose and CodeMirror agree on colour. */
  fun setTheme(tokens: Map<String, String>): String =
    call("setTheme", JsonObject(tokens.mapValues { (_, value) -> JsonPrimitive(value) }))

  /**
   * The id comes back with the answer so the page can drop a reply that arrived after the
   * user typed on - a completion for text that no longer exists is worse than none.
   */
  fun completionsReady(id: String, matches: List<String>): String =
    call(
      "completionsReady",
      buildJsonObject {
        put("id", id)
        put("matches", buildJsonArray { matches.forEach { add(JsonPrimitive(it)) } })
      },
    )

  private fun call(function: String, argument: kotlinx.serialization.json.JsonElement): String =
    "window.JupydroidEditor.$function($argument)"

  /** All four fields or nothing: a completion without its cursor cannot be answered. */
  private fun JsonObject.toCompletionRequest(): EditorMessage? =
    EditorMessage.CompletionRequested(
      id = string("id") ?: return null,
      source = string("source") ?: return null,
      line = int("line") ?: return null,
      column = int("col") ?: return null,
    )

  private fun JsonObject.string(key: String): String? = get(key)?.jsonPrimitive?.contentOrNullIfNotString()

  private fun JsonObject.int(key: String): Int? = get(key)?.jsonPrimitive?.intOrNull

  // A JSON number where a string belongs is as wrong as a missing key, and silently
  // stringifying it would put "240" into a cell's source.
  private fun JsonPrimitive.contentOrNullIfNotString(): String? = if (isString) content else null

  @Suppress("SwallowedException") // an unparseable message is noise, not our failure
  private fun decode(rawMessage: String): JsonObject? =
    try {
      json.parseToJsonElement(rawMessage).jsonObject
    } catch (e: SerializationException) {
      null
    } catch (e: IllegalArgumentException) {
      null
    }
}
