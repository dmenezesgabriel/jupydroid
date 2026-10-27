package com.jupydroid.extensions.ai.data.http

import com.jupydroid.extension.api.ServerAddress
import com.jupydroid.extensions.ai.domain.AgentChat
import com.jupydroid.extensions.ai.domain.AgentModels
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import mockwebserver3.junit4.MockWebServerRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * [OkHttpAgentChat]'s events socket as jupyterlab-commands-toolkit@7c87c5d's web client (`src/index.ts`,
 * `handleCommand`): ack a command, run it, post its result. The server keeps the first result it is sent
 * (`tools.py`, `handle_command_result`), so a second web client that runs the command runs it twice.
 */
class OkHttpAgentChatLabCommandsTest {
  @get:Rule val serverRule = MockWebServerRule()

  private val events = PersonaEventsLikeTheTablets(AgentModels())
  private val notebook = InMemoryNotebookCells("untitled.ipynb", listOf("a" to "x = 1"), activeCellId = "a")
  private val webClient = LabCommandWebClient(id = "this-app")

  @Test
  fun aCommandIsAckedThenRunAndItsResultPosted() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(events = events)

    withOpenChats(chatUnderTest()) {
      events.command(ADD_CELL, addCellAbove("a"), requestId = "r1")
      awaitResult("r1")
    }

    assertEquals(listOf("ack r1", "result r1"), labEvents().map { "${it.kind} ${it.requestId}" })
    val ack = labEvents().first()
    assertEquals("this-app", ack.data.getValue("client_id").jsonPrimitive.content)
    assertEquals(true, labEvents().last().data.getValue("success").jsonPrimitive.boolean)
    assertEquals(listOf("print(42)", "x = 1"), notebook.sources)
  }

  @Test
  fun twoOpenChatsRunACommandOnce() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(events = events)

    withOpenChats(chatUnderTest(), chatUnderTest()) {
      events.command(ADD_CELL, addCellAbove("a"), requestId = "r1")
      awaitResult("r1")
      // A second command's round trip is time enough for the other socket to have read the first.
      events.command(ADD_CELL, addCellAbove("a"), requestId = "r2")
      awaitResult("r2")
    }

    assertEquals(2, notebook.inserts)
    assertEquals(1, labEvents().count { it.kind == "ack" && it.requestId == "r1" })
  }

  @Test
  fun aCommandForAnotherWebClientIsNotRun() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(events = events)

    withOpenChats(chatUnderTest()) {
      events.command(ADD_CELL, addCellAbove("a"), requestId = "r1", clientId = "a-browser-tab")
      events.command(ADD_CELL, addCellAbove("a"), requestId = "r2")
      awaitResult("r2")
    }

    assertEquals(listOf("r2", "r2"), labEvents().map { it.requestId })
    assertEquals(1, notebook.inserts)
  }

  /** Acked first, so the server waits for the error rather than for `ack_timeout`. */
  @Test
  fun aCommandThisAppDoesNotAnswerIsAckedAndFailsByItsName() = runBlocking {
    serverRule.server.dispatcher = ChatLikeTheTabletsServer(events = events)

    withOpenChats(chatUnderTest()) {
      events.command("jupyterlab-ai-commands:run-cell", JsonObject(emptyMap()), requestId = "r1")
      awaitResult("r1")
    }

    val (ack, result) = labEvents()
    assertEquals("ack", ack.kind)
    assertEquals(false, result.data.getValue("success").jsonPrimitive.boolean)
    val error = result.data.getValue("error").jsonPrimitive.content
    assertTrue(error, "jupyterlab-ai-commands:run-cell" in error)
  }

  private suspend fun withOpenChats(vararg chats: AgentChat, check: suspend CoroutineScope.() -> Unit) =
    withTimeout(TIMEOUT_MILLIS) {
      // Each chat's events socket is open once its first transcript is out: `transcript` awaits it first.
      val opened = chats.map { CompletableDeferred<Unit>() }
      val collecting = chats.zip(opened) { chat, open -> launch { chat.transcript().collect { open.complete(Unit) } } }
      opened.awaitAll()
      try {
        check()
      } finally {
        collecting.forEach { it.cancel() }
      }
    }

  private suspend fun awaitResult(requestId: String) {
    while (labEvents().none { it.kind == "result" && it.requestId == requestId }) delay(POLL_MILLIS)
  }

  /** The commands toolkit's events among everything posted, the persona's selection left out. */
  private fun labEvents(): List<PostedLabEvent> = events.emitted.mapNotNull { request ->
    val event = Json.parseToJsonElement(request.body!!.utf8()).jsonObject
    val kind = KINDS[event.getValue("schema_id").jsonPrimitive.content] ?: return@mapNotNull null
    val data = event.getValue("data").jsonObject
    PostedLabEvent(kind, data.getValue("requestId").jsonPrimitive.content, data)
  }

  private class PostedLabEvent(val kind: String, val requestId: String, val data: JsonObject)

  private fun addCellAbove(reference: String) = buildJsonObject {
    put("notebookPath", JsonPrimitive("untitled.ipynb"))
    put("referenceCellId", JsonPrimitive(reference))
    put("content", JsonPrimitive("print(42)"))
    put("cellType", JsonPrimitive("code"))
    put("position", JsonPrimitive("above"))
  }

  private fun chatUnderTest(): AgentChat = OkHttpAgentChat(
    server = { ServerAddress(baseUrl = serverRule.server.url("/").toString(), token = "secret-token") },
    notebookCells = notebook,
    labCommands = webClient,
    uiDispatcher = Dispatchers.Unconfined,
  )

  private companion object {
    const val ADD_CELL = "jupyterlab-ai-commands:add-cell"
    const val TIMEOUT_MILLIS = 5_000L
    const val POLL_MILLIS = 10L

    val KINDS = mapOf(
      "https://events.jupyter.org/jupyterlab_command_toolkit/lab_command_ack/v1" to "ack",
      "https://events.jupyter.org/jupyterlab_command_toolkit/lab_command_result/v1" to "result",
    )
  }
}
