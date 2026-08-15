package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
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
 * The bar that replaces the notebook's own while a run of cells is marked.
 *
 * Instrumented rather than JVM for the reason `CellHeaderTest` is: what is being asserted is
 * whether there is something on screen to press, and how many cells it says it will act on -
 * neither of which a pure function can answer.
 */
@RunWith(AndroidJUnit4::class)
class SelectionTopBarTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun saysHowManyCellsAreMarked() {
    compose.setContent { bar(selectedCount = 3) }

    compose.onNodeWithText("3 selected").assertIsDisplayed()
  }

  /**
   * *"Move Cell Up"* / *"Move Cells Up"* - the reference pluralises its labels off the selection
   * with `trans._n` (`packages/notebook-extension/src/index.ts:4287-4292`), and a count that says
   * "1 cells" is the first thing anyone notices about a bar like this.
   */
  @Test
  fun oneCellIsSingular() {
    compose.setContent { bar(selectedCount = 1) }

    compose.onNodeWithText("1 cell selected").assertIsDisplayed()
  }

  @Test
  fun theCloseControlDeselectsEverything() {
    var deselected = 0
    compose.setContent { bar(selectedCount = 2, onDeselectAll = { deselected++ }) }

    compose.onNodeWithContentDescription("Deselect all cells").performClick()

    assertEquals(1, deselected)
  }

  @Test
  fun selectAllIsOnTheBarItself() {
    var all = 0
    compose.setContent { bar(selectedCount = 2, onSelectAll = { all++ }) }

    compose.onNodeWithContentDescription("Select all cells").performClick()

    assertEquals(1, all)
  }

  /**
   * All four `notebook:extend-marked-cells-*` are overflow rows rather than buttons, and that is a
   * measurement: with four arrows on the bar the count wrapped to three lines at 360dp
   * (`evidence/p2c-multi-select/x86_64/`, before this). A tick on the cell is the extend gesture a
   * touch user reaches for anyway.
   */
  @Test
  fun theOverflowCarriesAllFourReaches() {
    val reached = mutableListOf<SelectionReach>()
    compose.setContent { bar(selectedCount = 2, onExtend = { reached += it }) }

    compose.onNodeWithContentDescription(MENU).performClick()
    compose.onNodeWithContentDescription("Cell menu Extend to top").performClick()
    compose.onNodeWithContentDescription(MENU).performClick()
    compose.onNodeWithContentDescription("Cell menu Extend below").performClick()

    assertEquals(listOf(SelectionReach.TOP, SelectionReach.BELOW), reached)
  }

  /** Two actions and a count, so a three-figure selection still has a bar to sit on. */
  @Test
  fun theBarCarriesOnlyTheTwoControlsThatFit() {
    compose.setContent { bar(selectedCount = 2) }

    compose.onNodeWithContentDescription("Select all cells").assertIsDisplayed()
    compose.onNodeWithContentDescription(MENU).assertIsDisplayed()
    compose.onNodeWithContentDescription("Extend selection above").assertDoesNotExist()
  }

  /**
   * The rows say how many cells they will take, the way the reference's `trans._n` labels do
   * (`packages/notebook-extension/src/index.ts:3733`) - a menu reading "Delete" over a bar reading
   * "2 selected" is one of them lying.
   */
  @Test
  fun theOverflowCarriesTheCellActionsAndSaysHowManyItMeans() {
    val chosen = mutableListOf<CellAction>()
    compose.setContent { bar(selectedCount = 2, onCellAction = { chosen += it }) }

    compose.onNodeWithContentDescription(MENU).performClick()
    compose.onNodeWithContentDescription("Cell menu Delete cells").performScrollTo().performClick()

    assertEquals(listOf<CellAction>(CellAction.Delete), chosen)
  }

  /**
   * With a run marked, "Merge with above" and "Merge with below" would do the same thing - the
   * direction is only consulted for a single cell (`actions.tsx:390-425`) - so the two rows become
   * the reference's own third command, `notebook:merge-cells`.
   */
  @Test
  fun theOverflowOffersOneMergeRatherThanTwoThatAgree() {
    compose.setContent { bar(selectedCount = 2) }

    compose.onNodeWithContentDescription(MENU).performClick()

    compose.onNodeWithContentDescription("Cell menu $MERGE_SELECTED").performScrollTo().assertIsDisplayed()
    compose.onNodeWithContentDescription("Cell menu Merge with above").assertDoesNotExist()
  }

  /**
   * "Select cells" is what got here, so offering it again is a control that lies - taking it would
   * drop the anchor back onto the head and collapse the run to one cell.
   */
  @Test
  fun theOverflowDoesNotOfferToStartSelectingAgain() {
    compose.setContent { bar(selectedCount = 2) }

    compose.onNodeWithContentDescription(MENU).performClick()

    compose.onNodeWithContentDescription("Cell menu $SELECT_CELLS").assertDoesNotExist()
  }

  @Suppress("LongParameterList")
  @androidx.compose.runtime.Composable
  private fun bar(
    selectedCount: Int,
    onDeselectAll: () -> Unit = {},
    onSelectAll: () -> Unit = {},
    onExtend: (SelectionReach) -> Unit = {},
    onCellAction: (CellAction) -> Unit = {},
  ) = SelectionTopBar(
    selectedCount = selectedCount,
    headCell = HEAD_CELL,
    canPaste = false,
    onDeselectAll = onDeselectAll,
    onSelectAll = onSelectAll,
    onExtend = onExtend,
    onCellAction = onCellAction,
  )

  private companion object {
    const val MENU = "Selected cell actions"
    val HEAD_CELL = Cell("c1", CellKind.CODE, "x = 1")
  }
}
