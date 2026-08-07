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
 * where the flags are stored, `CellEditsTest` the toggles and `CollapsedPreviewTest` the line
 * that names the fold; this covers what is on screen.
 */
@RunWith(AndroidJUnit4::class)
class CollapsedCellTest {
  @get:Rule val compose = createComposeRule()

  /** A folded notebook of identical stubs would be unusable; the preview is what names it. */
  @Test
  fun showsThePreviewOfWhatIsFolded() {
    compose.setContent { CollapsedCell("import numpy as np", COLLAPSED_SOURCE, onExpand = {}) }

    compose.onNodeWithText("import numpy as np").assertIsDisplayed()
  }

  @Test
  fun tappingItAsksToExpand() {
    var asked = 0
    compose.setContent { CollapsedCell("x = 1", COLLAPSED_SOURCE, onExpand = { asked++ }) }

    compose.onNodeWithContentDescription(COLLAPSED_SOURCE).performClick()

    assertEquals(1, asked)
  }

  /** An output with no string form previews as nothing, and still has to be pressable. */
  @Test
  fun anEmptyPreviewIsStillTappable() {
    compose.setContent { CollapsedCell("", COLLAPSED_OUTPUTS, onExpand = {}) }

    compose.onNodeWithContentDescription(COLLAPSED_OUTPUTS).assertIsDisplayed()
  }

  @Test
  fun theMenuOffersBothCollapsesOnAnExpandedCodeCell() {
    compose.setContent { CellHeader(CODE_CELL, null, execution = null, {}, {}, {}) }

    compose.onNodeWithContentDescription("Cell actions").performClick()

    compose.onNodeWithContentDescription("Cell menu $COLLAPSE_CODE").assertIsDisplayed()
    compose.onNodeWithContentDescription("Cell menu $COLLAPSE_OUTPUTS").assertIsDisplayed()
  }

  /** The label is the verb, so a folded cell offers the opposite one and only that one. */
  @Test
  fun theMenuOffersExpandOnceTheSourceIsCollapsed() {
    compose.setContent { CellHeader(CODE_CELL.copy(sourceHidden = true), null, null, {}, {}, {}) }

    compose.onNodeWithContentDescription("Cell actions").performClick()

    compose.onNodeWithContentDescription("Cell menu $EXPAND_CODE").assertIsDisplayed()
    compose.onNodeWithContentDescription("Cell menu $COLLAPSE_CODE").assertDoesNotExist()
  }

  /** The two are independent: folding the source must not relabel the outputs' item. */
  @Test
  fun theTwoCollapsesAreLabelledIndependently() {
    compose.setContent { CellHeader(CODE_CELL.copy(outputsHidden = true), null, null, {}, {}, {}) }

    compose.onNodeWithContentDescription("Cell actions").performClick()

    compose.onNodeWithContentDescription("Cell menu $EXPAND_OUTPUTS").assertIsDisplayed()
    compose.onNodeWithContentDescription("Cell menu $COLLAPSE_CODE").assertIsDisplayed()
  }

  /**
   * JupyterLab's collapse actions all check `cell.model.type === 'code'`
   * (`packages/notebook/src/actions.tsx:1877`, `:1957`). A markdown cell already has rendered
   * and source, and never has outputs.
   */
  @Test
  fun theMenuOffersNeitherCollapseOnAMarkdownCell() {
    compose.setContent { CellHeader(MARKDOWN_CELL, null, execution = null, {}, {}, {}) }

    compose.onNodeWithContentDescription("Cell actions").performClick()

    compose.onNodeWithContentDescription("Cell menu $COLLAPSE_CODE").assertDoesNotExist()
    compose.onNodeWithContentDescription("Cell menu $COLLAPSE_OUTPUTS").assertDoesNotExist()
    compose.onNodeWithContentDescription("Cell menu Delete").assertIsDisplayed()
  }

  private companion object {
    val CODE_CELL = Cell("c1", CellKind.CODE, "x = 1")
    val MARKDOWN_CELL = Cell("c2", CellKind.MARKDOWN, "# title")
  }
}
