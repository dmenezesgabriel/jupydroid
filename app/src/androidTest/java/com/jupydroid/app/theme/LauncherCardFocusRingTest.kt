package com.jupydroid.app.theme

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.fake.FakeKernelGateway
import com.jupydroid.app.data.fake.FakeKernelSpecs
import com.jupydroid.app.data.fake.FakeNotebookRepository
import com.jupydroid.app.data.fake.FakeTerminalGateway
import com.jupydroid.app.data.fake.FakeWorkspaceFiles
import com.jupydroid.app.domain.model.BASE_SPACE
import com.jupydroid.app.ui.launcher.LAUNCHER_CARD_TAG
import com.jupydroid.app.ui.launcher.LauncherCommand
import com.jupydroid.app.ui.launcher.LauncherScreen
import com.jupydroid.app.ui.launcher.LauncherViewModel
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A launcher card that takes keyboard focus draws the palette's `focusBorder` on its edge.
 *
 * The card is a `Surface(onClick)`, which calls `ripple()` itself, so `FocusRingIndication` does
 * not reach it. The card is the node read, because it is the thing the ring is drawn on and it
 * draws its own border under it; what is compared is `FocusRingEdgeProbe`'s.
 */
@RunWith(AndroidJUnit4::class)
class LauncherCardFocusRingTest {
  @get:Rule val compose = createComposeRule()
  private val probe = FocusRingEdgeProbe(compose)

  @Test
  fun aFocusedLauncherCardDrawsTheRingInDark() = assertKeyboardFocusDrawsTheRing(darkTheme = true)

  @Test
  fun aFocusedLauncherCardDrawsTheRingInLight() = assertKeyboardFocusDrawsTheRing(darkTheme = false)

  @Test
  fun aFingerTapOnALauncherCardDrawsNoRing() {
    showLauncher(darkTheme = true)

    probe.assertFingerTapDrawsNoRing(card(), edgeOf = card())
  }

  private fun assertKeyboardFocusDrawsTheRing(darkTheme: Boolean) {
    showLauncher(darkTheme)

    probe.assertKeyboardFocusDrawsTheRing(card(), edgeOf = card())
  }

  private fun showLauncher(darkTheme: Boolean) = probe.show(darkTheme) {
    LauncherScreen(cwd = "", viewModel = viewModel(), onLaunched = {})
  }

  private fun card() = compose.onNodeWithTag("$LAUNCHER_CARD_TAG-${LauncherCommand.NEW_NOTEBOOK.name}")

  private fun viewModel() = LauncherViewModel(
    FakeNotebookRepository(),
    FakeWorkspaceFiles(),
    FakeTerminalGateway(),
    FakeKernelGateway(),
    FakeKernelSpecs(),
    BASE_SPACE,
  ) { 1 }
}
