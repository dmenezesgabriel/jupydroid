package com.jupydroid.extensions.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentModelsTest {
  private val models = AgentModels(
    current = "opencode/big-pickle",
    offered = listOf(AgentModel("opencode/big-pickle", "Big Pickle"), AgentModel("opencode/gpt-5-nano", "GPT-5 Nano")),
  )

  @Test
  fun theModelInUseIsTheOneTheAgentUsesWhenNoneIsPicked() {
    assertEquals("opencode/big-pickle", models.inUse(picked = null))
  }

  @Test
  fun aPickedModelIsTheOneInUse() {
    assertEquals("opencode/gpt-5-nano", models.inUse(picked = "opencode/gpt-5-nano"))
  }

  /** jupyter-ai labels a control with its value's name (jupyter-ai-persona-manager@d5ed04b `currentControlLabel`). */
  @Test
  fun theModelInUseIsNamedByItsName() {
    assertEquals("GPT-5 Nano", models.nameInUse(picked = "opencode/gpt-5-nano"))
  }

  /** ...and by its id when no offered model carries it. */
  @Test
  fun aModelNotOfferedIsNamedByItsId() {
    assertEquals("local/other", models.copy(current = "local/other").nameInUse(picked = null))
  }

  @Test
  fun nothingIsInUseBeforeTheAgentSaysSo() {
    assertNull(AgentModels().nameInUse(picked = null))
  }

  /** jupyter-ai draws the Model control only when the persona offers models (`buildControls`). */
  @Test
  fun modelsAreOfferedOnlyWhenTheAgentListsSome() {
    assertTrue(models.choosable)
    assertFalse(AgentModels(current = "opencode/big-pickle").choosable)
  }

  private val many = AgentModels(
    current = "opencode/big-pickle",
    offered = listOf(
      AgentModel("opencode/beam-free", "Beam Free"),
      AgentModel("opencode/big-pickle", "Big Pickle"),
      AgentModel("openrouter/cohere/north-mini-code:free", "North Mini Code (free)"),
    ),
  )

  private fun AgentModels.listedIds(picked: String?, query: String) = listing(picked, query).map { it.id }

  /** jupyter-ai heads its list with the value in use (`defaultChoiceLabel`), so it is found without a scroll. */
  @Test
  fun theModelInUseIsListedFirstThenTheRestInTheAgentsOrder() {
    assertEquals(
      listOf("opencode/big-pickle", "opencode/beam-free", "openrouter/cohere/north-mini-code:free"),
      many.listedIds(picked = null, query = ""),
    )
  }

  @Test
  fun aPickedModelIsListedFirst() {
    assertEquals(
      "openrouter/cohere/north-mini-code:free",
      many.listedIds("openrouter/cohere/north-mini-code:free", "")[0],
    )
  }

  /** A case-insensitive substring of the name, as jupyter-ai-persona-manager@d5ed04b `filterChoices` matches. */
  @Test
  fun aQueryListsTheModelsWhoseNameHoldsItInAnyCase() {
    assertEquals(listOf("openrouter/cohere/north-mini-code:free"), many.listedIds(null, "MINI code"))
  }

  /** ...or of the id, which names the provider a display name leaves out. */
  @Test
  fun aQueryAlsoMatchesTheModelsId() {
    assertEquals(listOf("openrouter/cohere/north-mini-code:free"), many.listedIds(null, "cohere"))
  }

  @Test
  fun aQueryOfOnlySpacesListsEverything() {
    assertEquals(3, many.listing(picked = null, query = "  ").size)
  }

  @Test
  fun aQueryNothingHoldsListsNothing() {
    assertTrue(many.listing(picked = null, query = "zzz").isEmpty())
  }
}
