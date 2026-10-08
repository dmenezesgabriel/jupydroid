package com.jupydroid.app.ui.spaces

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The environments section of a Space's Settings: each environment by its path, whether it can
 * hold a kernel, and for one that cannot, the command that fixes it - visible, not inferred.
 */
@RunWith(AndroidJUnit4::class)
class SpaceSettingsDialogEnvironmentsTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun eachEnvironmentIsListedByItsPath() {
    show(listOf(stats, bare))

    compose.onNodeWithText("envs/stats").assertExists()
    compose.onNodeWithText("envs/bare").assertExists()
  }

  @Test
  fun anEnvironmentWithIpykernelSaysSo() {
    show(listOf(stats))

    compose.onNodeWithText(HAS_IPYKERNEL).assertExists()
  }

  @Test
  fun anEnvironmentWithoutIpykernelShowsTheCommandThatAddsIt() {
    show(listOf(bare))

    compose.onNodeWithText(bare.ipykernelInstallCommand).assertExists()
  }

  @Test
  fun aWorkspaceWithNoEnvironmentSaysSo() {
    show(emptyList())

    compose.onNodeWithText(NO_ENVIRONMENTS).assertExists()
  }

  private fun show(environments: List<PythonEnvironment>?) {
    compose.setContent {
      JupyterDroidTheme { SpaceSettingsDialog(thesis, environments, onSave = {}, onDismiss = {}) }
    }
  }

  private companion object {
    val thesis = SpaceConfig(id = "space-1", name = "Thesis", created = Instant.EPOCH)
    val stats = PythonEnvironment("envs/stats", hasIpykernel = true)
    val bare = PythonEnvironment("envs/bare", hasIpykernel = false)
  }
}
