package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatFiles
import com.jupydroid.extensions.ai.domain.ChatFilesContract
import com.jupydroid.extensions.ai.domain.ChatFilesException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** The contract, against a loopback server answering `GET api/contents` as jupyter_server does. */
class OkHttpChatFilesTest : ChatFilesContract() {
  @get:Rule val serverRule = MockWebServerRule()

  override fun chatFiles(paths: List<String>): ChatFiles {
    serverRule.server.enqueue(MockResponse.Builder().body(rootListing(paths.map(::fileEntry))).build())
    return filesUnderTest()
  }

  @Test
  fun list_readsTheRootsContentsWithTheToken() = runBlocking {
    chatFiles(listOf("untitled.chat")).list()

    val request = serverRule.server.takeRequest()
    assertEquals("/api/contents?content=1", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  /** jupyter-chat's own filter: a file, by its name's ending (`getChatNames`). */
  @Test
  fun list_leavesOutWhatIsNotAChatFile() = runBlocking {
    val entries = listOf(
      fileEntry("a.chat"),
      """{"name": "b.ipynb", "path": "b.ipynb", "type": "notebook"}""",
      """{"name": "c.chat", "path": "c.chat", "type": "directory"}""",
    )
    serverRule.server.enqueue(MockResponse.Builder().body(rootListing(entries)).build())

    assertEquals(listOf(ChatFile("a.chat")), filesUnderTest().list())
  }

  @Test
  fun list_failsWithTheServersCodeAndAnswer() {
    serverRule.server.enqueue(MockResponse.Builder().code(403).body("""{"message": "Forbidden"}""").build())

    val failure = assertThrows(ChatFilesException::class.java) { runBlocking { filesUnderTest().list() } }

    assertTrue(failure.message!!, failure.message!!.contains("http 403"))
    assertTrue(failure.message!!, failure.message!!.contains("Forbidden"))
  }

  private fun filesUnderTest() = OkHttpChatFiles(
    server = { ServerAddress(serverRule.server.url("/").toString(), "secret-token") },
    ioDispatcher = Dispatchers.Unconfined,
  )

  private companion object {
    fun fileEntry(path: String) = """{"name": "$path", "path": "$path", "type": "file"}"""

    fun rootListing(entries: List<String>) =
      """{"name": "", "path": "", "type": "directory", "content": [${entries.joinToString()}]}"""
  }
}
