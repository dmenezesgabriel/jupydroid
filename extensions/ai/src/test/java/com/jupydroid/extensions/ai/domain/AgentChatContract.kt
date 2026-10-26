package com.jupydroid.extensions.ai.domain

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What every [AgentChat] must do, whoever implements it.
 *
 * `runBlocking` rather than `runTest`: an implementation may answer from another thread, as a socket
 * does, and virtual time would not wait for it.
 */
abstract class AgentChatContract {
  /**
   * A chat already holding [history], whose agent answers each question in [replies] with the reply
   * it maps to, and every other question with nothing; when [writing], its agent is writing a reply
   * as the chat opens; its agent offers [models], [settings] and [commands], and switches to what a
   * question is asked with.
   */
  protected abstract fun chat(
    history: List<ChatMessage> = emptyList(),
    replies: Map<String, String> = emptyMap(),
    writing: Boolean = false,
    models: AgentModels = AgentModels(),
    settings: List<AgentSetting> = emptyList(),
    commands: List<AgentCommand> = emptyList(),
  ): AgentChat

  /** What was asked before the chat was opened again is still the user's, and the answer the agent's. */
  @Test
  fun openingAChatShowsWhatItAlreadyHolds() = withOpenChat(
    chat(
      history = listOf(
        ChatMessage(id = "a", body = "earlier", fromMe = true),
        ChatMessage(id = "b", body = "answered", fromMe = false),
      ),
    ),
  ) { _, transcripts ->
    val shown = transcripts.receive().messages.map { it.body to it.fromMe }
    assertEquals(listOf("earlier" to true, "answered" to false), shown)
  }

  @Test
  fun aQuestionIsAnsweredBeneathItself() = withOpenChat(chat(replies = mapOf("hello?" to "hi"))) { chat, transcripts ->
    transcripts.receive()

    chat.ask("hello?")

    val answered = transcripts.until { it.messages.size == 2 }
    assertEquals(listOf("hello?" to true, "hi" to false), answered.messages.map { it.body to it.fromMe })
  }

  @Test
  fun anOpenChatSaysWhichModelsItsAgentOffersAndWhichItUses() = withOpenChat(chat(models = MODELS)) { _, transcripts ->
    assertEquals(MODELS, transcripts.until { it.agentModels.choosable }.agentModels)
  }

  /** The persona applies a model picked for a question before it answers it (`base_persona.py`, `apply_model_spec`). */
  @Test
  fun aQuestionAskedWithAModelSwitchesTheAgentToIt() = withOpenChat(chat(models = MODELS)) { chat, transcripts ->
    transcripts.until { it.agentModels.choosable }

    chat.ask("hello?", AgentSelection(model = "opencode/gpt-5-nano"))

    transcripts.until { it.agentModels.current == "opencode/gpt-5-nano" }
  }

  @Test
  fun anOpenChatSaysWhichSettingsItsAgentOffersAndTheValueOfEach() =
    withOpenChat(chat(models = MODELS, settings = SETTINGS)) { _, transcripts ->
      assertEquals(SETTINGS, transcripts.until { it.agentSettings.isNotEmpty() }.agentSettings)
    }

  @Test
  fun anOpenChatSaysWhichCommandsItsAgentOffers() =
    withOpenChat(chat(models = MODELS, commands = COMMANDS)) { _, transcripts ->
      assertEquals(COMMANDS, transcripts.until { it.agentCommands.isNotEmpty() }.agentCommands)
    }

  /** The persona applies a question's settings before it answers it (`base_persona.py`, `apply_settings_spec`). */
  @Test
  fun aQuestionAskedWithASettingSwitchesTheAgentToIt() =
    withOpenChat(chat(models = MODELS, settings = SETTINGS)) { chat, transcripts ->
      transcripts.until { it.agentSettings.isNotEmpty() }

      chat.ask("hello?", AgentSelection(settings = mapOf("__mode__" to "plan")))

      transcripts.until { shown -> shown.agentSettings.single().current == "plan" }
    }

