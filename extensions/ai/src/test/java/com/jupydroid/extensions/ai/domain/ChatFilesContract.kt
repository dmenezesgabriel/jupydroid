package com.jupydroid.extensions.ai.domain

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
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

  @Test
  fun aNewChatIsAChatFileAtTheRootThatTheListThenHolds() = runBlocking {
    val files = chatFiles(listOf("notes.chat"))

    val created = files.create()

    assertTrue(created.path, created.path.endsWith(ChatFile.CHAT_EXTENSION) && '/' !in created.path)
    assertEquals(setOf("notes.chat", created.path), files.list().map(ChatFile::path).toSet())
  }

  @Test
  fun twoNewChatsAreTwoFilesNeitherOfThemOneTheRootHeld() = runBlocking {
    val files = chatFiles(listOf("untitled.chat"))

    val created = listOf(files.create().path, files.create().path)

    assertEquals(2, created.toSet().size)
    assertFalse(created.toString(), "untitled.chat" in created)
  }

  @Test
  fun aRenamedChatIsListedUnderItsNewNameAndNotItsOld() = runBlocking {
    val files = chatFiles(listOf("untitled.chat", "untitled1.chat"))

    val renamed = files.rename(ChatFile("untitled1.chat"), "notes")

    assertEquals(ChatFile("notes.chat"), renamed)
    assertEquals(setOf("untitled.chat", "notes.chat"), files.list().map(ChatFile::path).toSet())
  }

  /** jupyter_server answers 409, "File already exists", rather than write over the other chat. */
  @Test
  fun renamingOntoAChatThatExistsFailsAndKeepsBoth() = runBlocking {
    val files = chatFiles(listOf("untitled.chat", "notes.chat"))

    assertThrows(ChatFilesException::class.java) { runBlocking { files.rename(ChatFile("untitled.chat"), "notes") } }

    assertEquals(setOf("untitled.chat", "notes.chat"), files.list().map(ChatFile::path).toSet())
  }
}
