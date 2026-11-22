package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extensions.ai.data.http.FakeContentsRoot.Companion.fileEntry
import com.jupydroid.extensions.ai.data.http.FakeContentsRoot.Companion.rootListing
import com.jupydroid.extensions.ai.domain.ChatFile
import com.jupydroid.extensions.ai.domain.ChatFiles
import com.jupydroid.extensions.ai.domain.ChatFilesContract
import com.jupydroid.extensions.ai.domain.ChatFilesException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import mockwebserver3.MockResponse
import mockwebserver3.SocketEffect
import mockwebserver3.junit4.MockWebServerRule
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** The contract, against a loopback server answering `api/contents` as jupyter_server does. */
class OkHttpChatFilesTest : ChatFilesContract() {
  @get:Rule val serverRule = MockWebServerRule()

  override fun chatFiles(paths: List<String>): ChatFiles {
    serverRule.server.dispatcher = FakeContentsRoot(paths)
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

  /** A server that takes the request and never answers runs into the client's read timeout. */
  @Test
  fun list_ofAServerThatNeverAnswersThrowsAChatFilesExceptionNamingTheAction() {
    serverRule.server.enqueue(MockResponse.Builder().onResponseStart(SocketEffect.Stall).build())
    val impatient = OkHttpClient.Builder().readTimeout(SILENT_SERVER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS).build()

    val failure = assertThrows(ChatFilesException::class.java) { runBlocking { filesUnderTest(impatient).list() } }

    assertTrue(failure.message!!, failure.message!!.contains("listing the workspace's chats"))
    assertTrue(failure.message!!, failure.message!!.contains("SocketTimeoutException"))
  }

  /** jupyterlab's `newUntitled`, as jupyter-chat's `createChat` calls it at the root. */
  @Test
  fun create_postsAnUntitledChatFileWithTheToken() = runBlocking {
    chatFiles().create()

    val request = serverRule.server.takeRequest()
    assertEquals("POST", request.method)
    assertEquals("/api/contents", request.target)
    assertEquals(
      Json.parseToJsonElement("""{"type": "file", "ext": ".chat"}"""),
      Json.parseToJsonElement(request.body!!.utf8()),
    )
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  @Test
  fun create_failsWithTheServersCodeAndAnswer() {
    serverRule.server.enqueue(MockResponse.Builder().code(409).body("""{"message": "File exists"}""").build())

    val failure = assertThrows(ChatFilesException::class.java) { runBlocking { filesUnderTest().create() } }

    assertTrue(failure.message!!, failure.message!!.contains("http 409"))
    assertTrue(failure.message!!, failure.message!!.contains("File exists"))
  }

  /** jupyterlab's `contents.rename`, which jupyter-chat's `renameChat` calls: the new path in the body. */
  @Test
  fun rename_patchesTheChatsPathWithItsNewOneAndTheToken() = runBlocking {
    chatFiles(listOf("untitled1.chat")).rename(ChatFile("untitled1.chat"), "notes")

    val request = serverRule.server.takeRequest()
    assertEquals("PATCH", request.method)
    assertEquals("/api/contents/untitled1.chat", request.target)
    assertEquals(
      Json.parseToJsonElement("""{"path": "notes.chat"}"""),
      Json.parseToJsonElement(request.body!!.utf8()),
    )
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  /** A name is a path segment, not a path: its space is escaped, and a quote in the body is too. */
  @Test
  fun rename_escapesTheOldPathInTheUrlAndTheNewOneInTheBody() = runBlocking {
    chatFiles(listOf("my chat.chat")).rename(ChatFile("my chat.chat"), "say \"hi\"")

    val request = serverRule.server.takeRequest()
    assertEquals("/api/contents/my%20chat.chat", request.target)
    assertEquals(
      Json.parseToJsonElement("""{"path": "say \"hi\".chat"}"""),
      Json.parseToJsonElement(request.body!!.utf8()),
    )
  }

  @Test
  fun rename_failsWithTheServersCodeAndAnswer() {
    serverRule.server.enqueue(
      MockResponse.Builder().code(409).body("""{"message": "File already exists: notes.chat"}""").build(),
    )

    val failure = assertThrows(ChatFilesException::class.java) {
      runBlocking { filesUnderTest().rename(ChatFile("untitled.chat"), "notes") }
    }

    assertTrue(failure.message!!, failure.message!!.contains("http 409"))
    assertTrue(failure.message!!, failure.message!!.contains("File already exists"))
  }

  /** jupyterlab's `contents.delete`: the chat's path, escaped as a segment, and no body. */
  @Test
  fun delete_deletesTheChatsPathWithTheToken() = runBlocking {
    chatFiles(listOf("my chat.chat")).delete(ChatFile("my chat.chat"))

    val request = serverRule.server.takeRequest()
    assertEquals("DELETE", request.method)
    assertEquals("/api/contents/my%20chat.chat", request.target)
    assertEquals("token secret-token", request.headers["Authorization"])
  }

  @Test
  fun delete_failsWithTheServersCodeAndAnswer() {
    serverRule.server.enqueue(
      MockResponse.Builder().code(
        404,
      ).body("""{"message": "file or directory does not exist: 'notes.chat'"}""").build(),
    )

    val failure = assertThrows(ChatFilesException::class.java) {
      runBlocking { filesUnderTest().delete(ChatFile("notes.chat")) }
    }

    assertTrue(failure.message!!, failure.message!!.contains("http 404"))
    assertTrue(failure.message!!, failure.message!!.contains("does not exist"))
  }

  /**
   * `address()` starts the Space's server when none runs, and its failure's type is the launcher's
   * to choose: the port promises `ChatFilesException` for every failure, and the list catches
   * nothing else.
   */
  @Test
  fun list_ofAServerThatCannotBeAskedThrowsAChatFilesExceptionNamingTheAction() {
    val files = OkHttpChatFiles(server = { throw IllegalStateException("the Space's server failed to come up") })

    val failure = assertThrows(ChatFilesException::class.java) { runBlocking { files.list() } }

    assertTrue(failure.message!!, failure.message!!.contains("listing the workspace's chats"))
    assertTrue(failure.message!!, failure.message!!.contains("IllegalStateException"))
  }

  /** An address `toHttpUrl` refuses is a failed read like any other, not an escaped `IllegalArgumentException`. */
  @Test
  fun list_ofAnAddressThatIsNotAnHttpUrlThrowsAChatFilesExceptionNamingTheAction() {
    val files = OkHttpChatFiles(server = { ServerAddress(baseUrl = "not a url", token = "secret-token") })

    val failure = assertThrows(ChatFilesException::class.java) { runBlocking { files.list() } }

    assertTrue(failure.message!!, failure.message!!.contains("listing the workspace's chats"))
  }

  private fun filesUnderTest(client: OkHttpClient = OkHttpClient()): ChatFiles = OkHttpChatFiles(
    server = { ServerAddress(serverRule.server.url("/").toString(), "secret-token") },
    client = client,
    ioDispatcher = Dispatchers.Unconfined,
  )

  private companion object {
    const val SILENT_SERVER_TIMEOUT_MILLIS = 100L
  }
}
