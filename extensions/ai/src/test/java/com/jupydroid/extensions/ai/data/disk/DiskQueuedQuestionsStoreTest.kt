package com.jupydroid.extensions.ai.data.disk

import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStore
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStoreContract
import com.jupydroid.extensions.ai.domain.QueuedQuestionsStoreException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/** The contract, against a file in a directory of its own, as the app's own storage holds one per Space. */
class DiskQueuedQuestionsStoreTest : QueuedQuestionsStoreContract() {
  @get:Rule val folder = TemporaryFolder()

  private val file: File get() = File(folder.root, "chat-queues/base.json")

  override fun store(): QueuedQuestionsStore = DiskQueuedQuestionsStore(file, Dispatchers.Unconfined)

  /** What a killed process wrote is what the next one reads. */
  @Test
  fun whatOneStoreWroteAnotherOnTheSameFileReads() = runBlocking {
    store().write(ChatFile("untitled.chat"), listOf("What is 2+2?"))

    assertEquals(listOf("What is 2+2?"), store().read(ChatFile("untitled.chat")))
  }

  @Test
  fun whatOneStoreMovedAnotherOnTheSameFileReads() = runBlocking {
    store().write(ChatFile("untitled.chat"), listOf("What is 2+2?"))

    store().move(ChatFile("untitled.chat"), to = ChatFile("notes.chat"))

    assertEquals(listOf("What is 2+2?"), store().read(ChatFile("notes.chat")))
    assertEquals(emptyList<String>(), store().read(ChatFile("untitled.chat")))
  }

  @Test
  fun aFileThatIsNotTheStoresFailsToReadNamingItsPath() {
    file.parentFile!!.mkdirs()
    file.writeText("not json")

    val failure = assertThrows(QueuedQuestionsStoreException::class.java) {
      runBlocking { store().read(ChatFile("untitled.chat")) }
    }

    assertTrue(failure.message, file.path in failure.message.orEmpty())
  }

  /** So one unreadable file costs the questions it held, and not every question queued after it. */
  @Test
  fun aWriteOverAFileThatIsNotTheStoresReplacesIt() = runBlocking {
    file.parentFile!!.mkdirs()
    file.writeText("not json")

    store().write(ChatFile("untitled.chat"), listOf("What is 2+2?"))

    assertEquals(listOf("What is 2+2?"), store().read(ChatFile("untitled.chat")))
  }
}