  @Test
  fun askingAChatNobodyOpenedThrowsAndNamesTheQuestion() = runBlocking {
    val failure = runCatching { chat().ask("hello?") }.exceptionOrNull()

    assertTrue("$failure", failure is AgentChatException)
    assertTrue("$failure", failure!!.message!!.contains("'hello?'"))
  }

  @Test
  fun choosingAnOptionResolvesTheCallWithIt() = withOpenChat(chat(history = listOf(AWAITING))) { chat, transcripts ->
    val call = transcripts.receive().messages.single().toolCalls.single()

    chat.choose(call, ALLOW_ONCE)

    val resolved = transcripts.until { it.messages.single().toolCalls.single().permission != call.permission }
    assertEquals(ToolCallPermission.Resolved(ALLOW_ONCE), resolved.messages.single().toolCalls.single().permission)
  }

  /** The server answers 404 when no permission is pending (jupyter_ai_acp_client 0.3.3 `routes.py`). */
  @Test
  fun choosingForACallNothingAwaitsThrowsAndNamesTheCall() {
    val answered = AWAITING.toolCalls.single().copy(permission = ToolCallPermission.Resolved(ALLOW_ONCE))
    val history = listOf(AWAITING.copy(toolCalls = listOf(answered)))
    withOpenChat(chat(history = history)) { chat, transcripts ->
      val call = transcripts.receive().messages.single().toolCalls.single()

      val failure = runCatching { chat.choose(call, ALLOW_ONCE) }.exceptionOrNull()

      assertTrue("$failure", failure is AgentChatException)
      assertTrue("$failure", failure!!.message!!.contains("'Allow once' for 'echo probe'"))
    }
  }

  @Test
  fun stoppingAWritingAgentEndsItsWriting() = withOpenChat(chat(writing = true)) { chat, transcripts ->
    transcripts.until { it.agentWriting }

    chat.stop()

    transcripts.until { !it.agentWriting }
  }

  @Test
  fun stoppingAChatNobodyOpenedThrows() = runBlocking {
    val failure = runCatching { chat(writing = true).stop() }.exceptionOrNull()

    assertTrue("$failure", failure is AgentChatException)
    assertTrue("$failure", failure!!.message!!.contains("not open"))
  }

  private fun withOpenChat(chat: AgentChat, check: suspend (AgentChat, ReceiveChannel<ChatTranscript>) -> Unit) =
    runBlocking {
      withTimeout(TIMEOUT_MILLIS) {
        val transcripts = Channel<ChatTranscript>(Channel.UNLIMITED)
        val collecting = launch { chat.transcript().collect { transcripts.send(it) } }
        try {
          check(chat, transcripts)
        } finally {
          collecting.cancel()
        }
      }
    }

  private suspend fun ReceiveChannel<ChatTranscript>.until(test: (ChatTranscript) -> Boolean): ChatTranscript {
    while (true) receive().takeIf(test)?.let { return it }
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L

    val MODELS = AgentModels(
      current = "opencode/big-pickle",
      offered = listOf(
        AgentModel("opencode/big-pickle", "Big Pickle"),
        AgentModel("opencode/gpt-5-nano", "GPT-5 Nano"),
      ),
    )

    val SETTINGS = listOf(
      AgentSetting(
        id = "__mode__",
        name = "Session Mode",
        current = "build",
        offered = listOf(AgentSettingValue("build", "build"), AgentSettingValue("plan", "plan")),
      ),
    )

    val COMMANDS = listOf(AgentCommand("/compact", "compact the session"), AgentCommand("/init", null))

    val ALLOW_ONCE = PermissionOption(id = "once", name = "Allow once", rejects = false)
    val REJECT = PermissionOption(id = "reject", name = "Reject", rejects = true)

    /** An agent's message holding one call that waits for the user, as opencode's `bash` does. */
    val AWAITING = ChatMessage(
      id = "t",
      body = "",
      fromMe = false,
      toolCalls = listOf(
        ToolCall(
          id = "c1",
          title = "echo probe",
          status = ToolCallStatus.Running,
          permission = ToolCallPermission.Pending(sessionId = "s1", options = listOf(ALLOW_ONCE, REJECT)),
        ),
      ),
    )
  }
}
