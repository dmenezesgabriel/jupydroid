package com.jupydroid.app

import androidx.navigation3.runtime.NavKey
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The reveal-don't-reopen rule, on its own, where the cases are cheap to state.
 *
 * The device half is `NotebookNavigationTest.tappingTheSameNotebookTwiceOpensOneEditor`; this is
 * the half that says what "already open" means when it is not the notebook on top.
 */
class NotebookBackStackTest {
  @Test
  fun openingANotebookPushesItsEditor() {
    val backStack = mutableListOf<NavKey>(Home)

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Home, Editor("nb-1")), backStack)
  }

  @Test
  fun openingTheNotebookAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Home, Editor("nb-1"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Home, Editor("nb-1")), backStack)
  }

  /**
   * A second notebook **replaces** the first rather than stacking on it.
   *
   * It used to push, which was unreachable on a phone - Home is not on screen while an editor
   * is - and so never wrong. Two panes make it reachable: the list is beside the editor, and a
   * stacked `[Home, Editor(a), Editor(b)]` would pair the *last two* entries and put a notebook
   * where the list belongs. Replacing also disposes `Editor(a)`'s entry, which is what makes
   * `EditorViewModel.onCleared` run and close its kernel socket.
   */
  @Test
  fun openingADifferentNotebookReplacesTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Home, Editor("nb-1"))

    backStack.openNotebook("nb-2")

    assertEquals(listOf(Home, Editor("nb-2")), backStack)
  }

  /** Only an editor is swapped out. Anything else below stays where it is. */
  @Test
  fun openingFromTheListLeavesTheListInPlace() {
    val backStack = mutableListOf<NavKey>(Home)

    backStack.openNotebook("nb-1")
    backStack.openNotebook("nb-2")

    assertEquals(listOf(Home, Editor("nb-2")), backStack)
  }
}
