package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extensions.ai.data.logcat.ChatSocketLog
import com.jupydroid.extensions.ai.data.logcat.chatFailureFields
import kotlinx.coroutines.delay
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration

/**
 * Keeps a Space's `jupyter_server` from stopping itself while a chat is open, by touching `api/me`.
 *
 * The server stops once no kernel exists, no ExtensionApp reports `current_activity()`, and no
 * tracked API request has come for its idle timeout (jupyter_server 2.20.0 `serverapp.py`
 * `shutdown_no_activity`). jupyterlab_chat 0.25.2 is not an ExtensionApp, and its socket's frames
 * are no API request, so an open chat counts for nothing. `api/me` is `IdentityHandler`, which is
 * tracked where `api/status` is not (`base/handlers.py` `update_api_activity`). It is also what
 * JupyterLab@7f0e690 polls (`packages/services/src/user/manager.ts`).
 *
 * ```
 * launch { ApiActivityHeartbeat(client, 30.seconds, log, "untitled.chat").beat(address) }
 * ```
 */
internal class ApiActivityHeartbeat(
  private val client: OkHttpClient,
  private val interval: Duration,
  private val log: ChatSocketLog,
  private val chatPath: String,
) {
  /** Touches `api/me` on [address] every [interval], until the caller's coroutine is cancelled. */
  suspend fun beat(address: ServerAddress): Nothing {
    val touch = Request.Builder()
      .url(address.baseUrl.toHttpUrl().newBuilder().addPathSegments("api/me").build())
      .header("Authorization", "token ${address.token}")
      .build()
    while (true) {
      delay(interval)
      client.newCall(touch).enqueue(LoggedTouch())
    }
  }

  /** A refused touch is logged and not retried early: the next beat is the retry. */
  private inner class LoggedTouch : Callback {
    override fun onResponse(call: Call, response: Response) = response.use {
      if (!it.isSuccessful) log.reached(FAILED, chatFailureFields(chatPath, http = it.code))
    }

    override fun onFailure(call: Call, e: IOException) {
      log.reached(FAILED, chatFailureFields(chatPath, error = e))
    }
  }

  private companion object {
    const val FAILED = "chat.heartbeat.failed"
  }
}
