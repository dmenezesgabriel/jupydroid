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
 * chatFiles.create().path // "untitled1.chat"
 * ```
 */
interface ChatFiles {
  /**
   * Every chat at the workspace's root, in no particular order.
   *
   * @throws ChatFilesException when the workspace cannot be read.
   */
  suspend fun list(): List<ChatFile>

  /**
   * A new, empty chat at the workspace's root, under the first untitled name not taken, as
   * jupyter-chat's New chat makes one (jupyter-chat@2c224bc
   * `packages/jupyterlab-chat-extension/src/index.ts`, `createChat`).
   *
   * @throws ChatFilesException when the file cannot be made.
   */
  suspend fun create(): ChatFile
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

/** What was asked of the workspace's chats, its [action], failed for [reason]. */
class ChatFilesException(action: String, reason: String) : Exception("$action failed: $reason")
