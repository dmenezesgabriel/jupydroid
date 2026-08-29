package com.jupydroid.app.data.space

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonPrimitive

/**
 * A [TerminalSocketOpener] that answers what is written to it with scripted frames.
 *
 * The [ScriptedKernelSocketOpener] analogue, and simpler for the protocol's reason: a terminal
 * socket carries no `msg_id`, so a script has nothing to address its frames to and [reply] is a
 * plain function of the text that was typed.
 *
 * **It reads and writes frames with `kotlinx.serialization` directly, never through
 * [TerminalFrame] or [TerminalMessage].** A double that encoded with the code under test could not
 * tell a wrong encoding from a reader that made the matching mistake; the literal wire shapes are
 * asserted in [TerminalFrameTest].
 *
 * ```
 * ScriptedTerminalSocketOpener(reply = { typed -> listOf(TerminalFrames.stdout(typed)) })
 * ```
 */
class ScriptedTerminalSocketOpener(
  /** Called with the text of each `stdin` frame; whatever it returns is delivered as if from the pty. */
  private val reply: (typed: String) -> List<String> = { emptyList() },
  /**
   * Whether `["setup", {}]` is delivered as soon as the socket opens.
   *
   * False is the case worth driving: the server sends `setup` from `TermSocket.open`, a round trip
   * after `newWebSocket` has already returned, so anything written in that window reaches a socket
   * the server has not associated. Call [finishSetup] to release it.
   */
  private val setUpImmediately: Boolean = true,
) : TerminalSocketOpener {
  var openedName: String? = null
    private set

  var openCount = 0
    private set

  var closed = false
    private set

  /** Every frame written, verbatim, so a test can assert the wire and not merely the effect. */
  val sent = mutableListOf<String>()

  private var events: ((JupyterSocketEvent) -> Unit)? = null

  /** Delivers `["setup", {}]`, the way `TermSocket.open` does once the handshake has completed. */
  fun finishSetup() {
    events?.invoke(JupyterSocketEvent.Received(TerminalFrames.SETUP))
  }

  /** Ends the socket without it ever being set up: a refused upgrade, a 404 for a stale name. */
  fun failHandshake(reason: String) {
    events?.invoke(JupyterSocketEvent.Ended(reason = reason, failed = true))
  }

  /** Delivers a frame nobody asked for, the way a shell printing on its own does. */
  fun push(frame: String) {
    events?.invoke(JupyterSocketEvent.Received(frame))
  }

  override fun open(name: String, onEvent: (JupyterSocketEvent) -> Unit): JupyterSocket {
    openedName = name
    openCount++
    events = onEvent
    onEvent(JupyterSocketEvent.Opened)
    if (setUpImmediately) finishSetup()
    return ScriptedSocket(onEvent)
  }

  private inner class ScriptedSocket(private val onEvent: (JupyterSocketEvent) -> Unit) : JupyterSocket {
    override fun send(frame: String): Boolean {
      sent += frame
      TerminalFrames.typedIn(frame)?.let { typed ->
        reply(typed).forEach { onEvent(JupyterSocketEvent.Received(it)) }
      }
      return true
    }

    override fun close() {
      closed = true
    }
  }
}

/** Frames a terminal socket carries, in the shapes `terminado/websocket.py` writes them. */
object TerminalFrames {
  const val SETUP = """["setup", {}]"""

  /** `on_pty_died`'s farewell; the payload is the constant 1 and carries nothing. */
  const val DISCONNECT = """["disconnect", 1]"""

  fun stdout(text: String): String = buildJsonArray {
    add("stdout")
    add(text)
  }.toString()

  /** The text of a `["stdin", text]` frame, or null for anything else this app writes. */
  fun typedIn(frame: String): String? {
    val parts = runCatching { Json.parseToJsonElement(frame) as? JsonArray }.getOrNull()
    return parts?.takeIf { it.getOrNull(0)?.jsonPrimitive?.content == "stdin" }
      ?.getOrNull(1)?.jsonPrimitive?.content
  }
}
