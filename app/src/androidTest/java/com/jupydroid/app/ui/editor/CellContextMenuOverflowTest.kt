package com.jupydroid.app.ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.withKeyDown
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The accessibility context menu's fullest state - the kernel answering the inspect rows and the
 * clipboard non-empty - is the one Compose's 32-custom-action cap
 * (`AndroidComposeViewAccessibilityDelegateCompat`) crashes on once the heading row expands to
 * six. The cap keeps that row as the single `Change to heading…` action, which opens the real
 * picker - and a cell's `inspectable` copy is covered by `CellHeaderTest`.
 */
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class) // `performKeyInput`'s own opt-in requirement
class CellContextMenuOverflowTest : NotebookKeyFixture() {

  /** The fullest list stays under the cap and keeps the picker's single row. */
  @Test
  fun theOverflowingListFitsByKeepingTheHeadingRow() {
    overflowedCell()

    val labels = contextLabels()

    assertTrue("${labels.size} actions exceeds the cap", labels.size < 32)
    assertTrue(CHANGE_TO_HEADING in labels)
    assertFalse("Heading 1" in labels)
  }

  /** The row is live: the second step it asks for is the picker's own surface. */
  @Test
  fun theRowOpensTheHeadingPicker() {
    overflowedCell()

    contextAction(CHANGE_TO_HEADING).action()

    val heading = compose.onNodeWithContentDescription("Cell menu Heading 1")
    heading.assertIsDisplayed()
    heading.performClick()
    compose.runOnIdle {
      assertEquals(CellKind.MARKDOWN, viewModel.uiState.value.cells[0].kind)
      assertEquals("# pass", viewModel.uiState.value.cells[0].source)
    }
  }

  /** The fullest case: the kernel has connected (inspect rows) and the clipboard holds a cell. */
  private fun overflowedCell() {
    showEditing(CELL)
    field(0).performKeyInput { pressKey(Key.Escape) }
    headFrame().performKeyInput { withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) } }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.kernelConnected }
    headFrame().performKeyInput { pressKey(Key.C) }
    compose.waitUntil(TIMEOUT_MILLIS) { viewModel.uiState.value.clipboard.isNotEmpty() }
  }

  private fun contextLabels(): List<String> = contextActions().map { it.label }

  private fun contextAction(label: String): CustomAccessibilityAction = contextActions().first { it.label == label }

  private fun contextActions(): List<CustomAccessibilityAction> =
    cellFrame("c1").fetchSemanticsNode().config[SemanticsActions.CustomActions]
}
