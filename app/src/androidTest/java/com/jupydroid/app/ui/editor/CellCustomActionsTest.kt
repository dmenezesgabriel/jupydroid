package com.jupydroid.app.ui.editor

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import com.jupydroid.app.theme.JupydroidTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every cell action reachable from the cell itself, without opening the menu.
 *
 * TalkBack reaches a `DropdownMenu` behind a `⋮` in three moves: find the button among the cell's
 * other controls, activate it, then walk a list that has just replaced the screen. `customActions`
 * puts the same list in the local context menu of the cell the user is already on - which is what
 * the property is for, and the reason Compose exposes it separately from `onClick`.
 *
 * Instrumented because custom actions exist only in a real semantics tree: `fetchSemanticsNode`
 * is the only way to ask what a node offers, and invoking one is the only way to know it is wired
 * to anything.
 */
@RunWith(AndroidJUnit4::class)
class CellCustomActionsTest {
  @get:Rule val compose = createComposeRule()

  private val taken = mutableListOf<CellAction>()

  @Test
  fun aCellOffersTheSameActionsAsItsMenu() {
    showCell(CODE_CELL)

    val labels = customActions().map { it.label }

    // The menu's own list, so the two can never drift apart - they are the same function. **With
    // one deliberate divergence**, and it is the reason this is spelled out rather than compared
    // directly: the row that stands for the six heading levels is *expanded* here. A visual menu
    // has eighteen rows already and a fold to run out of; this list has neither, and a submenu is
    // the worst shape there is to operate with TalkBack. See [headingLevelItems].
    val menu = cellActionItems(CODE_CELL, canPaste = false, selecting = false).map { it.first }
    val expanded = menu.flatMap {
      if (it ==
        CHANGE_TO_HEADING
      ) {
        headingLevelItems().map { l -> l.first }
      } else {
        listOf(it)
      }
    }
    assertEquals(expanded, labels)
    assertTrue("the picker's own row must not be here", CHANGE_TO_HEADING !in labels)
  }

  /** Every level is invocable, not just announced - the second half of what the expansion is for. */
  @Test
  fun eachHeadingLevelIsWiredToItsOwnCommand() {
    showCell(CODE_CELL)

    customActions().first { it.label == "Heading 3" }.action()

    assertEquals(listOf(CellAction.ChangeToHeading(3)), taken)
  }

  @Test
  fun invokingOneReportsIt() {
    showCell(CODE_CELL)

    val deleted = customActions().first { it.label == "Delete" }.action()

    assertTrue("the action returned false", deleted)
    assertEquals(listOf(CellAction.Delete), taken)
  }

  /**
   * Paste is offered only when there is something to paste, on this surface too. An action a
   * screen reader announces and that then does nothing is worse than one it never announced.
   */
  @Test
  fun pasteIsAbsentWithAnEmptyClipboard() {
    showCell(CODE_CELL)

    assertEquals(emptyList<String>(), customActions().map { it.label }.filter { it.startsWith("Paste") })
  }

  /** Markdown has no code to collapse, and the menu knows it; so must this. */
  @Test
  fun aMarkdownCellOffersTheMarkdownList() {
    showCell(CODE_CELL.copy(kind = CellKind.MARKDOWN))

    val labels = customActions().map { it.label }

    assertTrue("collapse offered on markdown: $labels", labels.none { it == COLLAPSE_CODE })
    assertTrue("no change-to-code: $labels", labels.contains(CHANGE_TO_CODE))
  }

  private fun customActions(): List<CustomAccessibilityAction> =
    compose.onNodeWithTag(CELL).fetchSemanticsNode().config[SemanticsActions.CustomActions]

  private fun showCell(cell: Cell) {
    compose.setContent {
      JupydroidTheme {
        Column(
          modifier = androidx.compose.ui.Modifier
            .testTag(CELL)
            .cellCustomActions(cell = cell, canPaste = false, selecting = false, count = 1, onAction = { taken += it }),
        ) {}
      }
    }
  }

  private companion object {
    const val CELL = "cell"
    val CODE_CELL = Cell(id = "c1", kind = CellKind.CODE, source = "x = 1")
  }
}
