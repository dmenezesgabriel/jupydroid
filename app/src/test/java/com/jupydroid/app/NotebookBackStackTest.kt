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
   * swap would stack `[Explorer, FileEditor(a), Editor(b)]` - two entries claiming one pane, of
   * which only the last is ever drawn.
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

  /** A terminal opens the same way: one document, pushed onto the browser. */
  @Test
  fun openingATerminalPushesItsScreen() {
    val backStack = mutableListOf<NavKey>(Explorer)

    backStack.openTerminal("1")

    assertEquals(listOf(Explorer, Terminal("1")), backStack)
  }

  /**
   * A second "new terminal" tap always opens a fresh session - two terminals never compare equal
   * by name, since the server mints a new one every time - so it replaces rather than reveals.
   */
  @Test
  fun openingASecondTerminalReplacesTheOneOpen() {
    val backStack = mutableListOf<NavKey>(Explorer, Terminal("1"))

    backStack.openTerminal("2")

    assertEquals(listOf(Explorer, Terminal("2")), backStack)
  }

  /** A terminal swaps a document exactly as the other two kinds swap each other. */
  @Test
  fun openingATerminalReplacesAnEditorAndViceVersa() {
    val fromEditor = mutableListOf<NavKey>(Explorer, Explorer, Editor("nb-1"))
    fromEditor.openTerminal("1")
    assertEquals(listOf(Explorer, Explorer, Terminal("1")), fromEditor)

    val fromTerminal = mutableListOf<NavKey>(Explorer, Explorer, Terminal("1"))
    fromTerminal.openNotebook("nb-1")
    assertEquals(listOf(Explorer, Explorer, Editor("nb-1")), fromTerminal)
  }

  /**
   * The same notebook, already open **beneath** whatever is on top, is promoted rather than
   * duplicated - `docs/PLAN.md`'s own Open risks entry on `openNotebook`: `lastOrNull() == document`
   * only ever checked the top, so a notebook one level deeper survived a swap of what sat above it
   * and came back a second time on the next [openNotebook] for the same id. Two entries would then
   * share one `contentKey` ([openNotebook]'s own doc comment), and popping either would clear
   * `ViewModelStore`/`SaveableStateHolder` state the other was still relying on (P6b-0).
   */
  @Test
  fun reopeningANotebookStackedBeneathSomethingElsePromotesItInstead() {
    val backStack = mutableListOf<NavKey>(Explorer, Editor("nb-1"), Terminal("1"))

    backStack.openNotebook("nb-1")

    assertEquals(listOf(Explorer, Editor("nb-1")), backStack)
  }
}
