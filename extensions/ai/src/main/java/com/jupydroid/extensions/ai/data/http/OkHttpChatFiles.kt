package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.SpaceServerEndpoint
import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatFiles
import com.jupydroid.extensions.ai.domain.ChatFilesException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * The workspace's chats, through jupyter_server's Contents API in the entered Space, the way
 * jupyterlab-chat's own panel finds them: `GET api/contents` on the root, and of what it holds, the
 * files whose name ends `.chat` (jupyter-chat@2c224bc `packages/jupyterlab-chat-extension/src/index.ts`,
 * `getChatNames`).
 *
 * ```
 * OkHttpChatFiles(host.server!!).list()
 * ```
 */
class OkHttpChatFiles(
  private val server: SpaceServerEndpoint,
  private val client: OkHttpClient = OkHttpClient(),
  private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ChatFiles {
  override suspend fun list(): List<ChatFile> {
    val address = server.address()
    val request = Request.Builder()
      .url(rootUrl(address.baseUrl))
      .header("Authorization", "token ${address.token}")
      .build()
    val (code, body) = withContext(ioDispatcher) {
      client.newCall(request).execute().use { it.code to it.body.string() }
    }
    if (code != HTTP_OK) throw ChatFilesException("http $code, ${body.take(BODY_EXCERPT)}")
    return lenient.decodeFromString<RootListing>(body).content.filter(ListedEntry::isChat).map { ChatFile(it.path) }
  }

  private fun rootUrl(baseUrl: String): HttpUrl = baseUrl.toHttpUrl().newBuilder()
    .addPathSegments("api/contents")
    .addQueryParameter("content", "1")
    .build()

  private companion object {
    const val HTTP_OK = 200

    /** Enough of an unexpected body to recognise it by, and not a page pasted into a panel. */
    const val BODY_EXCERPT = 200

    val lenient = Json { ignoreUnknownKeys = true }
  }
}

/** The root's model as `GET api/contents` answers it, of which only the entries are read. */
@Serializable
private data class RootListing(val content: List<ListedEntry> = emptyList())

/** One entry of a directory's model; `type` is `file`, `notebook` or `directory`. */
@Serializable
private data class ListedEntry(val path: String, val type: String) {
  val isChat: Boolean get() = type == "file" && path.endsWith(ChatFile.CHAT_EXTENSION)
}
