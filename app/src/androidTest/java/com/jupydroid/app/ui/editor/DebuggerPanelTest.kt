package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

  /**
   * P6a-14's own gate: tapping a compound variable (`variablesReference != 0`) fetches and shows
   * its children, indented one step further - the same drill-down [DebugVariable.variablesReference]
   * was always fetched for and then discarded, per `docs/PLAN.md`'s own Open risks entry.
   */
  @Test
  fun aCompoundVariableExpandsOnTapToShowItsChildren() {
    show(compoundStack(), onExpand = { listOf(DebugVariable("0", "1", "int", 0)) })

    compose.onNodeWithText("items: list = […]").performClick()

    compose.onNodeWithText("0: int = 1").assertIsDisplayed()
  }

  @Test
  fun tappingAnExpandedVariableCollapsesItAgain() {
    show(compoundStack(), onExpand = { listOf(DebugVariable("0", "1", "int", 0)) })
    compose.onNodeWithText("items: list = […]").performClick()
    compose.onNodeWithText("0: int = 1").assertIsDisplayed()

    compose.onNodeWithText("items: list = […]").performClick()

    compose.onNodeWithText("0: int = 1").assertDoesNotExist()
  }

  /** A leaf (`variablesReference == 0`) never fetches anything - there is nothing behind it. */
  @Test
  fun aLeafVariableDoesNotExpandOnTap() {
    var calls = 0
    show(stack(), onExpand = {
      calls++
      emptyList()
    })

    compose.onNodeWithText("x: int = 41").performClick()

    compose.runOnIdle { org.junit.Assert.assertEquals(0, calls) }
  }

  /**
   * P6a-15's own gate: a frame row other than the top one is tappable, and a tap reports that
   * frame's own id - [DebugStack]'s named Open risks gap, closed.
   */
  @Test
  fun tappingANonTopFrameRowSelectsIt() {
    var selected: Int? = null
    show(twoFrameStack(), onSelectFrame = { selected = it })

    compose.onNodeWithText("<module> — line 3").performClick()

    compose.runOnIdle { org.junit.Assert.assertEquals(5, selected) }
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
    expanded: Boolean = true,
    presentation: CellActionPresentation = CellActionPresentation.SHEET,
    onExpand: suspend (Int) -> List<DebugVariable> = { emptyList() },
    onSelectFrame: suspend (Int) -> Unit = {},
  ) {
    compose.setContent {
      JupyterDroidTheme {
        DebuggerPanel(
          expanded = expanded,
          stack = stack,
          onDismiss = {},
          presentation = presentation,
          onExpand = onExpand,
          onSelectFrame = onSelectFrame,
        )
      }
    }
  }
}
