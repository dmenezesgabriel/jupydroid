package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Both surfaces offer the same actions and report the same thing when one is chosen.
 *
 * The presentation is passed in rather than read from the window. `currentWindowAdaptiveInfo()`
 * measures the *activity's* window, which a `DeviceConfigurationOverride` around a composable
 * does not change - so a test that tried to force a width would have driven one branch twice
 * while appearing to cover both. The rule that maps width to presentation is pure and tested on
 * the JVM (`CellActionPresentationTest`); what needs a device is that each branch renders and
 * responds, and that is what this asserts.
 */
@RunWith(AndroidJUnit4::class)
class CellActionsTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun theMenuOffersTheActionsAndReportsTheChoice() {
    assertOffersAndReports(CellActionPresentation.MENU)
  }

  @Test
  fun theSheetOffersTheActionsAndReportsTheChoice() {
    assertOffersAndReports(CellActionPresentation.SHEET)
  }

  /**
   * The labels are identical on both, which is the point of branching only the presentation: one
   * Maestro flow and one accessibility vocabulary cover a phone and a tablet.
   */
  private fun assertOffersAndReports(presentation: CellActionPresentation) {
    val chosen = mutableListOf<CellAction>()
    compose.setContent {
      CellActions(
        expanded = true,
        cell = CODE_CELL,
        canPaste = false,
        onDismiss = {},
        onAction = { chosen += it },
        presentation = presentation,
      )
    }

    compose.onNodeWithContentDescription("Cell menu Move down").performScrollTo().assertIsDisplayed()
    // Delete is the last of thirteen and the one that fell off the bottom when the list grew, so
    // it is the item worth proving reachable. Asserted here rather than in the Maestro flow: a
    // scroll gesture on a bottom sheet is a dismiss gesture, and driving one there closed the
    // sheet. A semantics scroll has no such side effect.
    compose.onNodeWithContentDescription("Cell menu Delete").performScrollTo().assertIsDisplayed()
    compose.onNodeWithContentDescription("Cell menu Merge with above").performScrollTo().performClick()

    compose.runOnIdle { assertEquals(listOf(CellAction.Merge(MergeInto.ABOVE)), chosen) }
  }

  @Test
  fun theMenuHidesPasteWithAnEmptyClipboard() {
    assertHidesPasteWithNothingToPaste(CellActionPresentation.MENU)
  }

  /**
   * Asserted per surface rather than in a loop: `setContent` may be called once per rule, so a
   * loop would have driven the first presentation and silently skipped the second.
   */
  @Test
  fun theSheetHidesPasteWithAnEmptyClipboard() {
    assertHidesPasteWithNothingToPaste(CellActionPresentation.SHEET)
  }

  private fun assertHidesPasteWithNothingToPaste(presentation: CellActionPresentation) {
    compose.setContent {
      CellActions(
        expanded = true,
        cell = CODE_CELL,
        canPaste = false,
        onDismiss = {},
        onAction = {},
        presentation = presentation,
      )
    }

    compose.onNodeWithContentDescription("Cell menu Copy").performScrollTo().assertIsDisplayed()
    compose.onNodeWithContentDescription("Cell menu Paste below").assertDoesNotExist()
  }

  /**
   * Split reports [CellAction.Split], which carries nothing: where to cut is the cell's own editor
   * and arrives beside the action.
   */
  @Test
  fun splitIsOfferedOnACodeCell() {
    val chosen = mutableListOf<CellAction>()
    compose.setContent {
      CellActions(
        expanded = true,
        cell = CODE_CELL,
        canPaste = false,
        onDismiss = {},
        onAction = { chosen += it },
        presentation = CellActionPresentation.MENU,
      )
    }

    compose.onNodeWithContentDescription("Cell menu Split cell").performScrollTo().performClick()

    compose.runOnIdle { assertEquals(listOf(CellAction.Split), chosen) }
  }

  /**
   * **A markdown cell is offered it too**, which is the reference's `isEnabled`
   * (`!!current.content.activeCell?.editor`): rendering swaps which widget is *displayed* -
   * `renderInput` and `showEditor` toggle a class and call `inputArea.renderInput/showEditor`
   * (`packages/cells/src/widget.ts:2489-2503`) - and `inputArea.editor` exists either way. So a
   * rendered cell has a caret to split at, wherever it was last left.
   */
  @Test
  fun aMarkdownCellIsOfferedSplitToo() {
    compose.setContent {
      CellActions(
        expanded = true,
        cell = MARKDOWN_CELL,
        canPaste = false,
        onDismiss = {},
        onAction = {},
        presentation = CellActionPresentation.SHEET,
      )
    }

    compose.onNodeWithContentDescription("Cell menu Split cell").performScrollTo().assertIsDisplayed()
  }

  private companion object {
    val CODE_CELL = Cell(id = "c1", kind = CellKind.CODE, source = "print(1)")
    val MARKDOWN_CELL = Cell(id = "m1", kind = CellKind.MARKDOWN, source = "# notes")
  }
}
