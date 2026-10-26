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
}
