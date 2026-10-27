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
   * as the chat opens; its opencode agent offers [models], [settings] and [commands], and switches to what a
   * question is asked with. The chat lists [personas], and each answers what it is asked.
   */
  protected abstract fun chat(
    history: List<ChatMessage> = emptyList(),
    replies: Map<String, String> = emptyMap(),
    writing: Boolean = false,
    models: AgentModels = AgentModels(),
    settings: List<AgentSetting> = emptyList(),
    commands: List<AgentCommand> = emptyList(),
    personas: List<AgentPersona> = listOf(OPENCODE_PERSONA),
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

    chat.ask("hello?", OPENCODE)

    val answered = transcripts.until { it.messages.size == 2 }
    assertEquals(listOf("hello?" to true, "hi" to false), answered.messages.map { it.body to it.fromMe })
  }

  /** Each answer says which agent wrote it, whether the chat already held it or it was just asked. */
  @Test
  fun anAnswerIsNamedByTheAgentThatWroteIt() = withOpenChat(
    chat(
      history = listOf(ChatMessage(id = "b", body = "answered", fromMe = false, sender = OPENCODE)),
      replies = mapOf(
        "hello?" to "hi",
      ),
    ),
  ) { chat, transcripts ->
    transcripts.receive()

    chat.ask("hello?", OPENCODE)

    val answered = transcripts.until { it.messages.size == 3 }
    val names = answered.messages.map(answered::senderName)
    assertEquals(listOf("OpenCode", null, "OpenCode"), names)
  }

  @Test
  fun anOpenChatSaysWhichModelsItsAgentOffersAndWhichItUses() = withOpenChat(chat(models = MODELS)) {
      chat,
      transcripts,
    ->
    transcripts.receive()

    chat.select(OPENCODE)

    assertEquals(MODELS, transcripts.until { it.agentModels.choosable }.agentModels)
  }

  /** The persona applies a model picked for a question before it answers it (`base_persona.py`, `apply_model_spec`). */
  @Test
  fun aQuestionAskedWithAModelSwitchesTheAgentToIt() = withOpenChat(chat(models = MODELS)) { chat, transcripts ->
    transcripts.receive()
    chat.select(OPENCODE)
    transcripts.until { it.agentModels.choosable }

    chat.ask("hello?", OPENCODE, AgentSelection(model = "opencode/gpt-5-nano"))

    transcripts.until { it.agentModels.current == "opencode/gpt-5-nano" }
  }

  @Test
  fun anOpenChatSaysWhichSettingsItsAgentOffersAndTheValueOfEach() =
    withOpenChat(chat(models = MODELS, settings = SETTINGS)) { chat, transcripts ->
      transcripts.receive()

      chat.select(OPENCODE)

      assertEquals(SETTINGS, transcripts.until { it.agentSettings.isNotEmpty() }.agentSettings)
    }

  @Test
  fun anOpenChatSaysWhichCommandsItsAgentOffers() =
    withOpenChat(chat(models = MODELS, commands = COMMANDS)) { chat, transcripts ->
      transcripts.receive()

      chat.select(OPENCODE)

      assertEquals(COMMANDS, transcripts.until { it.agentCommands.isNotEmpty() }.agentCommands)
    }

  /** The persona applies a question's settings before it answers it (`base_persona.py`, `apply_settings_spec`). */
  @Test
  fun aQuestionAskedWithASettingSwitchesTheAgentToIt() =
    withOpenChat(chat(models = MODELS, settings = SETTINGS)) { chat, transcripts ->
      transcripts.receive()
      chat.select(OPENCODE)
      transcripts.until { it.agentSettings.isNotEmpty() }

      chat.ask("hello?", OPENCODE, AgentSelection(settings = mapOf("__mode__" to "plan")))

      transcripts.until { shown -> shown.agentSettings.single().current == "plan" }
    }

  /** jupyter-ai's picker lists the personas the manager publishes (`personas/v1`). */
  @Test
  fun anOpenChatListsItsAgents() = withOpenChat(chat(personas = BOTH)) { _, transcripts ->
    assertEquals(BOTH, transcripts.until { it.agentPersonas.isNotEmpty() }.agentPersonas)
  }

  /** The router hands a question to the persona its `metadata.to_persona` names (`persona_manager.py`). */
  @Test
  fun aQuestionIsAnsweredByTheAgentItIsAskedOf() =
    withOpenChat(chat(replies = mapOf("hello?" to "hi"), personas = BOTH)) { chat, transcripts ->
      transcripts.receive()

      chat.ask("hello?", CLAUDE)

      val answered = transcripts.until { it.messages.size == 2 }
      assertEquals(CLAUDE, answered.messages.first().askedOf)
      assertEquals("Claude", answered.senderName(answered.messages.last()))
    }

  @Test
  fun selectingInAChatNobodyOpenedThrows() = runBlocking {
    val failure = runCatching { chat().select(OPENCODE) }.exceptionOrNull()

    assertTrue("$failure", failure is AgentChatException)
    assertTrue("$failure", failure!!.message!!.contains("not open"))
  }

  @Test
  fun askingAChatNobodyOpenedThrowsAndNamesTheQuestion() = runBlocking {
    val failure = runCatching { chat().ask("hello?", OPENCODE) }.exceptionOrNull()

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

    /** opencode's persona as the chat's `users` map names it (`base_persona.py`, `as_user`). */
    const val OPENCODE = "jupyter-ai-personas::jupyter_ai_acp_client::OpenCodeAcpPersona"
    const val CLAUDE = "jupyter-ai-personas::jupyter_ai_acp_client::ClaudeAcpPersona"
    val OPENCODE_PERSONA = AgentPersona(OPENCODE, "OpenCode")
    val BOTH = listOf(AgentPersona(CLAUDE, "Claude"), OPENCODE_PERSONA)

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
