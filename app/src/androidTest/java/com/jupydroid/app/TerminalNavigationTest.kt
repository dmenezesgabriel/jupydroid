package com.jupydroid.app

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.data.disk.DiskNotebookRepository
import com.jupydroid.app.data.disk.DiskRecentDocumentsRepository
import com.jupydroid.app.data.disk.DiskWorkspaceRepository
import com.jupydroid.app.data.fake.FakeLauncherSettingsRepository
import com.jupydroid.app.di.AppContainer
import com.jupydroid.app.domain.model.LauncherSettings
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.terminal.TERMINAL_INPUT_TAG
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith

/**
 * The terminal is reachable from the explorer, and what is typed comes back on screen -
 * `docs/PLAN.md`'s P4i, sub-step 1's own gate. `AppContainer`'s default `FakeTerminalGateway`
 * echoes back whatever it is sent, standing in for a real shell here exactly as it does in
 * [com.jupydroid.app.ui.terminal.TerminalPaneViewModelTest].
 */
@RunWith(AndroidJUnit4::class)
class TerminalNavigationTest {
  @get:Rule val compose = createComposeRule()

  @get:Rule val temp = TemporaryFolder()

  @get:Rule val state = TemporaryFolder()

  @Test
  fun tappingNewTerminalOpensAShellAndTypingComesBackOnScreen() {
    startApp()

    compose.onNodeWithContentDescription("New terminal").performClick()
    waitForTag(TERMINAL_INPUT_TAG)

    // Enter, too - not just typing - so the field clears (`TerminalInputField`'s own rule: it
    // clears on a trailing "\n") and "ls" is unambiguously the *output* row rather than also the
    // field's own still-uncommitted text, which a bare substring match cannot tell apart.
    compose.onNodeWithTag(TERMINAL_INPUT_TAG).performTextInput("ls\n")

    // Existence, not `assertIsDisplayed` - the real IME this field summons resizes this bare
    // test host's own window, and the semantics tree reports every node's bounds relative to the
    // window's pre-resize frame, which reads as "off-screen" no matter where the node actually
    // draws (checked: `Terminal`'s own app-bar title carries the identical offset). The device
    // proof that this genuinely renders on screen is Maestro's - `evidence/p4i-terminal-screen/`
    // and `evidence/p4i-terminal-tablet/` are both screenshots of it, not a claim.
    waitForText("ls")
  }

  private fun startApp() {
    compose.setContent {
      JupyterDroidTheme {
        MainNavigation(
          appContainer = AppContainer(
            launcherSettings = FakeLauncherSettingsRepository(LauncherSettings(launcherSeen = true)),
            notebookRepository = DiskNotebookRepository(temp.root),
            workspaceRepository = DiskWorkspaceRepository(temp.root),
            recentDocuments = DiskRecentDocumentsRepository(state.root, temp.root),
          ),
          importTitle = null,
          onImportConsumed = {},
          // Single pane: the explorer and the terminal share the window otherwise, and "opened"
          // would already be true before the tap.
          twoPane = false,
        )
      }
    }
  }

  private fun waitForText(text: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty()
  }

  private fun waitForTag(tag: String) = compose.waitUntil(TIMEOUT_MILLIS) {
    compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
  }

  private companion object {
    const val TIMEOUT_MILLIS = 5_000L
  }
}
