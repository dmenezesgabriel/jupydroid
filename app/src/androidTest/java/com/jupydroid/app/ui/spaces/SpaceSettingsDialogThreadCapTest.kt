package com.jupydroid.app.ui.spaces

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The Space's thread cap in its Settings: a number saved as the cap, a blank field saved as none,
 * Save held outside the range - and the cap's own names refused as variables, so no row can set
 * one around it.
 */
@RunWith(AndroidJUnit4::class)
class SpaceSettingsDialogThreadCapTest {
  @get:Rule val compose = createComposeRule()

  private var saved: SpaceSettings? = null

  @Test
  fun aTypedCapIsSaved() {
    show(thesis)

    compose.onNodeWithTag(THREAD_CAP_FIELD_TAG).performTextInput("1")
    compose.onNodeWithText(SAVE_SETTINGS).performClick()

    assertEquals(SpaceSettings(threadCap = 1), saved)
  }

  @Test
  fun aStoredCapIsShownAndClearingItSavesNone() {
    show(thesis.copy(settings = SpaceSettings(threadCap = 4)))
    compose.onNodeWithTag(THREAD_CAP_FIELD_TAG).assertTextContains("4")

    compose.onNodeWithTag(THREAD_CAP_FIELD_TAG).performTextClearance()
    compose.onNodeWithText(SAVE_SETTINGS).performClick()

    assertEquals(SpaceSettings(threadCap = null), saved)
  }

  @Test
  fun aCapOutsideTheRangeHoldsSave() {
    show(thesis)

    compose.onNodeWithTag(THREAD_CAP_FIELD_TAG).performTextInput("0")

    compose.onNodeWithText(SAVE_SETTINGS).assertIsNotEnabled()
  }

  @Test
  fun aCapNameIsRefusedAsAVariableAndHoldsSave() {
    show(thesis)

    compose.onNodeWithText(ADD_VARIABLE).performClick()
    compose.onNodeWithTag(variableNameTag(0)).performTextInput("OMP_NUM_THREADS")
    compose.onNodeWithTag(variableValueTag(0)).performTextInput("8")

    compose.onNodeWithText("'OMP_NUM_THREADS' is set by the Space's thread cap and cannot be set").assertExists()
    compose.onNodeWithText(SAVE_SETTINGS).assertIsNotEnabled()
  }

  private fun show(space: SpaceConfig) {
    compose.setContent {
      JupydroidTheme { SpaceSettingsDialog(space, onSave = { saved = it }, onDismiss = {}) }
    }
  }

  private companion object {
    val thesis = SpaceConfig(id = "space-1", name = "Thesis", created = Instant.EPOCH)
  }
}
