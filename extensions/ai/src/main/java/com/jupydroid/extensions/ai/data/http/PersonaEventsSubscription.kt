package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extensions.ai.data.logcat.ChatSocketLog
import com.jupydroid.extensions.ai.data.logcat.chatFailureFields
import kotlinx.coroutines.CompletableDeferred
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * The server's event bus, `/api/events/subscribe`, read for the persona lists and states on it
 * ([PersonaStateFrames]) and for the commands jupyterlab-commands-toolkit asks a web client to run
 * ([LabCommandFrames]).
 *
 * **Opened before the chat's socket, and awaited**: events are fire-and-forget, and the persona manager
 * catches a client up by publishing every persona's whole state when that client connects to the chat
 * (jupyter_ai_persona_manager 0.2.2 `persona_manager.py`, `_on_chat_event`, in the shipped rootfs), so
 * only a subscriber already there receives it.
 *
 * ```
 * val bus = PersonaEventsSubscription(client, log, chatPath).open(address, fold) { command -> answer(command) }
 * ```
 */
internal class PersonaEventsSubscription(
  private val client: OkHttpClient,
  private val log: ChatSocketLog,
  private val chatPath: String,
) {
  /**
   * The open socket, each persona list and state on it handed to [fold] and each command to [onCommand],
   * from its reader thread; null when the server refused it, which leaves the chat without its pickers and
   * the agent's notebook commands unanswered, but the chat answering.
   */
  suspend fun open(address: ServerAddress, fold: TranscriptFold, onCommand: (LabCommand) -> Unit): WebSocket? {
    val opened = CompletableDeferred<WebSocket?>()
    val request = Request.Builder()
      .url(address.baseUrl.toHttpUrl().newBuilder().addPathSegments("api/events/subscribe").build())
      .header("Authorization", "token ${address.token}")
      .build()
    client.newWebSocket(request, StateListener(opened, fold, onCommand))
    return opened.await()
  }

  private inner class StateListener(
    private val opened: CompletableDeferred<WebSocket?>,
    private val fold: TranscriptFold,
    private val onCommand: (LabCommand) -> Unit,
  ) : WebSocketListener() {
    override fun onOpen(webSocket: WebSocket, response: Response) {
      opened.complete(webSocket)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
      PersonaStateFrames.parse(text)?.let(fold::stated)
      PersonaStateFrames.parsePersonas(text)?.let(fold::listed)
      LabCommandFrames.parse(text)?.let(onCommand)
    }

    /** Refused, or dropped later: either way the pickers keep what they last said, and the chat goes on. */
    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
      log.reached("chat.events.failed", chatFailureFields(chatPath, error = t, http = response?.code))
      opened.complete(null)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
      webSocket.close(code, null)
    }
  }
}
