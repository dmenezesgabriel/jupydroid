package com.jupydroid.app.ui.editor

import com.jupydroid.app.domain.model.Cell
import com.jupydroid.app.domain.model.CellKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The accessibility context menu's action list.
 *
 * Compose turns [androidx.compose.ui.semantics.CustomAccessibilityAction]s into actions on one
 * `AccessibilityNodeInfo`, and `AndroidComposeViewAccessibilityDelegateCompat` throws
 * *"Can't have more than 32 custom actions for one widget"* when a node offers that many - which
 * any accessibility walk of the tree then turns into a crash, whether the walker is TalkBack or a
 * `uiautomator dump`. The fullest list this surface can build - a code cell, a kernel answering
 * inspect asks, and something on the clipboard - is exactly that many once the heading row is
 * expanded, so the expansion is the first thing to fold back.
 */
class CellContextActionsTest {

  /** The ordinary list expands the heading row into six - see [headingLevelItems]. */
  @Test
  fun withinTheLimit_theHeadingRowExpands() {
    val labels = contextActionItems(
      CODE_CELL,
      canPaste = true,
      selecting = false,
      count = 1,
      inspection = false,
    ).map { it.first }

    assertTrue("Heading 3" in labels)
    assertTrue(CHANGE_TO_HEADING !in labels)
  }

  /**
   * The cap, on the fullest cell the notebook can show: the expansion alone takes the list past
   * it, so the row the picker stands behind stays instead - and stays *live*, which is the whole
   * reason it can be the fallback.
   */
  @Test
  fun atTheLimit_thePickerRowStandsInstead() {
    val actions = contextActionItems(CODE_CELL, canPaste = true, selecting = false, count = 1, inspection = true)

    assertTrue(actions.size < 32)
    assertTrue("Heading 1" !in actions.map { it.first })
    assertEquals(CellAction.ChooseHeadingLevel, actions.first { it.first == CHANGE_TO_HEADING }.second)
  }

  /** A marked run's list is short either way; the cap must not clip anything that fit before. */
  @Test
  fun aListThatFitsIsNeverFolded() {
    val actions = contextActionItems(CODE_CELL, canPaste = true, selecting = true, count = 4, inspection = true)

    assertTrue("Heading 1" in actions.map { it.first })
  }

  private companion object {
    val CODE_CELL = Cell(id = "c1", kind = CellKind.CODE, source = "x = 1")
  }
}
