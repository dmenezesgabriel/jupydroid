package com.jupydroid.app.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the app bar has to give the notebook its 64dp back.
 *
 * Measured, not guessed: on a phone in landscape - 640x320dp -
 * the status bar, the app bar, the symbol row and the keyboard add up to more than the window,
 * so `Scaffold` handed the cell list **zero height** and a `uiautomator` dump of a notebook being
 * typed into contained no cell at all.
 */
class EditorTopBarVisibilityTest {
  @Test
  fun aTallWindowKeepsTheAppBarWhileEditing() {
    assertTrue(editorTopBarVisible(heightDp = TALL, mode = CellMode.EDIT))
  }

  @Test
  fun aShortWindowKeepsItToo_whenNothingIsBeingEdited() {
    assertTrue(editorTopBarVisible(heightDp = SHORT, mode = CellMode.COMMAND))
  }

  /** The one case that has to give: short window *and* a keyboard, which is landscape typing. */
  @Test
  fun aShortWindowDropsTheAppBarWhileEditing() {
    assertFalse(editorTopBarVisible(heightDp = SHORT, mode = CellMode.EDIT))
  }

  /** The boundary is the documented compact/medium height class, not a number of our own. */
  @Test
  fun theBoundaryIsTheMediumHeightBreakpoint() {
    assertFalse(editorTopBarVisible(heightDp = MEDIUM_HEIGHT_DP - 1, mode = CellMode.EDIT))
    assertTrue(editorTopBarVisible(heightDp = MEDIUM_HEIGHT_DP, mode = CellMode.EDIT))
  }

  private companion object {
    const val TALL = 800
    const val SHORT = 320
  }
}
