package com.jupydroid.extensions.git.data.http

import com.jupydroid.extension.api.SpaceServerEndpoint
import com.jupydroid.extensions.git.domain.GitStatusReader
import com.jupydroid.extensions.git.domain.RepositoryStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * `POST git/<path>/status` on jupyterlab-git's handler in the entered Space's `jupyter_server`.
 *
 * **Token auth is enough for a `POST`.** `check_xsrf_cookie` returns early for a token-authenticated
 * request (`jupyter_server/base/handlers.py`), so the `Authorization` header the app's other REST
 * clients send is the whole of it (`app/src/main/java/com/jupydroid/app/data/space/OkHttpNotebookExporter.kt`).
 *
 * ```
 * OkHttpGitStatusReader(host.server!!).status("demo")
 * ```
 */
class OkHttpGitStatusReader(
  private val server: SpaceServerEndpoint,
  private val client: OkHttpClient = OkHttpClient(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : GitStatusReader {
  override suspend fun status(path: String): RepositoryStatus {
    val address = server.address()
    val request = Request.Builder()
      .url(statusUrl(address.baseUrl, path))
      .header("Authorization", "token ${address.token}")
      // `GitStatusHandler.post` reads no body; OkHttp needs one for a POST all the same.
      .post(ByteArray(0).toRequestBody())
      .build()
    val body = withContext(ioDispatcher) {
      client.newCall(request).execute().use { it.body.string() }
    }
    // The 500 a failure comes with carries git's own message, which is what tells "not a repository"
    // from a real failure, so the code is not checked here: the body decides.
    return GitStatusAnswer.parse(path, body)
  }

  /** `git/<path>/status`, segments encoded and slashes kept; the root adds no segment (`git/status`). */
  private fun statusUrl(baseUrl: String, path: String): HttpUrl = baseUrl.toHttpUrl().newBuilder()
    .addPathSegment("git")
    .apply { path.trim('/').takeIf { it.isNotEmpty() }?.let(::addPathSegments) }
    .addPathSegment("status")
    .build()
}
