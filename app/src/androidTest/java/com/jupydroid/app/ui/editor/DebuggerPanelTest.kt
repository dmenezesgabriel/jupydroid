package com.jupydroid.app.ui.editor

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
 * The narrow adaptation of [DebuggerSidePanel][com.jupydroid.app.ui.shell.DebuggerSidePanel], on
 * both presentations [CellActionPresentation]'s own rule offers - [TableOfContentsPanelTest]'s
 * own shape, since the width rule is tested once on the JVM and what needs a device is that each
 * branch renders here too.
 */
@RunWith(AndroidJUnit4::class)
class DebuggerPanelTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theMenuShowsTheStack() = assertShowsTheStack(CellActionPresentation.MENU)

  @Test
  fun theSheetShowsTheStack() = assertShowsTheStack(CellActionPresentation.SHEET)

  private fun assertShowsTheStack(presentation: CellActionPresentation) {
    show(stack(), presentation = presentation)

    compose.onNodeWithText("helper — line 10").assertIsDisplayed()
    compose.onNodeWithText("x: int = 41").assertIsDisplayed()
  }

  @Test
  fun theMenuShowsAPlaceholderWithNothingStopped() = assertShowsAPlaceholder(CellActionPresentation.MENU)

  @Test
  fun theSheetShowsAPlaceholderWithNothingStopped() = assertShowsAPlaceholder(CellActionPresentation.SHEET)

  private fun assertShowsAPlaceholder(presentation: CellActionPresentation) {
    show(stack = null, presentation = presentation)

    compose.onNodeWithText("Not stopped").assertIsDisplayed()
  }

  /** `expanded` is what a caller drives, and false renders nothing - [CellActionSurface]'s shape. */
  @Test
  fun collapsedRendersNothing() {
    show(stack(), expanded = false)

    compose.onNodeWithText("helper — line 10").assertDoesNotExist()
  }

  private fun stack() = DebugStack(
    frames = listOf(DebugStackFrame(id = 6, name = "helper", line = 10, column = 1)),
    groups = listOf(DebugVariableGroup(scope = "Locals", variables = listOf(DebugVariable("x", "41", "int", 0)))),
  )

  private fun show(
    stack: DebugStack?,
    expanded: Boolean = true,
    presentation: CellActionPresentation = CellActionPresentation.SHEET,
  ) {
    compose.setContent {
      JupyterDroidTheme {
        DebuggerPanel(expanded = expanded, stack = stack, onDismiss = {}, presentation = presentation)
      }
    }
  }
}
