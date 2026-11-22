package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.SpaceServerEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request

/** The type of every body sent to the server: each route here reads `get_json_body`. */
internal val JSON_MEDIA_TYPE: MediaType = "application/json".toMediaType()

/** What the server answered: its status code and its body, read whole. */
internal data class SpaceServerAnswer(val code: Int, val body: String)

/**
 * The answer to the request [shape] makes of the entered Space's base URL, sent with its token.
 *
 * The token header is enough for a `POST`, a `PATCH` or a `DELETE`: jupyter_server skips its XSRF check
 * for a request the token authenticated (`jupyter_server/base/handlers.py`, `check_xsrf_cookie`, in the
 * shipped rootfs).
 *
 * ```
 * client.callWithToken(server, Dispatchers.IO) { base -> url(base.newBuilder().addPathSegments("api/me").build()) }
 * ```
 */
internal suspend fun OkHttpClient.callWithToken(
  server: SpaceServerEndpoint,
  ioDispatcher: CoroutineDispatcher,
  shape: Request.Builder.(HttpUrl) -> Request.Builder,
): SpaceServerAnswer {
  val address = server.address()
  val request = Request.Builder().shape(address.baseUrl.toHttpUrl()).header("Authorization", "token ${address.token}")
    .build()
  return withContext(ioDispatcher) {
    newCall(request).execute().use { SpaceServerAnswer(it.code, it.body.string()) }
  }
}

/**
 * [callWithToken]'s answer, or [failureOf]'s exception naming the failure when the request cannot
 * even be made: `address()` starts the Space's server when none runs, and what it throws is the
 * launcher's to choose; the port promises its own exception for every failure.
 */
@Suppress("TooGenericExceptionCaught")
internal suspend fun OkHttpClient.callWithTokenOr(
  server: SpaceServerEndpoint,
  ioDispatcher: CoroutineDispatcher,
  failureOf: (String) -> Exception,
  shape: Request.Builder.(HttpUrl) -> Request.Builder,
): SpaceServerAnswer = try {
  callWithToken(server, ioDispatcher, shape)
} catch (failure: Exception) {
  throw when (failure) {
    is CancellationException -> failure
    else -> failureOf("${failure::class.java.simpleName}: ${failure.message}")
  }
}
