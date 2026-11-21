package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.SpaceServerEndpoint
import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatFiles
import com.jupydroid.extensions.ai.domain.ChatFilesException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException

/**
 * The workspace's chats, through jupyter_server's Contents API in the entered Space, the way
 * jupyterlab-chat's own panel finds and makes them: `GET api/contents` on the root, and of what it
 * holds, the files whose name ends `.chat` (jupyter-chat@2c224bc
 * `packages/jupyterlab-chat-extension/src/index.ts`, `getChatNames`); and for a new one, jupyterlab's
 * `newUntitled` at the root (`createChat`); and to rename one, jupyterlab's `contents.rename`, a
 * `PATCH` on the chat's path with its new one in the body (`renameChat`); and to delete one, jupyterlab's
 * `contents.delete`, a `DELETE` on its path, which jupyter_server answers 204
 * (`jupyter_server/services/contents/handlers.py`, `ContentsHandler.delete`, in the shipped rootfs),
 * each sent with the token ([callWithToken]).
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
    val body = call("listing the workspace's chats", HTTP_OK) { contents ->
      get().url(contents.newBuilder().addQueryParameter("content", "1").build())
    }
    return lenient.decodeFromString<RootListing>(body).content.filter(ListedEntry::isChat).map { ChatFile(it.path) }
  }

  override suspend fun create(): ChatFile {
    val body = call("creating a chat", HTTP_CREATED) { contents ->
      url(contents).post(UNTITLED_CHAT.toRequestBody(JSON_MEDIA_TYPE))
    }
    return ChatFile(lenient.decodeFromString<ListedEntry>(body).path)
  }

  override suspend fun rename(chat: ChatFile, name: String): ChatFile {
    val moved = buildJsonObject { put("path", ChatFile.named(name).path) }.toString()
    val body = call("renaming a chat", HTTP_OK) { contents ->
      url(contents.newBuilder().addPathSegments(chat.path).build()).patch(moved.toRequestBody(JSON_MEDIA_TYPE))
    }
    return ChatFile(lenient.decodeFromString<ListedEntry>(body).path)
  }

  override suspend fun delete(chat: ChatFile) {
    call("deleting a chat", HTTP_NO_CONTENT) { contents ->
      url(contents.newBuilder().addPathSegments(chat.path).build()).delete()
    }
  }

  /**
   * The body the server answers to the request [shape] makes of the root's `api/contents` URL.
   *
   * @throws ChatFilesException naming [action] when the request cannot be made or the answer is not [expected].
   */
  private suspend fun call(action: String, expected: Int, shape: Request.Builder.(HttpUrl) -> Request.Builder): String {
    val answer = try {
      client.callWithToken(server, ioDispatcher) { base ->
        shape(base.newBuilder().addPathSegments("api/contents").build())
      }
    } catch (failure: IOException) {
      throw ChatFilesException(action, "${failure::class.java.simpleName}: ${failure.message}")
    }
    val (code, body) = answer
    if (code != expected) throw ChatFilesException(action, "http $code, ${body.take(BODY_EXCERPT)}")
    return body
  }

  private companion object {
    const val HTTP_OK = 200
    const val HTTP_CREATED = 201
    const val HTTP_NO_CONTENT = 204

    /** Enough of an unexpected body to recognise it by, and not a page pasted into a panel. */
    const val BODY_EXCERPT = 200

    /** The options jupyter-chat's `createChat` gives `newUntitled`, which jupyterlab posts as the body. */
    const val UNTITLED_CHAT = """{"type": "file", "ext": "${ChatFile.CHAT_EXTENSION}"}"""

    val lenient = Json { ignoreUnknownKeys = true }
  }
}

/** The root's model as `GET api/contents` answers it, of which only the entries are read. */
@Serializable
private data class RootListing(val content: List<ListedEntry> = emptyList())

/**
 * One entry of a directory's model, or the model `POST` and `PATCH` answer; `type` is `file`, `notebook` or
 * `directory`.
 */
@Serializable
private data class ListedEntry(val path: String, val type: String) {
  val isChat: Boolean get() = type == "file" && path.endsWith(ChatFile.CHAT_EXTENSION)
}
