package com.jupydroid.app.ui.spaces

import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
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

/** The Space's startup script in its Settings: what is typed is saved verbatim, and a stored one is shown. */
@RunWith(AndroidJUnit4::class)
class SpaceSettingsDialogStartupScriptTest {
  @get:Rule val compose = createComposeRule()

  private var saved: SpaceSettings? = null

  @Test
  fun aTypedScriptIsSavedVerbatim() {
    show(thesis)

    compose.onNodeWithTag(STARTUP_SCRIPT_FIELD_TAG).performScrollTo().performTextInput("echo started > ~/startup.txt")
    compose.onNodeWithText(SAVE_SETTINGS).performClick()

    assertEquals(SpaceSettings(startupScript = "echo started > ~/startup.txt"), saved)
  }

  @Test
  fun aStoredScriptIsShownAndClearingItSavesNone() {
    show(thesis.copy(settings = SpaceSettings(startupScript = "exit 3")))
    compose.onNodeWithTag(STARTUP_SCRIPT_FIELD_TAG).performScrollTo().assertTextContains("exit 3")

    compose.onNodeWithTag(STARTUP_SCRIPT_FIELD_TAG).performTextClearance()
    compose.onNodeWithText(SAVE_SETTINGS).performClick()

    assertEquals(SpaceSettings(startupScript = ""), saved)
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
