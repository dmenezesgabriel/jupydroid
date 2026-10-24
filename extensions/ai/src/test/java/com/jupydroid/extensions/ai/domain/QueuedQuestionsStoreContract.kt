package com.jupydroid.extensions.ai.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** What every [QueuedQuestionsStore] must do, whoever implements it. */
abstract class QueuedQuestionsStoreContract {
  /** A store that holds nothing yet. */
  protected abstract fun store(): QueuedQuestionsStore

  @Test
  fun aChatNothingWasWrittenForHoldsNoQuestion() = runBlocking {
    assertEquals(emptyList<String>(), store().read(ChatFile("untitled.chat")))
  }

  @Test
  fun whatIsWrittenForAChatIsReadBackInItsOrder() = runBlocking {
    val store = store()

    store.write(ChatFile("untitled.chat"), listOf("What is 2+2?", "And 3+3?"))

    assertEquals(listOf("What is 2+2?", "And 3+3?"), store.read(ChatFile("untitled.chat")))
  }

  @Test
  fun aWriteReplacesWhatTheChatHeldBefore() = runBlocking {
    val store = store()
    store.write(ChatFile("untitled.chat"), listOf("What is 2+2?", "And 3+3?"))

    store.write(ChatFile("untitled.chat"), listOf("And 3+3?"))

    assertEquals(listOf("And 3+3?"), store.read(ChatFile("untitled.chat")))
  }

  @Test
  fun eachChatHoldsItsOwnQuestions() = runBlocking {
    val store = store()

    store.write(ChatFile("untitled.chat"), listOf("What is 2+2?"))
    store.write(ChatFile("notes.chat"), listOf("And 3+3?"))

    assertEquals(listOf("What is 2+2?"), store.read(ChatFile("untitled.chat")))
    assertEquals(listOf("And 3+3?"), store.read(ChatFile("notes.chat")))
  }

  @Test
  fun anEmptyWriteLeavesTheChatHoldingNoQuestion() = runBlocking {
    val store = store()
    store.write(ChatFile("untitled.chat"), listOf("What is 2+2?"))

    store.write(ChatFile("untitled.chat"), emptyList())

    assertEquals(emptyList<String>(), store.read(ChatFile("untitled.chat")))
  }
}
