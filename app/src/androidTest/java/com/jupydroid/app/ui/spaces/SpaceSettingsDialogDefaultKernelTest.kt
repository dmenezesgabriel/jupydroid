package com.jupydroid.app.ui.spaces

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.KernelSpec
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.domain.model.SpaceSettings
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The Space's default kernel in its Settings: the stored one shown on the control, the Space's own
 * kernelspecs offered, and the one picked handed to Save by its name.
 */
@RunWith(AndroidJUnit4::class)
class SpaceSettingsDialogDefaultKernelTest {
  @get:Rule val compose = createComposeRule()

  private var saved: SpaceSettings? = null

  @Test
  fun theStoredDefaultIsShownByItsDisplayName() {
    show(thesis.copy(settings = SpaceSettings(defaultKernel = "stats")))

    compose.onNodeWithContentDescription(defaultKernelDescription("Python (stats)")).assertExists()
  }

  @Test
  fun aPickedKernelIsSavedByItsName() {
    show(thesis)

    compose.onNodeWithContentDescription(defaultKernelDescription("Python 3")).performClick()
    compose.onNodeWithContentDescription(defaultKernelPickDescription("Python (stats)")).performClick()
    compose.onNodeWithText(SAVE_SETTINGS).performClick()

    assertEquals(SpaceSettings(defaultKernel = "stats"), saved)
  }

  private fun show(space: SpaceConfig) {
    compose.setContent {
      JupyterDroidTheme {
        SpaceSettingsDialog(space, kernelChoices = CHOICES, onSave = { saved = it }, onDismiss = {})
      }
    }
  }

  private companion object {
    val thesis = SpaceConfig(id = "space-1", name = "Thesis", created = Instant.EPOCH)
    val CHOICES = listOf(KernelSpec("python3", "Python 3", "python"), KernelSpec("stats", "Python (stats)", "python"))
  }
}
