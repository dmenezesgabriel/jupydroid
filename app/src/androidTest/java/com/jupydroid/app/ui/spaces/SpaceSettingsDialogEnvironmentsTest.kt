package com.jupydroid.app.ui.spaces

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.PythonEnvironment
import com.jupydroid.app.domain.model.SpaceConfig
import com.jupydroid.app.theme.JupydroidTheme
import com.jupydroid.app.ui.environments.HAS_IPYKERNEL
import com.jupydroid.app.ui.environments.NO_ENVIRONMENTS
import com.jupydroid.app.ui.environments.goneLabel
import com.jupydroid.app.ui.environments.registerLabel
import com.jupydroid.app.ui.environments.registeredLabel
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

/**
 * The environments section of a Space's Settings: each environment by its path, whether it can
 * hold a kernel, and for one that cannot, the command that fixes it - visible, not inferred. Register
 * is pressable exactly when a kernel could start in the environment.
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

  @Test
  fun registerIsPressableForAnEnvironmentWithIpykernel() {
    show(listOf(stats))

    compose.onNodeWithContentDescription(registerLabel(stats.path)).assertIsEnabled()
  }

  /** A spec for an env with no ipykernel names a kernel that cannot start, so there is nothing to press. */
  @Test
  fun registerIsNotPressableForAnEnvironmentWithoutIpykernel() {
    show(listOf(bare))

    compose.onNodeWithContentDescription(registerLabel(bare.path)).assertIsNotEnabled()
  }

  @Test
  fun pressingRegisterAsksForThatEnvironment() {
    var asked: String? = null
    show(listOf(bare, stats), onRegister = { asked = it })

    compose.onNodeWithContentDescription(registerLabel(stats.path)).performScrollTo().performClick()

    assertEquals(stats.path, asked)
  }

  @Test
  fun aRegisteredEnvironmentSaysItsKernelNameInsteadOfOfferingRegister() {
    show(listOf(stats.copy(registeredAs = "stats")))

    compose.onNodeWithText(registeredLabel("stats")).assertExists()
    compose.onNodeWithContentDescription(registerLabel(stats.path)).assertDoesNotExist()
  }

  /** `rm -r` after Register: the spec is still there, so the row says so and gives the command that removes it. */
  @Test
  fun aGoneEnvironmentSaysSoAndShowsTheCommandThatRemovesItsKernel() {
    show(listOf(gone))

    compose.onNodeWithText(goneLabel("stats")).assertExists()
    compose.onNodeWithText(gone.kernelRemoveCommand).assertExists()
  }

  /** Nothing can start in it, and its spec already names it, so there is neither Register nor "Registered". */
  @Test
  fun aGoneEnvironmentOffersNoRegister() {
    show(listOf(gone))

    compose.onNodeWithContentDescription(registerLabel(gone.path)).assertDoesNotExist()
    compose.onNodeWithText(registeredLabel("stats")).assertDoesNotExist()
  }

  /** A refusal is shown where it was asked for: the home behind the dialog is covered by it. */
  @Test
  fun aRefusalIsShownInTheDialog() {
    show(listOf(stats), refusal = "a kernel named 'stats' is already held")

    compose.onNodeWithText("a kernel named 'stats' is already held").assertExists()
  }

  private fun show(
    environments: List<PythonEnvironment>?,
    onRegister: (String) -> Unit = {},
    refusal: String? = null,
  ) {
    compose.setContent {
      JupydroidTheme {
        SpaceSettingsDialog(
          thesis,
          environments,
          onSave = {},
          onDismiss = {},
          onRegister = onRegister,
          refusal = refusal,
        )
      }
    }
  }

  private companion object {
    val thesis = SpaceConfig(id = "space-1", name = "Thesis", created = Instant.EPOCH)
    val stats = PythonEnvironment("envs/stats", hasIpykernel = true)
    val bare = PythonEnvironment("envs/bare", hasIpykernel = false)
    val gone = PythonEnvironment("envs/stats", hasIpykernel = false, registeredAs = "stats", isMissing = true)
  }
}
