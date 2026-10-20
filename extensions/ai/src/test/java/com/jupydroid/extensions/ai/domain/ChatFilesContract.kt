package com.jupydroid.extensions.ai.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** What every [ChatFiles] must do, whoever implements it. */
abstract class ChatFilesContract {
  /** A workspace whose root holds the chats at [paths], and nothing else that is a chat. */
  protected abstract fun chatFiles(paths: List<String> = emptyList()): ChatFiles

  @Test
  fun listsEveryChatTheRootHolds() = runBlocking {
    val listed = chatFiles(listOf("untitled.chat", "notes.chat")).list()

    assertEquals(setOf("untitled.chat", "notes.chat"), listed.map(ChatFile::path).toSet())
  }

  @Test
  fun aRootWithNoChatListsNone() = runBlocking {
    assertEquals(emptyList<ChatFile>(), chatFiles().list())
  }
}
