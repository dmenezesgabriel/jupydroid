package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Collapse is an affordance question - is there anything left to press, and does it say which
 * cell it is - which is exactly the kind no JVM test can answer. `CellCollapseTest` covers
 * where the flag is stored and `CellEditsTest` covers the toggle; this covers what is on screen.
 */
@RunWith(AndroidJUnit4::class)
class CollapsedCellTest {
  @get:Rule val compose = createComposeRule()

  /** A folded notebook of identical stubs would be unusable; the first line is what names it. */
  @Test
  fun showsTheFirstLineOfTheSource() {
    compose.setContent { CollapsedCell(source = "import numpy as np\nx = np.eye(3)", onExpand = {}) }

    compose.onNodeWithText("import numpy as np").assertIsDisplayed()
    compose.onNodeWithText("x = np.eye(3)").assertDoesNotExist()
  }

  @Test
  fun tappingItAsksToExpand() {
    var asked = 0
    compose.setContent { CollapsedCell(source = "x = 1", onExpand = { asked++ }) }

    compose.onNodeWithContentDescription(COLLAPSED_SOURCE).performClick()

    assertEquals(1, asked)
  }

  /** An empty cell still has to leave something to press, or it collapses to nothing at all. */
  @Test
  fun anEmptyCellIsStillTappable() {
    compose.setContent { CollapsedCell(source = "", onExpand = {}) }

    compose.onNodeWithContentDescription(COLLAPSED_SOURCE).assertIsDisplayed()
  }

  @Test
  fun theMenuOffersCollapseOnAnExpandedCodeCell() {
    compose.setContent { CellHeader(CODE_CELL, null, running = false, {}, {}, {}) }

    compose.onNodeWithContentDescription("Cell actions").performClick()

    compose.onNodeWithContentDescription("Cell menu $COLLAPSE_CODE").assertIsDisplayed()
  }

  /** The label is the verb, so a collapsed cell offers the opposite one and only that one. */
  @Test
  fun theMenuOffersExpandOnceTheCellIsCollapsed() {
    compose.setContent { CellHeader(CODE_CELL.copy(sourceHidden = true), null, false, {}, {}, {}) }

    compose.onNodeWithContentDescription("Cell actions").performClick()

    compose.onNodeWithContentDescription("Cell menu $EXPAND_CODE").assertIsDisplayed()
    compose.onNodeWithContentDescription("Cell menu $COLLAPSE_CODE").assertDoesNotExist()
  }

  /**
   * JupyterLab's collapse actions all check `cell.model.type === 'code'`
   * (`packages/notebook/src/actions.tsx:1877`). A markdown cell already has rendered and source.
   */
  @Test
  fun theMenuDoesNotOfferCollapseOnAMarkdownCell() {
    compose.setContent { CellHeader(MARKDOWN_CELL, null, running = false, {}, {}, {}) }

    compose.onNodeWithContentDescription("Cell actions").performClick()

    compose.onNodeWithContentDescription("Cell menu $COLLAPSE_CODE").assertDoesNotExist()
    compose.onNodeWithContentDescription("Cell menu Delete").assertIsDisplayed()
  }

  private companion object {
    val CODE_CELL = Cell("c1", CellKind.CODE, "x = 1")
    val MARKDOWN_CELL = Cell("c2", CellKind.MARKDOWN, "# title")
  }
}
