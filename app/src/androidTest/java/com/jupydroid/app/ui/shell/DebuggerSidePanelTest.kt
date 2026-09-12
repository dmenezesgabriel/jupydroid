package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.model.DebugVariableGroup
import com.jupydroid.app.domain.port.DebugStackFrame
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.theme.JupyterDroidTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The reference's `Debugger` sidebar (`packages/debugger-extension/src/index.ts:1357`), folded
 * into this app's one side area - a column that draws the open notebook's stopped call stack and
 * top-frame variables and stays there while the document scrolls, [TableOfContentsSidePanel]'s
 * own shape.
 */
@RunWith(AndroidJUnit4::class)
class DebuggerSidePanelTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun everyFrameAndVariableIsOnScreen() {
    show(stack())

    compose.onNodeWithText("<module> — line 3").assertIsDisplayed()
    compose.onNodeWithText("helper — line 10").assertIsDisplayed()
    compose.onNodeWithText("Locals").assertIsDisplayed()
    compose.onNodeWithText("x: int = 41").assertIsDisplayed()
  }

  @Test
  fun nothingStoppedDrawsThePlaceholder() {
    show(stack = null)

    compose.onNodeWithText("Not stopped").assertIsDisplayed()
  }

  /** The header takes the panel's own name - there is no per-document title, unlike the table of contents. */
  @Test
  fun theHeaderNamesThePanel() {
    show(stack())

    compose.onNodeWithText("Debugger").assertIsDisplayed()
  }

  private fun stack() = DebugStack(
    frames = listOf(
      DebugStackFrame(id = 5, name = "<module>", line = 3, column = 1),
      DebugStackFrame(id = 6, name = "helper", line = 10, column = 1),
    ),
    groups = listOf(DebugVariableGroup(scope = "Locals", variables = listOf(DebugVariable("x", "41", "int", 0)))),
  )

  private fun show(stack: DebugStack?) {
    compose.setContent { JupyterDroidTheme { DebuggerSidePanel(stack = stack) } }
  }
}
