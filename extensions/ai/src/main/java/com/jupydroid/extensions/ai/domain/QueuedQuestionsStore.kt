package com.jupydroid.extensions.ai.domain

/**
 * Where each chat's [QuestionQueue.pending] is kept, so questions still waiting when the app is
 * killed are there when the chat is opened again. VS Code saves its pending requests with the session
 * (vscode@3f3380c `src/vs/workbench/contrib/chat/common/model/chatModel.ts`,
 * `ISerializableChatData.pendingRequests`).
 *
 * `DiskQueuedQuestionsStore` keeps them in the app's own storage, one file per Space;
 * `FakeQueuedQuestionsStore` in memory.
 *
 * ```
 * store.write(ChatFile("untitled.chat"), listOf("What is 2+2?"))
 * store.read(ChatFile("untitled.chat")) // ["What is 2+2?"]
 * ```
 */
interface QueuedQuestionsStore {
  /**
   * The questions [chat] has waiting, oldest first; none if nothing was written for it.
   *
   * @throws QueuedQuestionsStoreException when what is kept cannot be read.
   */
  suspend fun read(chat: ChatFile): List<String>

  /**
   * [pending] as all the questions [chat] has waiting, replacing what it held; none leaves it holding none.
   *
   * @throws QueuedQuestionsStoreException when they cannot be kept.
   */
  suspend fun write(chat: ChatFile, pending: List<String>)
}

/**
 * What [chat] has waiting, or none when what is kept cannot be read. The next write replaces it, so an
 * unreadable file costs the questions in it rather than the chat.
 *
 * ```
 * QuestionQueue(pending = store.readOrNone(ChatFile("untitled.chat")))
 * ```
 */
suspend fun QueuedQuestionsStore.readOrNone(chat: ChatFile): List<String> = try {
  read(chat)
} catch (_: QueuedQuestionsStoreException) {
  emptyList()
}

/** What was asked of the kept questions, its [action], failed for [reason]. */
class QueuedQuestionsStoreException(action: String, reason: String, cause: Throwable? = null) :
  Exception("$action failed: $reason", cause)
