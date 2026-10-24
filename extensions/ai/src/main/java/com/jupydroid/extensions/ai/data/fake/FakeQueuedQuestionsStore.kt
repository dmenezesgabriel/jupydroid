package com.jupydroid.extensions.ai.data.fake

import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStore
import java.util.concurrent.ConcurrentHashMap

/**
 * Each chat's waiting questions, held for as long as this object is.
 *
 * ```
 * FakeQueuedQuestionsStore().read(ChatFile("untitled.chat")) // []
 * ```
 */
class FakeQueuedQuestionsStore : QueuedQuestionsStore {
  // Concurrent, since the panel writes from the main thread while a test reads from its own.
  private val held = ConcurrentHashMap<String, List<String>>()

  override suspend fun read(chat: ChatFile): List<String> = held[chat.path].orEmpty()

  override suspend fun write(chat: ChatFile, pending: List<String>) {
    if (pending.isEmpty()) held.remove(chat.path) else held[chat.path] = pending.toList()
  }
}
