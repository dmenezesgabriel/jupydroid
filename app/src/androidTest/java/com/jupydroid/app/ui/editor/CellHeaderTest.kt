package com.jupydroid.app.ui.editor

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
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
    compose.setContent { CellHeader(CODE_CELL, null, execution = null, {}, {}, {}) }

    compose.onNodeWithContentDescription(MENU_BUTTON).assertIsDisplayed()
  }

  @Test
  fun tappingTheMenuButtonOpensTheActions() {
    compose.setContent { CellHeader(CODE_CELL, null, execution = null, {}, {}, {}) }

    compose.onNodeWithContentDescription(MENU_BUTTON).performClick()

    compose.onNodeWithContentDescription("Cell menu Insert code below").assertIsDisplayed()
    compose.onNodeWithContentDescription("Cell menu Delete").assertIsDisplayed()
  }

  /**
   * A markdown cell has no Run button, and the header used to stop building right where that
   * would have been - so a trailing button would have been dropped for exactly these cells.
   */
  @Test
  fun aMarkdownCellHasTheMenuButtonToo() {
    compose.setContent { CellHeader(MARKDOWN_CELL, null, execution = null, {}, {}, {}) }

    compose.onNodeWithContentDescription(MENU_BUTTON).performClick()

    compose.onNodeWithContentDescription("Cell menu Delete").assertIsDisplayed()
  }

  @Test
  fun choosingAnActionReportsItAndClosesTheMenu() {
    val chosen = mutableListOf<CellAction>()
    compose.setContent { CellHeader(CODE_CELL, null, execution = null, {}, {}, { chosen += it }) }

    compose.onNodeWithContentDescription(MENU_BUTTON).performClick()
    compose.onNodeWithContentDescription("Cell menu Move down").performClick()

    assertEquals(listOf<CellAction>(CellAction.Move(CellMove.DOWN)), chosen)
    compose.onNodeWithContentDescription("Cell menu Delete").assertDoesNotExist()
  }

  private companion object {
    const val MENU_BUTTON = "Cell actions"
    val CODE_CELL = Cell("c1", CellKind.CODE, "x = 1")
    val MARKDOWN_CELL = Cell("c2", CellKind.MARKDOWN, "# title")
  }
}
