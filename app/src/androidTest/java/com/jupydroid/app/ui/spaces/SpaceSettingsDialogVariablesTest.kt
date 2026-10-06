package com.jupydroid.app.ui.spaces

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The Space's own variables in its Settings: added, edited and removed, and a name the app derives
 * refused **on screen, by name**, with Save held - the port would refuse it too.
 */
@RunWith(AndroidJUnit4::class)
class SpaceSettingsDialogVariablesTest {
  @get:Rule val compose = createComposeRule()

  private var saved: SpaceSettings? = null

  @Test
  fun anAddedVariableIsSavedBesideTheIdleShutdown() {
    show(thesis)

    addVariable("GREETING", "hello")
    compose.onNodeWithText(SAVE_SETTINGS).performClick()

    assertEquals(SpaceSettings(environmentVariables = mapOf("GREETING" to "hello")), saved)
  }

  @Test
  fun aDerivedNameIsRefusedByNameAndHoldsSave() {
    show(thesis)

    addVariable("HOME", "/tmp")

    compose.onNodeWithText("'HOME' is derived from the Space's directory and cannot be set").assertExists()
    compose.onNodeWithText(SAVE_SETTINGS).assertIsNotEnabled()
  }

  @Test
  fun aStoredVariableIsShownAndCanBeRemoved() {
    show(thesis.copy(settings = SpaceSettings(environmentVariables = mapOf("GREETING" to "hello"))))
    compose.onNodeWithTag(variableNameTag(0)).assertExists()

    compose.onNodeWithContentDescription(removeVariableLabel("GREETING")).performClick()
    compose.onNodeWithText(SAVE_SETTINGS).assertIsEnabled().performClick()

    assertEquals(SpaceSettings(), saved)
  }

  private fun addVariable(name: String, value: String) {
    compose.onNodeWithText(ADD_VARIABLE).performClick()
    compose.onNodeWithTag(variableNameTag(0)).performTextInput(name)
    compose.onNodeWithTag(variableValueTag(0)).performTextInput(value)
  }

  private fun show(space: SpaceConfig) {
    compose.setContent {
      JupyterDroidTheme { SpaceSettingsDialog(space, onSave = { saved = it }, onDismiss = {}) }
    }
  }

  private companion object {
    val thesis = SpaceConfig(id = "space-1", name = "Thesis", created = Instant.EPOCH)
  }
}
