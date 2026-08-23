package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.Instant

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
    compose.setContent { CellHeader(CODE_CELL, execution = null, canPaste = false, mark = null, {}, {}) }

    compose.onNodeWithContentDescription(MENU_BUTTON).assertIsDisplayed()
  }

  @Test
  fun tappingTheMenuButtonOpensTheActions() {
    compose.setContent { CellHeader(CODE_CELL, execution = null, canPaste = false, mark = null, {}, {}) }

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
    compose.setContent {
      CellHeader(MARKDOWN_CELL, execution = null, canPaste = false, mark = null, {}, {})
    }

    compose.onNodeWithContentDescription(MENU_BUTTON).performClick()

    compose.onNodeWithContentDescription("Cell menu Delete").performScrollTo().assertIsDisplayed()
  }

  /**
   * Raw gets no Run anywhere - the gutter draws it nothing (`cellexecutor.ts:36-141` falls through
   * `default: break`) - and since P2v-1 that is true of every kind's *header*; what raw keeps here
   * is the `⋮`, because every other cell operation still applies.
   */
  @Test
  fun aRawCellHasNoRunControlButKeepsItsMenu() {
    compose.setContent { CellHeader(RAW_CELL, execution = null, canPaste = false, mark = null, {}, {}) }

    compose.onNodeWithContentDescription(RUN_BUTTON).assertDoesNotExist()
    compose.onNodeWithContentDescription(MENU_BUTTON).assertIsDisplayed()
  }

  /**
   * **No header carries a Run control any more** - P2v-1 moved it into the gutter left of the
   * editor ([CellGutterTest] holds the gutter's own assertions). The header keeps the duration and
   * the menu; raw never had Run and markdown lost it with the rest, since running it renders it
   * and that button lives where every other one does now.
   */
  @Test
  fun noHeaderCarriesARunControlAnyMore() {
    compose.setContent {
      Column {
        CellHeader(CODE_CELL, execution = null, canPaste = false, mark = null, {}, {})
        CellHeader(MARKDOWN_CELL, execution = null, canPaste = false, mark = null, {}, {})
      }
    }

    // One matcher over both headers: zero matches passes here, and any Run at all - on either
    // cell - fails this either as a second match or as a found node.
    compose.onNodeWithContentDescription(RUN_BUTTON).assertDoesNotExist()
  }

  @Test
  fun choosingAnActionReportsItAndClosesTheMenu() {
    val chosen = mutableListOf<CellAction>()
    compose.setContent {
      CellHeader(CODE_CELL, execution = null, canPaste = false, mark = null, {}, {
        chosen +=
          it
      })
    }

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
      CellHeader(CODE_CELL, execution = null, canPaste = false, mark = MARK, {}, {})
    }

    compose.onNodeWithContentDescription("Select cell 2").assertIsDisplayed()
    compose.onNodeWithContentDescription(MENU_BUTTON).assertDoesNotExist()
  }

  @Test
  fun withNoSelection_thereIsNoTick() {
    compose.setContent {
      CellHeader(CODE_CELL, execution = null, canPaste = false, mark = null, {}, {})
    }

    compose.onNodeWithContentDescription("Select cell 2").assertDoesNotExist()
  }

  /** The tick has to *say* whether the cell is in the run, not only look it. */
  @Test
  fun theTickCarriesWhetherTheCellIsInTheRun() {
    compose.setContent {
      CellHeader(CODE_CELL, execution = null, canPaste = false, mark = MARK, {}, {})
    }

    compose.onNodeWithContentDescription("Select cell 2").assertIsOn()
  }

  @Test
  fun tappingTheTickReportsIt() {
    var toggled = 0
    compose.setContent {
      CellHeader(CODE_CELL, execution = null, canPaste = false, MARK, { toggled++ }, {})
    }

    compose.onNodeWithContentDescription("Select cell 2").performClick()

    assertEquals(1, toggled)
  }

  // --- what the strip no longer carries (P2v-2) ----------------------------------------

  /**
   * **No header draws a bracket any more.** The prompt left for the gutter's foot in P2v-2 -
   * `[7]:` above the editor and `[7]` at its foot are one fact twice - so a cell that has run
   * twelve times says so below its editor ([CellGutterTest] holds that side), and nothing above
   * it repeats the number. The alignment tests this replaces (`everyCodePromptInOneFrame…`,
   * `aMarkedRunKeepsEveryPromptInLine`) pinned brackets that no longer exist.
   */
  @Test
  fun aHeaderDrawsNoBracketAnyMore() {
    compose.setContent {
      Column {
        CellHeader(CODE_CELL, execution = null, canPaste = false, mark = null, {}, {})
        CellHeader(HEADING_CELL, execution = null, canPaste = false, mark = null, {}, {})
        CellHeader(RAW_CELL, execution = null, canPaste = false, mark = null, {}, {})
      }
    }

    compose.onNodeWithText("[ ]").assertDoesNotExist()
    compose.onNodeWithText("[12]").assertDoesNotExist()
  }

  /**
   * **A heading cell's header is a markdown cell's header, with nothing extra on it.** The caret
   * left this strip in P2n-3 for the heading it folds, and its Run control left for the gutter in
   * P2v-1 - which is the parity the reference has by having no per-cell strip at all.
   */
  @Test
  fun aHeadingsHeaderCarriesNothingAProseHeaderDoesNot() {
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(COMPACT)) {
        Column {
          CellHeader(PROSE_CELL, execution = null, canPaste = false, mark = null, {}, {})
          CellHeader(HEADING_CELL, execution = null, canPaste = false, mark = null, {}, {})
        }
      }
    }

    compose.onNodeWithContentDescription(COLLAPSE_HEADING).assertDoesNotExist()
    val prose = compose.onAllNodesWithContentDescription(MENU_BUTTON)[0].getUnclippedBoundsInRoot().left
    assertEquals(prose, compose.onAllNodesWithContentDescription(MENU_BUTTON)[1].getUnclippedBoundsInRoot().left)
  }

  /**
   * The words this app invented, gone. `MarkdownCell` and `RawCell` never call `_setPrompt`, whose
   * only caller is `CodeCell._updatePrompt` (`packages/cells/src/widget.ts:1693-1700`), so the
   * reference draws their prompt node empty.
   */
  @Test
  fun neitherMarkdownNorRawCarriesAPrompt() {
    compose.setContent {
      Column {
        CellHeader(PROSE_CELL, execution = null, canPaste = false, mark = null, {}, {})
        CellHeader(RAW_CELL, execution = null, canPaste = false, mark = null, {}, {})
      }
    }

    compose.onNodeWithText("markdown").assertDoesNotExist()
    compose.onNodeWithText("raw").assertDoesNotExist()
  }

  /** A marked run puts one tick on every cell, and nothing about that changed for the strip. */

  /**
   * **A header has to fit at 320dp**, which is the width this app is driven at and the one that
   * decides. The worst case left here is a code cell that has finished: the duration label and the
   * `⋮`. This is the assertion that keeps anything added to the strip honest - the version of P2n
   * that reserved two 48dp gutter slots left about 10dp of headroom here and would have gone over
   * as soon as `fontScale` rose, which on the tablet it already has (1.1).
   */
  @Test
  fun theMenuButtonStaysOnScreenAt320dp() {
    compose.setContent {
      DeviceConfigurationOverride(DeviceConfigurationOverride.ForcedSize(COMPACT)) {
        CellHeader(CODE_CELL, RAN_FOR_EIGHT_SECONDS, canPaste = false, mark = null, {}, {})
      }
    }

    val right = compose.onNodeWithContentDescription(MENU_BUTTON).getUnclippedBoundsInRoot().right
    assertTrue("the menu button ends at $right, past the ${COMPACT.width} window", right <= COMPACT.width)
    compose.onNodeWithText("ran in 8.0 s").assertIsDisplayed()
  }

  private companion object {
    const val MENU_BUTTON = "Cell actions"
    const val RUN_BUTTON = "Run cell"

    /** The emulator's own window, and the narrowest this app is driven at. */
    val COMPACT = DpSize(320.dp, 640.dp)

    /** A finished run, so the header carries the duration label that competes with the gutter. */
    val RAN_FOR_EIGHT_SECONDS = CellExecution(
      ExecutionStatus.STOPPED_OK,
      startedAt = Instant.parse("2026-08-21T12:00:00Z"),
      endedAt = Instant.parse("2026-08-21T12:00:08Z"),
    )
    val MARK = CellMark(checked = true, position = 2)
    val CODE_CELL = Cell("c1", CellKind.CODE, "x = 1")
    val MARKDOWN_CELL = Cell("c2", CellKind.MARKDOWN, "# title")
    val RAW_CELL = Cell("c3", CellKind.RAW, "\\begin{equation}")
    val HEADING_CELL = Cell("h1", CellKind.MARKDOWN, "## Results")
    val PROSE_CELL = Cell("p1", CellKind.MARKDOWN, "just some prose")
  }
}
