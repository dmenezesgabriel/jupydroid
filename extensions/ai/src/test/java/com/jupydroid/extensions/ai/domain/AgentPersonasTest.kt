package com.jupydroid.extensions.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AgentPersonasTest {
  private val openCode = AgentPersona("opencode-persona", "OpenCode")
  private val claude = AgentPersona("claude-persona", "Claude")

  private fun asked(persona: String?) = ChatMessage("q", "hello?", fromMe = true, askedOf = persona)

  private fun answered(persona: String) = ChatMessage("a", "hi", fromMe = false, sender = persona)

  private fun transcript(vararg messages: ChatMessage, listed: List<AgentPersona> = listOf(openCode, claude)) =
    ChatTranscript(messages.toList(), agentPersonas = listed)

  @Test
  fun aPickMadeInThePanelIsTheOneAsked() {
    assertEquals("claude-persona", transcript(asked("opencode-persona")).agents.inUse(picked = "claude-persona"))
  }

  /** VS Code uses a remembered pick only while it is usable (vscode@3f3380c `getUsableRememberedSessionType`). */
  @Test
  fun aPickNoLongerListedIsNotAsked() {
    val listed = transcript(asked("opencode-persona"), listed = listOf(openCode))
    assertEquals("opencode-persona", listed.agents.inUse(picked = "claude-persona"))
  }

  /** The server keeps each question's `metadata.to_persona` in the `.chat` file, so a relaunch reads it back. */
  @Test
  fun withNoPickTheNewestQuestionsPersonaIsAsked() {
    val chat = transcript(asked("opencode-persona"), answered("opencode-persona"), asked("claude-persona"))
    assertEquals("claude-persona", chat.agents.inUse(picked = null))
  }

  @Test
  fun anAgentsAnswerSaysNothingOfWhoIsAsked() {
    assertNull(transcript(answered("claude-persona")).agents.inUse(picked = null))
  }

  @Test
  fun aNewestQuestionToAPersonaNoLongerListedIsNotFollowed() {
    assertNull(transcript(asked("gone-persona")).agents.inUse(picked = null))
  }

  /**
   * jupyter-ai selects a sole persona until the user picks (jupyter-ai-persona-manager@d5ed04b
   * `src/persona-controls.tsx`, `reconcileSelection`).
   */
  @Test
  fun aSolePersonaIsAskedUnpicked() {
    assertEquals("claude-persona", transcript(listed = listOf(claude)).agents.inUse(picked = null))
  }

  /** With several and nothing to go by, none is guessed: the list's order is entry-point load order. */
  @Test
  fun severalPersonasAndNoQuestionAskNone() {
    assertNull(transcript().agents.inUse(picked = null))
    assertNull(transcript().agents.nameInUse(picked = null))
  }

  @Test
  fun thePersonaAskedIsNamedByItsName() {
    assertEquals("Claude", transcript().agents.nameInUse(picked = "claude-persona"))
  }

  @Test
  fun noPersonaListedOffersNothingToPick() {
    assertFalse(transcript(listed = emptyList()).agents.choosable)
  }
}
