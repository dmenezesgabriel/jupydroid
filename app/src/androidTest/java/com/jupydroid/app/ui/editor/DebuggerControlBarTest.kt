package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The debugger's step/continue toolbar (P6a-9b) - [NotebookBar]'s own sibling for the one state a
 * notebook being read never reaches on its own: a real kernel paused under the debugger.
 */
@RunWith(AndroidJUnit4::class)
class DebuggerControlBarTest {
  @get:Rule val compose = createComposeRule()

  private val calls = mutableListOf<String>()

  @Test
  fun continueFiresItsOwnCallbackOnce() {
    showBar(stepping = false)

    compose.onNodeWithContentDescription("Continue").performClick()

    compose.runOnIdle { assertEquals(listOf("continue"), calls) }
  }

  @Test
  fun nextFiresItsOwnCallbackOnce() {
    showBar(stepping = false)

    compose.onNodeWithContentDescription("Step over").performClick()

    compose.runOnIdle { assertEquals(listOf("next"), calls) }
  }

  @Test
  fun stepInFiresItsOwnCallbackOnce() {
    showBar(stepping = false)

    compose.onNodeWithContentDescription("Step into").performClick()

    compose.runOnIdle { assertEquals(listOf("stepIn"), calls) }
  }

  @Test
  fun stepOutFiresItsOwnCallbackOnce() {
    showBar(stepping = false)

    compose.onNodeWithContentDescription("Step out").performClick()

    compose.runOnIdle { assertEquals(listOf("stepOut"), calls) }
  }

  /**
   * Disabled for the length of one DAP round trip - a second tap before the kernel reports where
   * it landed would send a stale command for a stop it has already left.
   */
  @Test
  fun everyControlIsDisabledWhileAStepIsInFlight() {
    showBar(stepping = true)

    compose.onNodeWithContentDescription("Continue").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Step over").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Step into").assertIsNotEnabled()
    compose.onNodeWithContentDescription("Step out").assertIsNotEnabled()
  }

  @Test
  fun everyControlIsEnabledOnceStopped() {
    showBar(stepping = false)

    compose.onNodeWithContentDescription("Continue").assertIsEnabled()
    compose.onNodeWithContentDescription("Step over").assertIsEnabled()
    compose.onNodeWithContentDescription("Step into").assertIsEnabled()
    compose.onNodeWithContentDescription("Step out").assertIsEnabled()
  }

  @Test
  fun terminateFiresItsOwnCallbackOnce() {
    showBar(stepping = false)

    compose.onNodeWithContentDescription("Terminate").performClick()

    compose.runOnIdle { assertEquals(listOf("terminate"), calls) }
  }

  /**
   * Terminate is the escape hatch for a request stuck in flight - P6a-10's own tablet run needed
   * exactly this to get free of a kernel that never answered. Gating it behind `stepping` would
   * remove the one control meant to work when everything else is stuck.
   */
  @Test
  fun terminateStaysEnabledWhileAStepIsInFlight() {
    showBar(stepping = true)

    compose.onNodeWithContentDescription("Terminate").assertIsEnabled()
  }

  /**
   * The call stack and variables toggle (the phone-narrow adaptation of
   * [DebuggerSidePanel][com.jupydroid.app.ui.shell.DebuggerSidePanel]) - absent by default, the
   * same nullable-means-no-affordance rule [EditorTopBar]'s own `onShowTableOfContents` already
   * uses, since a two-pane window shows this through the side panel instead and needs no toggle.
   */
  @Test
  fun theVariablesToggleFiresItsOwnCallbackOnce() {
    showBar(stepping = false, onToggleVariables = { calls += "toggleVariables" })

    compose.onNodeWithContentDescription(SHOW_DEBUGGER_PANEL).performClick()

    compose.runOnIdle { assertEquals(listOf("toggleVariables"), calls) }
  }

  @Test
  fun theVariablesToggleIsAbsentWithNoCallbackGiven() {
    showBar(stepping = false)

    compose.onNodeWithContentDescription(SHOW_DEBUGGER_PANEL).assertDoesNotExist()
  }

  private fun showBar(stepping: Boolean, onToggleVariables: (() -> Unit)? = null) {
    compose.setContent {
      JupyterDroidTheme {
        DebuggerControlBar(
          stepping = stepping,
          onContinue = { calls += "continue" },
          onNext = { calls += "next" },
          onStepIn = { calls += "stepIn" },
          onStepOut = { calls += "stepOut" },
          onTerminate = { calls += "terminate" },
          onToggleVariables = onToggleVariables,
        )
      }
    }
  }
}
