package com.jupydroid.extensions.ai.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentSettingsTest {
  private val mode = AgentSetting(
    id = "__mode__",
    name = "Session Mode",
    current = "build",
    offered = listOf(AgentSettingValue("build", "build"), AgentSettingValue("plan", "plan")),
  )

  /** A setting is labelled with its value's name, as the model is (`currentControlLabel`). */
  @Test
  fun aSettingIsNamedByTheValueInUse() {
    assertEquals("build", mode.nameInUse(picked = null))
    assertEquals("plan", mode.nameInUse(picked = "plan"))
  }

  @Test
  fun aSettingIsChoosableOnlyWhenTheAgentOffersValues() {
    assertTrue(mode.choosable)
    assertFalse(mode.copy(offered = emptyList()).choosable)
  }

  /**
   * opencode lists `effort` only for a model with variants (anomalyco/opencode@e3c4eb24
   * `packages/opencode/src/acp/config-option.ts`, `buildEffortSelectOption`): a pick for a setting
   * no longer listed is not sent.
   */
  @Test
  fun aSelectionKeepsOnlyTheSettingsTheAgentLists() {
    val selection = AgentSelection(model = "opencode/a", settings = mapOf("__mode__" to "plan", "effort" to "high"))

    val kept = selection.listedIn(listOf(mode))

    assertEquals(AgentSelection(model = "opencode/a", settings = mapOf("__mode__" to "plan")), kept)
  }
}
