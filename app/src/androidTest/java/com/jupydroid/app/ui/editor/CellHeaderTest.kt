package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The first instrumented test in the project, and it earns its keep: the thing under test is an
 * affordance - whether there is something visible to press - which no JVM test can answer.
 *
 * The menu was briefly reachable only by long-pressing the header. That is gone: nothing on
 * screen said the strip was interactive, and a gesture on the Row made the whole header one
 * target, which would have fought every button added beside it later.
 *
 * Where the menu *lands* is not asserted here and cannot be: a popup gets its own window, and
 * `getUnclippedBoundsInRoot` reports coordinates inside that window - a menu pinned to the far
 * left of the cell and one under the button both read `[0.dp..175.dp]`. That was found by
 * writing the assertion, watching it fail identically before and after the fix, and checking
 * the device. Anchoring is evidenced by screenshot instead: `evidence/step4b-cell-menu/`.
 */
@RunWith(AndroidJUnit4::class)
class CellHeaderTest {
  @get:Rule val compose = createComposeRule()

  @Test
  fun showsAVisibleMenuButton() {
    compose.setContent { CellHeader(CODE_CELL, null, execution = null, canPaste = false, mark = null, {}, {}, {}, {}) }

    compose.onNodeWithContentDescription(MENU_BUTTON).assertIsDisplayed()
  }

  @Test
  fun tappingTheMenuButtonOpensTheActions() {
    compose.setContent { CellHeader(CODE_CELL, null, execution = null, canPaste = false, mark = null, {}, {}, {}, {}) }

    compose.onNodeWithContentDescription(MENU_BUTTON).performClick()

    compose.onNodeWithContentDescription("Cell menu Insert code below").assertIsDisplayed()
    // Scrolled to, and that is a finding rather than a workaround: the menu grew from eight
    // items to thirteen when insert-above, change-type, merge and cut/copy/paste arrived, so
    // Delete no longer fits on a phone-height screen. `DropdownMenu` scrolls, so the action is
    // still reachable - but a thirteen-item dropdown is the argument for U5b's bottom sheet,
    // and this assertion is where that argument is recorded.
    compose.onNodeWithContentDescription("Cell menu Delete").performScrollTo().assertIsDisplayed()
  }

  /**
   * A markdown cell has no Run button, and the header used to stop building right where that
   * would have been - so a trailing button would have been dropped for exactly these cells.
   */
  @Test
  fun aMarkdownCellHasTheMenuButtonToo() {
    compose.setContent { CellHeader(MARKDOWN_CELL, null, execution = null, canPaste = false, mark = null, {}, {}, {}, {}) }

    compose.onNodeWithContentDescription(MENU_BUTTON).performClick()

    compose.onNodeWithContentDescription("Cell menu Delete").performScrollTo().assertIsDisplayed()
  }

  /**
   * **A raw cell has no Run control at all**, which markdown does and code does.
   * `runCell`'s switch answers `markdown` by rendering and `code` by executing, and falls to
   * `default: break` for raw (`packages/notebook/src/cellexecutor.ts:36-141`), so a Run button
   * here would be a control that lights up and does nothing - the thing this app already refuses
   * to ship in `cellActionItems`. It keeps the `\u22ee`: every other cell operation still applies.
   */
  @Test
  fun aRawCellHasNoRunControlButKeepsItsMenu() {
    compose.setContent { CellHeader(RAW_CELL, null, execution = null, canPaste = false, mark = null, {}, {}, {}, {}) }

    compose.onNodeWithContentDescription(RUN_BUTTON).assertDoesNotExist()
    compose.onNodeWithContentDescription(MENU_BUTTON).assertIsDisplayed()
  }

  /** Markdown does get one - running it renders it, which is a real thing to do. */
  @Test
  fun aMarkdownCellDoesHaveARunControl() {
    compose.setContent { CellHeader(MARKDOWN_CELL, null, execution = null, canPaste = false, mark = null, {}, {}, {}, {}) }

    compose.onNodeWithContentDescription(RUN_BUTTON).assertIsDisplayed()
  }

