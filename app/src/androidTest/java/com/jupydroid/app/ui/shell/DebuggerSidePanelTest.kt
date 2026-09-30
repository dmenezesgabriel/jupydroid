package com.jupydroid.app.ui.shell

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.DebugStack
import com.jupydroid.app.domain.model.DebugVariableGroup
import com.jupydroid.app.domain.port.DebugStackFrame
import com.jupydroid.app.domain.port.DebugVariable
import com.jupydroid.app.theme.JupyterDroidTheme
import com.jupydroid.app.ui.editor.EVALUATE_FIELD_TAG
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

  /** The side panel's own compound-variable drill-down - [DebuggerPanelTest]'s own case. */
  @Test
  fun aCompoundVariableExpandsOnTapToShowItsChildren() {
    val compound = DebugStack(
      frames = listOf(DebugStackFrame(id = 5, name = "<module>", line = 3, column = 1)),
      groups = listOf(
        DebugVariableGroup(scope = "Locals", variables = listOf(DebugVariable("items", "[…]", "list", 5))),
      ),
    )
    show(compound, onExpand = { listOf(DebugVariable("0", "1", "int", 0)) })

    compose.onNodeWithText("items: list = […]").performClick()

    compose.onNodeWithText("0: int = 1").assertIsDisplayed()
  }

  /** The side panel's own frame reselection - [DebuggerPanelTest]'s own case. */
  @Test
  fun tappingANonTopFrameRowSelectsIt() {
    var selected: Int? = null
    show(stack(), onSelectFrame = { selected = it })

    compose.onNodeWithText("helper — line 10").performClick()

    compose.runOnIdle { org.junit.Assert.assertEquals(6, selected) }
  }

  /** The side panel's own ad hoc expression - [DebuggerPanelTest]'s own case. */
  @Test
  fun submittingAnExpressionShowsItsAnswer() {
    show(stack(), onEvaluate = { expression -> DebugVariable(expression, "42", "int", 0) })

    compose.onNodeWithTag(EVALUATE_FIELD_TAG).performTextInput("6 * 7")
    compose.onNodeWithTag(EVALUATE_FIELD_TAG).performImeAction()

    compose.onNodeWithText("6 * 7: int = 42").assertIsDisplayed()
  }

  private fun stack() = DebugStack(
    frames = listOf(
      DebugStackFrame(id = 5, name = "<module>", line = 3, column = 1),
      DebugStackFrame(id = 6, name = "helper", line = 10, column = 1),
    ),
    groups = listOf(DebugVariableGroup(scope = "Locals", variables = listOf(DebugVariable("x", "41", "int", 0)))),
  )

  private fun show(
    stack: DebugStack?,
    onExpand: suspend (Int) -> List<DebugVariable> = { emptyList() },
    onSelectFrame: suspend (Int) -> Unit = {},
    onEvaluate: suspend (String) -> DebugVariable? = { null },
  ) {
    compose.setContent {
      JupyterDroidTheme {
        DebuggerSidePanel(stack = stack, onExpand = onExpand, onSelectFrame = onSelectFrame, onEvaluate = onEvaluate)
      }
    }
  }
}
