package com.jupydroid.app.ui.editor

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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The one-pane adaptation of [DebuggerSidePanel][com.jupydroid.app.ui.shell.DebuggerSidePanel] -
 * a single non-modal surface docked above [DebuggerControlBar] (`EditorBottomBars`' own caller),
 * not the old menu/sheet split [CellActionPresentation] drove: unlike a cell's own actions, this
 * has no button of its own to anchor a `DropdownMenu` to and is never worth blocking the notebook
 * or the controls underneath for, so one shape covers every one-pane width.
 */
@RunWith(AndroidJUnit4::class)
class DebuggerVariablesSheetTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun showsTheStack() {
    show(stack())

    compose.onNodeWithText("helper — line 10").assertIsDisplayed()
    compose.onNodeWithText("x: int = 41").assertIsDisplayed()
  }

  @Test
  fun showsAPlaceholderWithNothingStopped() {
    show(stack = null)

    compose.onNodeWithText("Not stopped").assertIsDisplayed()
  }

  /** A compound variable expands and collapses on tap. */
  @Test
  fun aCompoundVariableExpandsOnTapToShowItsChildren() {
    show(compoundStack(), onExpand = { listOf(DebugVariable("0", "1", "int", 0)) })

    compose.onNodeWithText("items: list = […]").performClick()

    compose.onNodeWithText("0: int = 1").assertIsDisplayed()
  }

  /** A non-top frame row is tappable and reports its id. */
  @Test
  fun tappingANonTopFrameRowSelectsIt() {
    var selected: Int? = null
    show(twoFrameStack(), onSelectFrame = { selected = it })

    compose.onNodeWithText("<module> — line 3").performClick()

    compose.runOnIdle { org.junit.Assert.assertEquals(5, selected) }
  }

  /** A submitted expression's answer renders as a row. */
  @Test
  fun submittingAnExpressionShowsItsAnswer() {
    show(stack(), onEvaluate = { expression -> DebugVariable(expression, "42", "int", 0) })

    compose.onNodeWithTag(EVALUATE_FIELD_TAG).performTextInput("6 * 7")
    compose.onNodeWithTag(EVALUATE_FIELD_TAG).performImeAction()

    compose.onNodeWithText("6 * 7: int = 42").assertIsDisplayed()
  }

  private fun stack() = DebugStack(
    frames = listOf(DebugStackFrame(id = 6, name = "helper", line = 10, column = 1)),
    groups = listOf(DebugVariableGroup(scope = "Locals", variables = listOf(DebugVariable("x", "41", "int", 0)))),
  )

  private fun twoFrameStack() = DebugStack(
    frames = listOf(
      DebugStackFrame(id = 6, name = "helper", line = 10, column = 1),
      DebugStackFrame(id = 5, name = "<module>", line = 3, column = 1),
    ),
    groups = listOf(DebugVariableGroup(scope = "Locals", variables = listOf(DebugVariable("x", "41", "int", 0)))),
  )

  private fun compoundStack() = DebugStack(
    frames = listOf(DebugStackFrame(id = 6, name = "helper", line = 10, column = 1)),
    groups = listOf(
      DebugVariableGroup(scope = "Locals", variables = listOf(DebugVariable("items", "[…]", "list", 5))),
    ),
  )

  private fun show(
    stack: DebugStack?,
    onExpand: suspend (Int) -> List<DebugVariable> = { emptyList() },
    onSelectFrame: suspend (Int) -> Unit = {},
    onEvaluate: suspend (String) -> DebugVariable? = { null },
  ) {
    compose.setContent {
      JupyterDroidTheme {
        DebuggerVariablesSheet(
          stack = stack,
          onExpand = onExpand,
          onSelectFrame = onSelectFrame,
          onEvaluate = onEvaluate,
        )
      }
    }
  }
}