  @Test
  fun choosingAnActionReportsItAndClosesTheMenu() {
    val chosen = mutableListOf<CellAction>()
    compose.setContent { CellHeader(CODE_CELL, null, execution = null, canPaste = false, mark = null, {}, {}, {}, { chosen += it }) }

    compose.onNodeWithContentDescription(MENU_BUTTON).performClick()
    compose.onNodeWithContentDescription("Cell menu Move down").performClick()

    assertEquals(listOf<CellAction>(CellAction.Move(CellMove.DOWN)), chosen)
    compose.onNodeWithContentDescription("Cell menu Delete").assertDoesNotExist()
  }

  /**
   * While a run is being marked the cell offers a tick and nothing else: the `⋮` would be a second
   * control acting on one cell while the bar above acts on all of them, which is two answers to
   * "what does this menu apply to".
   */
  @Test
  fun whileSelecting_theCellShowsATickInsteadOfTheMenuButton() {
    compose.setContent {
      CellHeader(CODE_CELL, null, execution = null, canPaste = false, mark = MARK, {}, {}, {}, {})
    }

    compose.onNodeWithContentDescription("Select cell 2").assertIsDisplayed()
    compose.onNodeWithContentDescription(MENU_BUTTON).assertDoesNotExist()
  }

  @Test
  fun withNoSelection_thereIsNoTick() {
    compose.setContent {
      CellHeader(CODE_CELL, null, execution = null, canPaste = false, mark = null, {}, {}, {}, {})
    }

    compose.onNodeWithContentDescription("Select cell 2").assertDoesNotExist()
  }

  /** The tick has to *say* whether the cell is in the run, not only look it. */
  @Test
  fun theTickCarriesWhetherTheCellIsInTheRun() {
    compose.setContent {
      CellHeader(CODE_CELL, null, execution = null, canPaste = false, mark = MARK, {}, {}, {}, {})
    }

    compose.onNodeWithContentDescription("Select cell 2").assertIsOn()
  }

  @Test
  fun tappingTheTickReportsIt() {
    var toggled = 0
    compose.setContent {
      CellHeader(CODE_CELL, null, execution = null, canPaste = false, MARK, { toggled++ }, {}, {}, {})
    }

    compose.onNodeWithContentDescription("Select cell 2").performClick()

    assertEquals(1, toggled)
  }

  // --- the heading collapser (P2f-1) -----------------------------------------------------

  /**
   * Whether there is something visible to press, which is what this suite exists for. The
   * reference creates its collapser only for `level > 0` (`packages/cells/src/widget.ts:2387-2408`),
   * so prose and code get none.
   */
  @Test
  fun onlyAHeadingCarriesTheCollapser() {
    compose.setContent {
      Column {
        CellHeader(HEADING_CELL, null, execution = null, canPaste = false, mark = null, {}, {}, {}, {})
        CellHeader(PROSE_CELL, null, execution = null, canPaste = false, mark = null, {}, {}, {}, {})
        CellHeader(CODE_CELL, null, execution = null, canPaste = false, mark = null, {}, {}, {}, {})
      }
    }

    compose.onAllNodesWithContentDescription(COLLAPSE_HEADING).assertCountEquals(1)
  }

  /** The label names what a tap will do, so it is the other word once the section is folded. */
  @Test
  fun aCollapsedHeadingOffersToExpandInstead() {
    compose.setContent {
      CellHeader(HEADING_CELL.copy(headingCollapsed = true), null, null, false, null, {}, {}, {}, {})
    }

    compose.onNodeWithContentDescription(EXPAND_HEADING).assertIsDisplayed()
  }

  @Test
  fun tappingTheCollapserAsksForTheToggle() {
    val asked = mutableListOf<CellAction>()
    compose.setContent {
      CellHeader(HEADING_CELL, null, null, false, null, {}, {}, {}) { asked += it }
    }

    compose.onNodeWithContentDescription(COLLAPSE_HEADING).performClick()

    assertEquals(listOf(CellAction.ToggleHeadingCollapse), asked)
  }

  private companion object {
    const val MENU_BUTTON = "Cell actions"
    const val RUN_BUTTON = "Run cell"
    val MARK = CellMark(checked = true, position = 2)
    val CODE_CELL = Cell("c1", CellKind.CODE, "x = 1")
    val MARKDOWN_CELL = Cell("c2", CellKind.MARKDOWN, "# title")
    val RAW_CELL = Cell("c3", CellKind.RAW, "\\begin{equation}")
    val HEADING_CELL = Cell("h1", CellKind.MARKDOWN, "## Results")
    val PROSE_CELL = Cell("p1", CellKind.MARKDOWN, "just some prose")
  }
}
