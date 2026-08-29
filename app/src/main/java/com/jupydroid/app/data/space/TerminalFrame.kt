package com.jupydroid.app.data.space

import com.jupydroid.app.domain.port.TerminalEvent
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * Decoding for terminal WebSocket frames: `[type, …content]`, one JSON array per message.
 *
 * Flat where [JupyterFrame] has to correlate, and that is the protocol's doing rather than a
 * simplification: a terminal's socket serves exactly one pty, so there is no parent message to
 * match and nothing on the wire that belongs to somebody else.
 *
 * Total, for [JupyterFrame]'s reason - one malformed frame must not take a shell down - and
 * forward-compatible for one of its own: `stdin` and `set_size` are defined on this wire and only
 * ever travel the other way, so a type this app does not act on is ignored rather than fatal.
 *
 * ```
 * TerminalFrame.read("""["stdout", "hello"]""")   // Output("hello")
 * ```
 */
internal object TerminalFrame {
  private val json = Json { ignoreUnknownKeys = true }

  /** The event this frame carries, or null when it is one this app does not act on. */
  fun read(frame: String): TerminalEvent? {
    val parts = decode(frame) ?: return null
    return when (parts.textAt(0)) {
      SETUP -> TerminalEvent.Ready
      STDOUT -> parts.textAt(1)?.let(TerminalEvent::Output)
      DISCONNECT -> TerminalEvent.Ended(SHELL_EXITED)
      else -> null
    }
  }

  private fun decode(frame: String): JsonArray? =
    runCatching { json.parseToJsonElement(frame) as? JsonArray }.getOrElse { failure ->
      if (failure is SerializationException) null else throw failure
    }

  private fun JsonArray.textAt(index: Int): String? =
    getOrNull(index)?.let { runCatching { it.jsonPrimitive.content }.getOrNull() }

  private const val SETUP = "setup"
  private const val STDOUT = "stdout"
  private const val DISCONNECT = "disconnect"

  /**
   * `on_pty_died` is the only sender of `disconnect`, so the reason is known without reading the
   * payload - which is the constant `1` and carries nothing.
   */
  private const val SHELL_EXITED = "the shell exited"
}

/**
 * The messages this app sends over a terminal WebSocket.
 *
 * Reading is [TerminalFrame]'s job; this is the half terminado's `on_message` dispatches on.
 */
internal object TerminalMessage {
  /** `["stdin", text]` - the bytes reach `ptyproc.write` untouched. */
  fun stdin(text: String): String = buildJsonArray {
    add(STDIN)
    add(text)
  }.toString()

  /**
   * `["set_size", rows, cols]`, rows first.
   *
   * The order is the wire's rather than a preference: terminado takes `self.size = command[1:3]`
   * and hands it to `resize_to_smallest`, so a swap here is a shell that wraps at the wrong column
   * and reports the wrong `$LINES`.
   */
  fun setSize(rows: Int, cols: Int): String = buildJsonArray {
    add(SET_SIZE)
    add(rows)
    add(cols)
  }.toString()

  private const val STDIN = "stdin"
  private const val SET_SIZE = "set_size"
}
