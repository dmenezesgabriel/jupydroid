package com.jupydroid.extensions.ai.domain

import com.jupydroid.extensions.ai.data.fake.FakeQueuedQuestionsStore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class QueuedQuestionsReadTest {
  @Test
  fun aChatsKeptQuestionsAreReadAsKept() = runBlocking {
    val store = FakeQueuedQuestionsStore()
    store.write(ChatFile("untitled.chat"), listOf("What is 2+2?"))

    assertEquals(listOf("What is 2+2?"), store.readOrNone(ChatFile("untitled.chat")))
  }

  @Test
  fun whatCannotBeReadIsReadAsNoQuestion() = runBlocking {
    assertEquals(emptyList<String>(), UnreadableQueuedQuestionsStore().readOrNone(ChatFile("untitled.chat")))
  }
}

/** A store whose file is not one it wrote, as after a write the filesystem cut short. */
private class UnreadableQueuedQuestionsStore : QueuedQuestionsStore {
  override suspend fun read(chat: ChatFile): List<String> =
    throw QueuedQuestionsStoreException("reading ${chat.path}'s queued questions", "not json")

  override suspend fun write(chat: ChatFile, pending: List<String>) = Unit
}
