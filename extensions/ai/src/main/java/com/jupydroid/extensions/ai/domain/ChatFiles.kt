package com.jupydroid.extensions.ai.domain

/**
 * The workspace's chats: the `.chat` files at its root, which is where jupyterlab-chat keeps them
 * unless told otherwise (`defaultDirectory`, jupyter-chat@2c224bc
 * `packages/jupyterlab-chat-extension/src/index.ts`, `getChatNames`).
 *
 * `OkHttpChatFiles` reads them through jupyter_server's Contents API; `FakeChatFiles` from a list.
 *
 * ```
 * chatFiles.list().byName().map { it.name } // ["notes", "untitled"]
 * ```
 */
interface ChatFiles {
  /**
   * Every chat at the workspace's root, in no particular order.
   *
   * @throws ChatFilesException when the workspace cannot be read.
   */
  suspend fun list(): List<ChatFile>
}

/**
 * One chat, by its file.
 *
 * @param path relative to the workspace root, as the Contents API and the chat's socket take it.
 */
data class ChatFile(val path: String) {
  /** What the list shows: the file's name without `.chat`, as jupyter-chat lists it (`getChatNames`). */
  val name: String get() = path.substringAfterLast('/').removeSuffix(CHAT_EXTENSION)

  companion object {
    const val CHAT_EXTENSION = ".chat"
  }
}

/**
 * These chats in the order a list shows them: by name, whatever their case, as jupyter-chat sorts by
 * `localeCompare` (jupyter-chat@2c224bc `packages/jupyter-chat/src/widgets/placeholder.tsx`).
 */
fun List<ChatFile>.byName(): List<ChatFile> = sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER, ChatFile::name))

/** The workspace's chats could not be read, for [reason]. */
class ChatFilesException(reason: String) : Exception("listing the workspace's chats failed: $reason")
