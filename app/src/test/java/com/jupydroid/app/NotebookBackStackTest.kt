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
    val backStack = mutableListOf<NavKey>(Explorer)

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer, Editor("nb-1")), backStack)
  }

  @Test
  fun openingTheNotebookAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer, Editor("nb-1")), backStack)
  }

  /**
   * A second notebook **replaces** the first rather than stacking on it.
   *
   * It used to push, which was unreachable on a phone - Explorer is not on screen while an editor
   * is - and so never wrong. Two panes make it reachable: the list is beside the editor, and a
   * stacked `[Explorer, Editor(a), Editor(b)]` would pair the *last two* entries and put a notebook
   * where the list belongs. Replacing also disposes `Editor(a)`'s entry, which is what makes
   * `EditorViewModel.onCleared` run and close its kernel socket.
   */
  @Test
  fun openingADifferentNotebookReplacesTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"))

    backStack.openNotebook("nb-2")

    assertEquals(listOf(Explorer, Editor("nb-2")), backStack)
  }

  /** Only an editor is swapped out. Anything else below stays where it is. */
  @Test
  fun openingFromTheListLeavesTheListInPlace() {
    val backStack = mutableListOf<NavKey>(Explorer)

    backStack.openNotebook("nb-1")
    backStack.openNotebook("nb-2")

    assertEquals(listOf(Explorer, Editor("nb-2")), backStack)
  }

  /** A plain file opens the same way a notebook does: one document, pushed onto the browser. */
  @Test
  fun openingAFilePushesItsEditor() {
    val backStack = mutableListOf<NavKey>(Explorer, Explorer)

    backStack.openFile("data.csv")

    assertEquals(listOf(Explorer, Explorer, FileEditor("data.csv")), backStack)
  }

  @Test
  fun openingTheFileAlreadyOnTopChangesNothing() {
    val backStack = mutableListOf<NavKey>(Explorer, Explorer, FileEditor("data.csv"))

    backStack.openFile("data.csv")

    assertEquals(listOf(Explorer, Explorer, FileEditor("data.csv")), backStack)
  }

  /** A second file replaces the one open - the reference's `openOrReveal`, applied to files. */
  @Test
  fun openingADifferentFileReplacesTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Explorer, Explorer, FileEditor("a.csv"))

    backStack.openFile("b.csv")

    assertEquals(listOf(Explorer, Explorer, FileEditor("b.csv")), backStack)
  }

  /**
   * The two document kinds swap each other. The explorer can open both, and a detail it cannot
   * swap would stack `[Explorer, FileEditor(a), Editor(b)]`, whose last two entries
   * [TwoPaneSceneStrategy] refuses to pair - one stray tap would collapse the two panes.
   */
  @Test
  fun openingANotebookReplacesAPlainFileAndViceVersa() {
    val fromFile = mutableListOf<NavKey>(Explorer, Explorer, FileEditor("a.py"))
    fromFile.openNotebook("nb-1")
    assertEquals(listOf(Explorer, Explorer, Editor("nb-1")), fromFile)

    val fromNotebook = mutableListOf<NavKey>(Explorer, Explorer, Editor("nb-1"))
    fromNotebook.openFile("a.py")
    assertEquals(listOf(Explorer, Explorer, FileEditor("a.py")), fromNotebook)
  }
}
