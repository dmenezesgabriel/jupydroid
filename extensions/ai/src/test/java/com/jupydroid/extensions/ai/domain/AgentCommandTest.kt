package com.jupydroid.extensions.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class AgentCommandTest {
  private val compact = AgentCommand("/compact", "compact the session")
  private val commit = AgentCommand("/commit", null)
  private val review = AgentCommand("/review", "review changes")
  private val offered = listOf(compact, commit, review)

  @Test
  fun aSlashAloneListsEveryCommandInTheAgentsOrder() {
    assertEquals(offered, offered.completing("/"))
  }

  /** jupyter-ai prefix-matches the word typed (jupyter-ai-persona-manager@d5ed04b `src/slash-commands.ts`). */
  @Test
  fun whatIsTypedNarrowsTheListToTheCommandsItBegins() {
    assertEquals(listOf(compact, commit), offered.completing("/co"))
    assertEquals(listOf(compact), offered.completing("/compact"))
  }

  /** opencode runs a command only as the prompt's first word, so a space ends the choice. */
  @Test
  fun onceASpaceIsTypedNothingIsListed() {
    assertEquals(emptyList<AgentCommand>(), offered.completing("/compact "))
    assertEquals(emptyList<AgentCommand>(), offered.completing("/co now"))
  }

  @Test
  fun aQuestionThatDoesNotBeginWithASlashListsNothing() {
    assertEquals(emptyList<AgentCommand>(), offered.completing(""))
    assertEquals(emptyList<AgentCommand>(), offered.completing("what /co"))
  }

  /** jupyter-chat adds a space to a command accepted with `spaceOnAccept`, so what follows is its argument. */
  @Test
  fun aCommandIsTypedAsItsNameAndASpace() {
    assertEquals("/compact ", compact.typed)
  }
}
